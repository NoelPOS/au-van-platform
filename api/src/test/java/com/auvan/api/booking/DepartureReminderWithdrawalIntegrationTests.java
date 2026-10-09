package com.auvan.api.booking;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DepartureReminderWithdrawalIntegrationTests extends DepartureReminderTestSupport {
    @Test
    void cancellingABookingKillsItsUnsentRemindersAndLeavesItsCancellationMessageAlone() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-cancel");

        bookingService.cancel(student, bookingId);

        assertThat(remindersOf(bookingId)).hasSize(2).allSatisfy(reminder -> {
            assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(reminder.getLastError()).contains("no longer eligible");
            assertThat(reminder.getProcessedAt()).isNotNull();
        });
        assertThat(eventsOfType(OutboxEventType.BOOKING_CANCELLED)).singleElement().satisfies(cancelled ->
                assertThat(cancelled.getStatus()).isEqualTo(OutboxStatus.PENDING));

        dueAt(remindersOf(bookingId).getFirst().getId(), OffsetDateTime.now().minusSeconds(1));
        dispatcher.dispatchBatch();
        assertThat(sender.messages()).noneSatisfy(message ->
                assertThat(message.message().altText()).contains("departs in"));
    }

    @Test
    void aReminderAlreadyDueButNotYetClaimedIsStillWithdrawnByCancellation() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-due-cancel");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();
        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));
        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(reminder.getId()).orElseThrow()).satisfies(withdrawn -> {
            assertThat(withdrawn.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(withdrawn.getLastError()).contains("no longer eligible");
        });

        dispatcher.dispatchBatch();
        assertThat(sender.messages()).noneSatisfy(message ->
                assertThat(message.message().altText()).contains("departs in"));
    }

    @Test
    void expiringABookingKillsItsUnsentReminders() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = createBooking(trip, "key-expire", trip.getSeats().getFirst());
        transactions.executeWithoutResult(status ->
                reminders.schedule(bookings.findById(bookingId).orElseThrow(), OffsetDateTime.now()));
        assertThat(remindersOf(bookingId)).hasSize(2);
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(remindersOf(bookingId)).hasSize(2).allSatisfy(reminder ->
                assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD));
        assertThat(eventsOfType(OutboxEventType.BOOKING_EXPIRED)).singleElement().satisfies(expired ->
                assertThat(expired.getStatus()).isEqualTo(OutboxStatus.PENDING));
    }

    @Test
    void aStateChangeNotificationBackingOffAfterAFailureIsNotWithdrawnWithTheReminders() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-backoff");
        sender.failNext(1);
        dispatcher.dispatchBatch();
        OutboxEvent backingOff = events.findAll().stream()
                .filter(event -> event.getDedupeKey() == null && event.getAttempts() > 0)
                .findFirst().orElseThrow();
        assertThat(backingOff.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(backingOff.getNextAttemptAt()).isAfter(OffsetDateTime.now());

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(backingOff.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PENDING);
        assertThat(remindersOf(bookingId)).allSatisfy(reminder ->
                assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD));
    }

    @Test
    void aReminderThatHasAlreadyBeenSentIsNotTouchedByTheWithdrawal() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-sent");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();
        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isOne();

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    private void overdue(UUID bookingId) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(5), bookingId);
    }

    private List<OutboxEvent> eventsOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }
}
