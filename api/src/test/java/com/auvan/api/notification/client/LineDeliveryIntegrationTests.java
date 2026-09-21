package com.auvan.api.notification.client;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.config.OutboxProperties;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The real sender driven by the real dispatcher: what each kind of LINE answer
 * does to the outbox row it came from.
 *
 * <p>{@link LineMessageSenderImplTests} proves the classification; this proves
 * the consequence, which is the thing that actually costs something. A
 * permanent error that is merely "an exception" is retried
 * {@code outbox.max-attempts} times, and for a student who has not added the
 * official account that is five futile sends for every notification they are
 * ever owed.
 *
 * <p>The sender is swapped per test through {@link SwitchableSender} so that one
 * application context covers every case. Each variant is a real
 * {@link LineMessageSenderImpl} bound to a {@link MockRestServiceServer}: the
 * production code path runs in full and no request leaves the machine.
 *
 * <p>Not {@code @Transactional}: the dispatcher must run outside any
 * transaction of its own, and its claim and outcome are separate commits.
 */
@SpringBootTest
class LineDeliveryIntegrationTests extends AuthenticationTestSupport {
    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private static final String TOKEN = "channel-access-token-for-this-test-only";

    /** Lets one context serve every kind of LINE answer, one test at a time. */
    static class SwitchableSender implements LineMessageSender {
        volatile LineMessageSender delegate;

        @Override
        public void send(LinePushMessage message) {
            delegate.send(message);
        }
    }

    @TestConfiguration
    static class Ports {
        @Bean
        @Primary
        SwitchableSender switchableSender() {
            return new SwitchableSender();
        }
    }

    @Autowired
    private SwitchableSender sender;

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private OutboxProperties outbox;

    @Autowired
    private AppUserRepository users;

    private MockRestServiceServer line;
    private UUID student;

    @BeforeEach
    void setUp() {
        clearData();
        student = users.save(new AppUser("Ustudent-delivery", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        users.deleteAll();
    }

    @Test
    void anAcceptedPushMarksTheRowSent() {
        sender.delegate = lineAlwaysAnswering(withSuccess("{}", MediaType.APPLICATION_JSON));
        UUID eventId = record(student);

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    /**
     * Trap 12, as the consequence rather than the classification. Make
     * {@code LineMessageSenderImpl} throw a plain {@code IllegalStateException}
     * for a 404 instead of a {@code PermanentFailureException} — or delete the
     * permanent branch from {@code OutboxDispatcher.recordFailure} — and this
     * reddens: the row comes back {@code PENDING} on attempt one, with four more
     * pointless sends ahead of it.
     */
    @Test
    void aPermanentLineErrorDeadLettersTheRowOnItsFirstAttemptRatherThanItsFifth() {
        sender.delegate = lineAlwaysAnswering(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"message\":\"The user hasn't added the LINE Official Account as a friend.\"}")
                .contentType(MediaType.APPLICATION_JSON));
        UUID eventId = record(student);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
            // One, not outbox.max-attempts. This number is the whole point.
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("404");
            assertThat(event.getProcessedAt()).isNotNull();
        });
        // And nothing claims it again, however due it looks.
        assertThat(dispatcher.dispatchBatch()).isZero();
    }

    /**
     * The other half of the same guard: a non-2xx that LINE does not define as
     * permanent has to keep its retries. Classify 5xx as permanent and a
     * passing outage silently dead-letters every notification taken during it.
     */
    @Test
    void aTransientLineErrorLeavesTheRowPendingWithItsBackoffAndItsRemainingAttempts() {
        sender.delegate = lineAlwaysAnswering(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        UUID eventId = record(student);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("500");
            assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusSeconds(20));
        });
    }

    /**
     * With no channel access token the dispatch must fail cleanly and leave the
     * row for another attempt — a token supplied afterwards makes that attempt
     * work — rather than throwing out of {@code dispatchBatch} and stopping the
     * batch. The application starting at all with the token blank is the same
     * property, and every other {@code @SpringBootTest} in this suite is the
     * proof of it.
     */
    @Test
    void aBlankChannelAccessTokenLeavesTheRowRetryingInsteadOfCrashingTheDispatch() {
        sender.delegate = lineWithNoToken();
        UUID eventId = record(student);

        assertThatCode(() -> assertThat(dispatcher.dispatchBatch()).isZero()).doesNotThrowAnyException();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("LINE_CHANNEL_ACCESS_TOKEN");
        });
    }

    /**
     * A student with no usable LINE subject is undeliverable for as long as that
     * stays true, so the row dies on the first attempt and nothing is ever put
     * on the wire — {@link MockRestServiceServer#verify()} below expects no
     * request at all.
     */
    @Test
    void aStudentWithNoLineUserIdIsNotRetriedFiveTimesOverAndOverAgain() {
        sender.delegate = lineExpectingNoRequest();
        UUID unreachable = users.save(new AppUser("", "No LINE identity")).getId();
        UUID eventId = record(unreachable);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("no LINE user id");
        });
        line.verify();
    }

    /**
     * Trap 10, as arithmetic over the values actually configured rather than
     * over the ones the plan assumed. LINE honours {@code X-Line-Retry-Key} for
     * twenty-four hours and treats anything later as a fresh request, so a row
     * still retrying past that window can put a second message in front of a
     * student. Raise {@code outbox.backoff-cap} or {@code outbox.max-attempts}
     * far enough and this reddens instead of shipping that risk in silence.
     */
    @Test
    void theWholeRetryScheduleFinishesFarInsideLinesTwentyFourHourRetryKeyWindow() {
        Duration total = Duration.ZERO;
        for (int attempt = 1; attempt < outbox.maxAttempts(); attempt++) {
            Duration backoff = outbox.backoffBase().multipliedBy(1L << (attempt - 1));
            total = total.plus(backoff.compareTo(outbox.backoffCap()) > 0 ? outbox.backoffCap() : backoff);
            // Each attempt also holds a lease before it fails.
            total = total.plus(outbox.lease());
        }

        assertThat(total).isLessThan(Duration.ofHours(24));
    }

    // Fixtures

    private UUID record(UUID recipient) {
        return recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), recipient,
                new BookingNotification("AUV-250101-LINE", "Booked seats A1."), OffsetDateTime.now()).getId();
    }

    /** The production sender, against a LINE that answers this way to anything. */
    private LineMessageSenderImpl lineAlwaysAnswering(
            org.springframework.test.web.client.ResponseCreator answer) {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        line.expect(ExpectedCount.manyTimes(), requestTo(PUSH_URL)).andRespond(answer);
        return senderOn(builder, TOKEN);
    }

    private LineMessageSenderImpl lineExpectingNoRequest() {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        return senderOn(builder, TOKEN);
    }

    private LineMessageSenderImpl lineWithNoToken() {
        RestClient.Builder builder = RestClient.builder();
        line = MockRestServiceServer.bindTo(builder).build();
        return senderOn(builder, "");
    }

    private static LineMessageSenderImpl senderOn(RestClient.Builder builder, String token) {
        return new LineMessageSenderImpl(
                new LineMessagingProperties(token, "https://api.line.me", true), builder);
    }
}
