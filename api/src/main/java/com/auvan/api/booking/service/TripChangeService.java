package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
import com.auvan.api.booking.dto.UpdateTripRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.dto.TripResponse;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
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
    private final WaitlistEntryRepository waitlist;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;
    private final BookingProperties properties;
    private final LiveSignalPublisher live;

    public TripChangeService(TripRepository trips, BookingRepository bookings, SeatClaimRepository claims,
                             WaitlistEntryRepository waitlist, OutboxRecorder outbox,
                             DepartureReminderService reminders, BookingProperties properties,
                             LiveSignalPublisher live) {
        this.trips = trips;
        this.bookings = bookings;
        this.claims = claims;
        this.waitlist = waitlist;
        this.outbox = outbox;
        this.reminders = reminders;
        this.properties = properties;
        this.live = live;
    }

    @Transactional
    public TripResponse update(UUID adminId, UUID tripId, UpdateTripRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = lockChangeable(tripId, now);
        if (request.status() == TripStatus.CANCELLED) {
            throw Problems.badRequest("trip_cancellation_needs_reason",
                    "Cancel a trip with its cancel action, which asks for a reason to tell the passengers.");
        }
        OffsetDateTime previous = trip.getDepartureAt();
        OffsetDateTime departureAt = request.departureAt() == null ? previous : request.departureAt();
        if (departureAt.isEqual(previous)) {
            return TripResponse.from(trip);
        }
        assertCanMoveTo(trip, departureAt, now);
        trip.reschedule(departureAt);
        String detail = "Departure moved from " + BookingNotifications.moment(previous) + " to "
                + BookingNotifications.moment(departureAt) + ".";
        bookings.findActiveIdsByTripId(tripId).forEach(bookingId -> retimeBooking(bookingId, adminId, detail, now));
        live.publish(LiveSignal.trip(tripId));
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse cancel(UUID adminId, UUID tripId, String reason) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = lockChangeable(tripId, now);
        String trimmedReason = reason.trim();
        trip.cancel(trimmedReason);

        bookings.findActiveIdsByTripId(tripId)
                .forEach(bookingId -> cancelBooking(bookingId, adminId, trimmedReason, now));
        waitlist.findQueuedIdsByTripId(tripId)
                .forEach(entryId -> cancelWaitlistEntry(entryId, trip, trimmedReason, now));

        TripResponse response = TripResponse.from(trip);
        // Flush before deleteBookedOnTrip: it clears the context and would silently discard the cascade.
        bookings.flush();
        claims.deleteBookedOnTrip(tripId);
        live.publish(LiveSignal.trip(tripId));
        return response;
    }

    private void assertCanMoveTo(Trip trip, OffsetDateTime departureAt, OffsetDateTime now) {
        if (!now.isBefore(properties.bookingClosesAt(departureAt))) {
            throw Problems.badRequest("departure_too_soon", "A trip can only be moved to a time at least "
                    + properties.closesBeforeDeparture().toMinutes() + " minutes from now.");
        }
        if (trips.existsByVehicleIdAndDepartureAtAndIdNot(trip.getVehicle().getId(), departureAt, trip.getId())) {
            throw Problems.conflict("vehicle_already_scheduled",
                    "This vehicle already has a trip at that departure time.");
        }
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

    private void retimeBooking(UUID bookingId, UUID adminId, String detail, OffsetDateTime now) {
        Booking booking = bookings.lockById(bookingId).orElseThrow();
        if (booking.isCancelled()) {
            return;
        }
        booking.capPaymentDeadlineAt(properties.departureBoundFor(booking.getTrip().getDepartureAt()), now);
        booking.recordEvent(BookingEventType.TRIP_RESCHEDULED, detail, adminId, now);
        outbox.record(OutboxEventType.TRIP_RESCHEDULED, booking.getId(), booking.getUserId(),
                BookingNotifications.of(booking, detail), now);
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            reminders.reschedule(booking, now);
        }
    }

    private void cancelWaitlistEntry(UUID entryId, Trip trip, String reason, OffsetDateTime now) {
        WaitlistEntry entry = waitlist.lockById(entryId).orElseThrow();
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
