package com.auvan.api.outbox;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * Two workers, one due row. Exactly one claim succeeds and exactly one message
 * is sent — the property the whole design rests on, because nothing in the
 * schema would notice a message being sent twice.
 *
 * <p>Like the three concurrency classes before it, this one must not be
 * {@code @Transactional}: a test-managed transaction would put both threads on
 * one connection and there would be no race left to observe.
 *
 * <p>The candidate read is stubbed so both workers hold the same id before
 * either tries to claim it, which is the interleaving a real race only
 * sometimes produces. The stub controls timing only; both threads then run the
 * production claim for real and the database decides.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour.
 * {@link OutboxClaimConcurrencyPostgresTests} re-proves the claim against a real
 * PostgreSQL 17, where the {@code WHERE}-clause recheck that the loser's {@code 0}
 * actually rests on is a documented guarantee rather than an emulation. That suite
 * is tagged {@code postgres} and excluded from {@code ./gradlew test}, so this
 * class keeps its Docker-free feedback.
 */
@SpringBootTest
class OutboxConcurrencyIntegrationTests extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeSenderConfiguration {
        @Bean
        @Primary
        RecordingLineMessageSender recordingLineMessageSender() {
            return new RecordingLineMessageSender();
        }
    }

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private RecordingLineMessageSender sender;

    // A spy, not a mock: every call runs for real except the one this stubs.
    @MockitoSpyBean
    private OutboxEventRepository events;

    @Autowired
    private AppUserRepository users;

    private UUID student;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        student = users.save(new AppUser("Ustudent-outbox-race", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        users.deleteAll();
    }

    /**
     * The conditional {@code UPDATE} is the only thing standing between this
     * and two identical messages. Weaken it — drop {@code status} and
     * {@code nextAttemptAt} from the {@code WHERE} so the claim always reports
     * success — and both workers send: the loser's {@code UPDATE} waits for the
     * winner's commit and then happily affects its one row.
     */
    @Test
    void onlyOneOfTwoWorkersClaimsADueRowAndOnlyOneMessageIsSent() throws Exception {
        UUID eventId = recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), student,
                new BookingNotification("AUV-250101-RACE", "Booked seats A1."), OffsetDateTime.now()).getId();
        CountDownLatch bothHoldTheCandidate = new CountDownLatch(2);

        // Both workers leave the candidate read holding the same id, so both
        // reach the claim. Without this they would usually not overlap at all.
        doAnswer(invocation -> {
            Object candidates = real(invocation);
            bothHoldTheCandidate.countDown();
            assertThat(bothHoldTheCandidate.await(10, TimeUnit.SECONDS)).isTrue();
            return candidates;
        }).when(events).findDispatchable(any(), any());

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> workers = pool.invokeAll(List.of(dispatcher::dispatchBatch,
                    dispatcher::dispatchBatch), 30, TimeUnit.SECONDS);
            int claimed = 0;
            for (Future<Integer> worker : workers) {
                claimed += worker.get();
            }
            assertThat(claimed).isOne();
        }

        assertThat(sender.messages()).hasSize(1);
        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            // One claim, so one spent attempt: the loser wrote nothing at all.
            assertThat(event.getAttempts()).isOne();
        });
    }

    /**
     * Runs the call the stub intercepted for real.
     *
     * <p>{@code invocation.callRealMethod()} cannot do this for a Spring Data
     * repository: the method is an interface method with no body, and the spy
     * keeps the actual repository in its default answer rather than as a spied
     * instance.
     */
    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
}
