package com.auvan.api.outbox;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
                Map.of("reference", "AUV-250101-SCHEDULED", "detail", "Booked seats A1."), OffsetDateTime.now()).getId();

        OutboxEvent dispatched = awaitResolution(eventId);

        assertThat(dispatched.getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(dispatched.getAttempts()).isOne();
        assertThat(dispatched.getProcessedAt()).isNotNull();
    }

    // Wait for a terminal status: a row polled mid-dispatch is IN_FLIGHT, not SENT.
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
