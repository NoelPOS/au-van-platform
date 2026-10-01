package com.auvan.api.notification.client;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.config.LineMessagingProperties;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@SpringBootTest
class LineDeliveryIntegrationTests extends AuthenticationTestSupport {
    private static final String PUSH_URL = "https://api.line.me/v2/bot/message/push";
    private static final String TOKEN = "channel-access-token-for-this-test-only";

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

    @Test
    void aPermanentLineErrorDeadLettersTheRowOnItsFirstAttemptRatherThanItsFifth() {
        sender.delegate = lineAlwaysAnswering(withStatus(HttpStatus.NOT_FOUND)
                .body("{\"message\":\"The user hasn't added the LINE Official Account as a friend.\"}")
                .contentType(MediaType.APPLICATION_JSON));
        UUID eventId = record(student);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("404");
            assertThat(event.getProcessedAt()).isNotNull();
        });
        assertThat(dispatcher.dispatchBatch()).isZero();
    }

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

    @Test
    void theWholeRetryScheduleFinishesFarInsideLinesTwentyFourHourRetryKeyWindow() {
        Duration total = Duration.ZERO;
        for (int attempt = 1; attempt < outbox.maxAttempts(); attempt++) {
            Duration backoff = outbox.backoffBase().multipliedBy(1L << (attempt - 1));
            total = total.plus(backoff.compareTo(outbox.backoffCap()) > 0 ? outbox.backoffCap() : backoff);
            total = total.plus(outbox.lease());
        }

        assertThat(total).isLessThan(Duration.ofHours(24));
    }

    private UUID record(UUID recipient) {
        return recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), recipient,
                Map.of("reference", "AUV-250101-LINE", "detail", "Booked seats A1."), OffsetDateTime.now()).getId();
    }

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
