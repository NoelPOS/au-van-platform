package com.auvan.api.outbox;

import com.auvan.api.PostgresTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
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
import java.util.Map;
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

    @Test
    void onlyOneOfTwoWorkersClaimsADueRowAndOnlyOneMessageIsSent() throws Exception {
        UUID eventId = recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), student,
                Map.of("reference", "AUV-250101-PGRC", "detail", "Booked seats A1."), OffsetDateTime.now()).getId();
        CountDownLatch bothHoldTheCandidate = new CountDownLatch(2);

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
            assertThat(event.getAttempts()).isOne();
        });
    }

    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
}
