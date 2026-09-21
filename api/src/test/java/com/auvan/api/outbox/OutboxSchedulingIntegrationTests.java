package com.auvan.api.outbox;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other side of the scheduling gate: with {@code outbox.dispatch.enabled}
 * turned back on, a recorded row really is dispatched by the scheduler, with
 * nothing in this test calling {@code dispatchBatch()}. Every other test class
 * calls the dispatcher directly, so without this one the wiring — the
 * {@code @Scheduled} method, the poll interval binding, the conditional bean —
 * would be entirely unproven.
 *
 * <p>The property is set here and nowhere else, so this is the only context in
 * the suite with a live poller in it. That isolation is the point: a scheduler
 * running through the booking and payment-proof concurrency tests would move
 * their fixtures mid-assertion.
 *
 * <p>No {@code LineMessageSender} is registered, which also makes this the
 * proof that the handler is safe with nothing behind the port: the row
 * completes rather than failing five times over an absent dependency.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "outbox.dispatch.enabled=true",
        "outbox.dispatch.poll-interval=PT0.1S"
})
class OutboxSchedulingIntegrationTests extends AuthenticationTestSupport {
    private static final long TIMEOUT_MILLIS = 20_000;

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private AppUserRepository users;

    private UUID student;

    @BeforeEach
    void setUp() {
        clearData();
        student = users.save(new AppUser("Ustudent-outbox-schedule", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        users.deleteAll();
    }

    @Test
    void theScheduledPollerDispatchesARecordedRowWithoutAnybodyCallingTheDispatcher() throws Exception {
        UUID eventId = recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), student,
                new BookingNotification("AUV-250101-SCHEDULED", "Booked seats A1."), OffsetDateTime.now()).getId();

        OutboxEvent dispatched = awaitResolution(eventId);

        assertThat(dispatched.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(dispatched.getAttempts()).isOne();
        assertThat(dispatched.getProcessedAt()).isNotNull();
    }

    /**
     * Polls rather than sleeping a fixed time, so a slow machine waits longer
     * and a fast one does not.
     *
     * <p>It waits for a <em>terminal</em> status, not merely for something other
     * than {@code PENDING}. A dispatch takes the row through {@code IN_FLIGHT}
     * on its way, so "not PENDING" returns a row that has only been claimed and
     * the {@code SENT} assertion fails on a machine slow enough to be polled
     * mid-dispatch — which is a flake in this test, not a fault in the
     * dispatcher.
     */
    private OutboxEvent awaitResolution(UUID eventId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            OutboxEvent event = events.findById(eventId).orElseThrow();
            if (event.getStatus() == OutboxStatus.SENT || event.getStatus() == OutboxStatus.DEAD) {
                return event;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The scheduled poller never dispatched outbox event " + eventId + ".");
    }
}
