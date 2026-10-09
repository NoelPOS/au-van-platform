package com.auvan.api.booking;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DepartureReminderIntegrationTests extends DepartureReminderTestSupport {
    @Test
    void approvingABookingSchedulesBothDepartureRemindersForTheStudent() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-both");

        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H)).satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_24H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(24), within(1, ChronoUnit.SECONDS));
            assertThat(event.getPayload()).contains("\"origin\":\"AU\"", "\"destination\":\"Asok\"")
                    .containsPattern("\"seats\":\\[\"A").containsPattern("\"departureAt\":\"20");
        });
        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H)).satisfies(event -> {
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_1H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.SECONDS));
        });
    }

    @Test
    void aReminderWhoseMomentHasAlreadyPassedIsNotQueuedAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofHours(12)), "key-twelve");

        assertThat(remindersOf(bookingId)).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.DEPARTURE_REMINDER_1H);
    }

    @Test
    void aBookingApprovedInsideTheLastHourGetsNoReminderAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofMinutes(30)), "key-thirty");

        assertThat(remindersOf(bookingId)).isEmpty();
    }

    @Test
    void schedulingTheSameRemindersAgainLeavesOneRowPerOffset() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-twice");

        int queuedAgain = transactions.execute(status ->
                reminders.schedule(bookings.findById(bookingId).orElseThrow(), OffsetDateTime.now()));

        assertThat(queuedAgain).isZero();
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Test
    void theDedupeKeyIsUniqueSoASecondRowCarryingItCannotBeStoredAtAll() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-constraint");
        OutboxEvent scheduled = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H);

        assertThatThrownBy(() -> insertRowCarrying(scheduled.getDedupeKey()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Test
    void aReminderIsNotDispatchedBeforeItsTimeAndIsDispatchedAfterIt() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-due");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).isEmpty();

        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Ustudent-reminder");
            assertThat(message.message().altText()).contains("departs in 1 hour").contains("AU to Asok");
            assertThat(message.retryKey()).isEqualTo(reminder.getId().toString());
        });
        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Transactional
    void insertRowCarrying(String dedupeKey) {
        jdbc.update("""
                insert into outbox_events (id, event_type, aggregate_id, recipient_user_id, payload, status,
                                           attempts, next_attempt_at, dedupe_key, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), OutboxEventType.DEPARTURE_REMINDER_24H.name(), UUID.randomUUID(), student,
                "{}", OutboxStatus.PENDING.name(), 0, OffsetDateTime.now().plusDays(1), dedupeKey,
                OffsetDateTime.now());
    }
}
