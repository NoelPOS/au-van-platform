package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.dto.TripResponse;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TripChangeService {
    private final TripRepository trips;
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final WaitlistEntryRepository entries;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;

    public TripChangeService(TripRepository trips, BookingRepository bookings, SeatClaimRepository claims,
                                   WaitlistEntryRepository entries, OutboxRecorder outbox,
                                   DepartureReminderService reminders) {
        this.trips = trips;
        this.bookings = bookings;
        this.claims = claims;
        this.entries = entries;
        this.outbox = outbox;
        this.reminders = reminders;
    }

    @Transactional
    public TripResponse cancel(UUID adminId, UUID tripId, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = lockChangeable(tripId, now);
        String why = reason.trim();
        trip.cancel(why);

        bookings.findActiveIdsByTripId(tripId).forEach(bookingId -> cancelBooking(bookingId, adminId, why, now));
        entries.findQueuedIdsByTripId(tripId).forEach(entryId -> closeEntry(entryId, trip, why, now));

        TripResponse response = TripResponse.from(trip);
        // Flush before deleteBookedOnTrip: it clears the context and would silently discard the cascade.
        bookings.flush();
        claims.deleteBookedOnTrip(tripId);
        return response;
    }

    // Locked before any booking is read: BookingWriter takes the same lock, so none joins mid-change.
    private Trip lockChangeable(UUID tripId, OffsetDateTime now) {
        Trip trip = trips.lockById(tripId).orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        if (trip.hasDepartedAt(now)) {
            throw Problems.conflict("trip_departed", "This trip has already departed and can no longer be changed.");
        }
        if (trip.isCancelled()) {
            throw Problems.conflict("trip_cancelled",
                    "This trip has been cancelled and can no longer be changed. Create a new trip instead.");
        }
        return trip;
    }

    private void cancelBooking(UUID bookingId, UUID adminId, String reason, OffsetDateTime now) {
        Booking booking = bookings.lockById(bookingId).orElseThrow();
        if (booking.isCancelled()) {
            return;
        }
        booking.cancelWithTrip(now);
        booking.recordEvent(BookingEventType.TRIP_CANCELLED, "Trip cancelled and seats " + labelsOf(booking)
                + " released." + BookingNotifications.refundNote(booking) + " Reason: " + reason, adminId, now);
        outbox.record(OutboxEventType.TRIP_CANCELLED, booking.getId(), booking.getUserId(),
                BookingNotifications.of(booking, reason), now);
        reminders.cancel(booking.getId(), now);
    }

    private void closeEntry(UUID entryId, Trip trip, String reason, OffsetDateTime now) {
        WaitlistEntry entry = entries.lockById(entryId).orElseThrow();
        if (!entry.isQueued()) {
            return;
        }
        entry.cancel(now);
        outbox.record(OutboxEventType.TRIP_CANCELLED, entry.getId(), entry.getUserId(),
                new BookingNotification(null, reason, trip.getRoute().getOrigin(), trip.getRoute().getDestination(),
                        trip.getDepartureAt(), null, null, null), now);
    }

    private static String labelsOf(Booking booking) {
        return booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
