package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

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

    @Transactional
    public boolean expire(UUID bookingId, OffsetDateTime now) {
        Booking booking = bookings.lockById(bookingId).orElse(null);
        if (booking == null || !booking.isExpirable(now)) {
            return false;
        }

        booking.expire(now);
        String detail = "Expired unpaid and released seats " + labelsOf(booking) + ".";
        booking.recordEvent(BookingEventType.EXPIRED, detail, null, now);
        // Record and flush before deleteByBookingId: it clears the context and would silently
        // discard the expiry and its outbox row.
        outbox.record(OutboxEventType.BOOKING_EXPIRED, booking.getId(), booking.getUserId(),
                BookingNotifications.of(booking, detail), now);
        bookings.flush();
        claims.deleteByBookingId(booking.getId());
        reminders.cancel(booking.getId(), now);
        return true;
    }

    private static String labelsOf(Booking booking) {
        return booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
