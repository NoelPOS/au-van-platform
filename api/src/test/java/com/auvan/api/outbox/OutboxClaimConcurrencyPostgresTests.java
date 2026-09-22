package com.auvan.api.outbox;

import com.auvan.api.PostgresTestSupport;
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
 * The outbox claim against the engine whose behaviour it depends on. This is the
 * one property in the suite that <em>only</em> PostgreSQL is obliged to provide.
 *
 * <p>{@code OutboxEventRepository.claim} is a single conditional {@code UPDATE},
 * and the affected-row count is the whole answer. Two workers issue it against one
 * row at the same instant. The second one blocks on the first's row lock; when the
 * first commits, PostgreSQL under READ COMMITTED does <strong>not</strong> abandon
 * the statement and does not apply it to the stale version either — it re-evaluates
 * the {@code WHERE} clause against the newly committed row (the EvaluateTuple
 * recheck) and, finding {@code next_attempt_at} pushed a lease into the future,
 * matches nothing and reports {@code 0}.
 *
 * <p>Nothing in the schema would notice a message being sent twice, so that recheck
 * is the only thing between this design and duplicate notifications. No other
 * engine is obliged to behave this way, and {@link OutboxConcurrencyIntegrationTests}
 * on H2 has never been proof of it.
 *
 * <p>Must not be {@code @Transactional}: a test-managed transaction would put both
 * workers on one connection and there would be no race left to observe.
 */
@SpringBootTest
class OutboxClaimConcurrencyPostgresTests extends PostgresTestSupport {
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
        student = users.save(new AppUser("Upg-outbox-race", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        users.deleteAll();
    }

    /**
     * Two dispatchers, one due row: exactly one claim reports {@code 1} and exactly
     * one message is sent.
     *
     * <p>Weaken the claim's {@code WHERE} to {@code status} alone — drop the
     * {@code next_attempt_at <= :now} condition — and the recheck starts passing for
     * the loser too, because the winner left the row {@code IN_FLIGHT} and
     * {@code IN_FLIGHT} is still in the status set. Both workers then claim it and
     * the student gets the message twice.
     */
    @Test
    void onlyOneOfTwoWorkersClaimsADueRowAndOnlyOneMessageIsSent() throws Exception {
        UUID eventId = recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), student,
                new BookingNotification("AUV-250101-PGRC", "Booked seats A1."), OffsetDateTime.now()).getId();
        CountDownLatch bothHoldTheCandidate = new CountDownLatch(2);

        // Both workers leave the candidate read holding the same id, so both reach
        // the claim. Without this they would usually not overlap at all. The stub
        // controls timing only; the production claim then runs for real in both
        // threads and the database decides.
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
            // One claim, so one spent attempt: the loser's UPDATE wrote nothing at
            // all, which is exactly what the recheck is for.
            assertThat(event.getAttempts()).isOne();
        });
    }

    /**
     * Runs the call the stub intercepted for real.
     *
     * <p>{@code invocation.callRealMethod()} cannot do this for a Spring Data
     * repository: the method is an interface method with no body, and the spy keeps
     * the actual repository in its default answer rather than as a spied instance.
     */
    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
}
