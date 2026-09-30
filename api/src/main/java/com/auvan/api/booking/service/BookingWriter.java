package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingWriter {
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final IdempotencyService idempotency;
    private final OutboxRecorder outbox;
    private final BookingProperties properties;

    public BookingWriter(BookingRepository bookings, SeatClaimRepository claims, IdempotencyService idempotency,
                         OutboxRecorder outbox, BookingProperties properties) {
        this.bookings = bookings;
        this.claims = claims;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.properties = properties;
    }

    @Transactional
    public IdempotencyService.StoredResponse create(UUID userId, String endpoint, String key, String requestHash,
                                                    CreateBookingRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        List<SeatClaim> held = claims.lockByHoldId(request.holdId());
        assertConfirmable(held, userId, now);

        Trip trip = held.getFirst().getTripSeat().getTrip();
        assertBookable(trip, now);

        Booking booking = new Booking(trip, userId, BookingReference.generate(now), request.passengerName(),
                request.passengerPhone(), trip.getFare().multiply(BigDecimal.valueOf(held.size())),
                properties.paymentDeadlineFor(trip.getDepartureAt(), now), now);
        held.forEach(claim -> booking.addSeat(claim.getTripSeat()));
        String detail = "Booked seats " + labelsOf(held) + ".";
        booking.recordEvent(BookingEventType.CREATED, detail, userId, now);

        try {
            bookings.save(booking);
            // Mutate the locked rows: a bulk update would run before the insert and break the FK.
            held.forEach(claim -> claim.attachTo(booking.getId()));
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            bookings.flush();
        } catch (DataIntegrityViolationException collision) {
            throw Problems.conflict("booking_creation_conflict",
                    "That booking could not be completed. Please try again.", collision);
        }

        outbox.record(OutboxEventType.BOOKING_CREATED, booking.getId(), userId,
                new BookingNotification(booking.getReference(), detail), now);

        return idempotency.record(userId, endpoint, key, requestHash, HttpStatus.CREATED.value(),
                BookingResponse.from(booking), now);
    }

    private void assertConfirmable(List<SeatClaim> held, UUID userId, OffsetDateTime now) {
        if (held.isEmpty() || held.stream().anyMatch(claim -> !claim.isOwnedBy(userId))) {
            throw Problems.notFound("hold_not_found", "Hold not found.");
        }
        if (held.stream().anyMatch(SeatClaim::isBooked)) {
            throw Problems.conflict("hold_already_used", "That hold has already been booked.");
        }
        if (held.stream().anyMatch(claim -> claim.hasExpiredAt(now))) {
            throw Problems.conflict("hold_expired", "That hold has expired. Please pick your seats again.");
        }
    }

    private void assertBookable(Trip trip, OffsetDateTime now) {
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw Problems.conflict("trip_not_available", "This trip is no longer available.");
        }
        if (!trip.getDepartureAt().isAfter(now)) {
            throw Problems.conflict("trip_departed", "This trip has already departed.");
        }
    }

    private static String labelsOf(List<SeatClaim> held) {
        return held.stream().map(SeatClaim::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
