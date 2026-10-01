package com.auvan.api.notification;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.service.FlexCards;
import com.auvan.api.outbox.RecordingLineMessageSender;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static com.auvan.api.notification.service.FlexCards.texts;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NotificationCardIntegrationTests extends AuthenticationTestSupport {
    @TestConfiguration
    static class Ports {
        @Bean
        @Primary
        RecordingLineMessageSender recordingLineMessageSender() {
            return new RecordingLineMessageSender();
        }
    }

    @Autowired
    private RecordingLineMessageSender sender;

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    private UUID student;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        student = users.save(new AppUser("Ustudent-cards", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        users.deleteAll();
    }

    @Test
    void everyEventTypeIsPushedAsAFlexBubbleAndItsRowLandsSent() {
        for (OutboxEventType type : OutboxEventType.values()) {
            record(type, FlexCards.payloadFor(type));
        }

        assertThat(dispatcher.dispatchBatch()).isEqualTo(OutboxEventType.values().length);

        assertThat(events.findAll()).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT));
        assertThat(sender.messages()).hasSize(OutboxEventType.values().length).allSatisfy(message -> {
            assertThat(message.to()).isEqualTo("Ustudent-cards");
            assertThat(message.message().type()).isEqualTo("flex");
            assertThat(message.message().contents()).containsEntry("type", "bubble");
            assertThat(texts(message.message())).contains("Siam Paragon", "Fri 2 Oct · 22:14");
        });
    }

    @Test
    void aBookingRowWrittenBeforeTheCardFieldsStillDeliversAsASimplerBubble() {
        UUID eventId = record(OutboxEventType.PAYMENT_REJECTED,
                Map.of("reference", "AUV-250101-OLD", "detail", "The slip is unreadable."));

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        LinePushMessage pushed = sender.messages().getFirst();
        assertThat(texts(pushed.message())).contains("Payment slip not accepted", "The slip is unreadable.",
                "Booking ref AUV-250101-OLD").doesNotContain("Departs");
    }

    @Test
    void aWaitlistRowWrittenBeforeTheCardFieldsStillDeliversAsASimplerBubble() {
        UUID eventId = record(OutboxEventType.WAITLIST_PROMOTED,
                Map.of("trip", "AU to Asok, departing 2 Oct 2026 at 15:14.", "detail", "Take the seats by 09:00."));

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(texts(sender.messages().getFirst().message()))
                .contains("AU to Asok, departing 2 Oct 2026 at 15:14.", "Take the seats by 09:00.");
    }

    @Test
    void aRowCarryingAFieldThisVersionDoesNotKnowStillDelivers() {
        UUID eventId = record(OutboxEventType.BOOKING_CREATED,
                Map.of("reference", "AUV-261002-NEWER", "detail", "Booked seats A1.", "gate", "B"));

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    private UUID record(OutboxEventType type, Object payload) {
        UUID eventId = recorder.record(type, UUID.randomUUID(), student, payload, OffsetDateTime.now()).getId();
        // The column keeps less precision than the clock, so a row recorded now can read as not yet due.
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), eventId);
        return eventId;
    }
}
