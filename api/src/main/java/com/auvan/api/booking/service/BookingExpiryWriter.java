package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One booking's expiry, in one transaction: the cancellation, the history
 * entry, the outbox row, and the seat release commit together or not at all.
 *
 * <p>It is a bean of its own rather than a method on {@link BookingExpiryService}
 * for the reason {@link BookingWriter} is — the sweep must stay outside any
 * transaction so that each booking gets its own, and a {@code @Transactional}
 * method called from a sibling method of the same bean would bypass the proxy
 * and quietly run in whatever transaction it found.
 */
@Service
public class BookingExpiryWriter {
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;

    public BookingExpiryWriter(BookingRepository bookings, SeatClaimRepository claims, OutboxRecorder outbox,
                               DepartureReminderService reminders) {
        this.bookings = bookings;
        this.claims = claims;
        this.outbox = outbox;
        this.reminders = reminders;
    }

    /**
     * Expires one booking, if it is still expirable once its row is held.
     *
     * <p>The lock is the first thing this does and <strong>every decision below
     * is made from what it returned</strong>. The candidate list this id came
     * from was read outside this transaction and is only a hint: an
     * administrator's approval or the student's own cancellation may have
     * committed since. Without the re-read behind the lock, the sweep cancels a
     * booking that was just confirmed and deletes the claims of a seat somebody
     * has paid for — the same failure {@link BookingService#cancel} documents
     * for cancellation racing a review.
     *
     * @return whether this call is the one that expired the booking
     */
    @Transactional
    public boolean expire(UUID bookingId, OffsetDateTime now) {
        Booking booking = bookings.lockById(bookingId).orElse(null);
        // Not an error, and the ordinary outcome of a lost race: the booking was
        // confirmed, cancelled, or had its deadline pushed out while this sweep
        // was reading its candidates.
        if (booking == null || !booking.isExpirable(now)) {
            return false;
        }

        booking.expire(now);
        String detail = "Expired unpaid and released seats " + labelsOf(booking) + ".";
        // No actor. booking_events.actor_user_id is nullable for exactly this,
        // and nothing else in the system takes a seat away with nobody asking.
        booking.recordEvent(BookingEventType.EXPIRED, detail, null, now);
        // Recorded before the flush, for the same reason the flush exists.
        // deleteByBookingId clears the persistence context, and an outbox insert
        // that has not been flushed by then is discarded in silence: the seats
        // would be released with nothing saying the student was ever told.
        outbox.record(OutboxEventType.BOOKING_EXPIRED, booking.getId(), booking.getUserId(),
                new BookingNotification(booking.getReference(), detail), now);
        // Load-bearing, and it must come before the delete: deleteByBookingId
        // clears the persistence context, which would discard the expiry and the
        // event above without raising anything at all.
        bookings.flush();
        // By booking, never by claim id: deleteByIdIn deliberately refuses rows
        // that carry a booking_id, which is exactly what these are, so it would
        // report success and free nothing.
        claims.deleteByBookingId(booking.getId());
        // An expired booking is not departing either. Placed last for the same
        // reason cancellation places it last, and scoped the same way, so the
        // BOOKING_EXPIRED row this expiry just recorded still reaches the
        // student while the reminders that would have followed it do not.
        reminders.cancel(booking.getId(), now);
        return true;
    }

    private static String labelsOf(Booking booking) {
        return booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
