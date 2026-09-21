package com.auvan.api.booking.service;

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

/**
 * The one transaction that turns a hold into a booking: the booking, its seats,
 * its first history entry, the claims that now carry it, and the idempotency
 * record all commit together or not at all.
 *
 * <p>It is a bean of its own rather than a method on {@link BookingService} so
 * that the orchestrator can stay outside any transaction. Once a constraint
 * violation is converted at flush the transaction is already rollback-only, so
 * the replay that follows a lost race has to run somewhere else entirely.
 */
@Service
public class BookingWriter {
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final IdempotencyService idempotency;
    private final OutboxRecorder outbox;

    public BookingWriter(BookingRepository bookings, SeatClaimRepository claims, IdempotencyService idempotency,
                         OutboxRecorder outbox) {
        this.bookings = bookings;
        this.claims = claims;
        this.idempotency = idempotency;
        this.outbox = outbox;
    }

    @Transactional
    public IdempotencyService.StoredResponse create(UUID userId, String endpoint, String key, String requestHash,
                                                    CreateBookingRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        // Locking read first, and every decision below is made from what it
        // returned. Two confirmations of one hold both only UPDATE rows that
        // already exist, so the unique constraint sees nothing wrong with them.
        List<SeatClaim> held = claims.lockByHoldId(request.holdId());
        assertConfirmable(held, userId, now);

        Trip trip = held.getFirst().getTripSeat().getTrip();
        assertBookable(trip, now);

        Booking booking = new Booking(trip, userId, BookingReference.generate(now), request.passengerName(),
                request.passengerPhone(), trip.getFare().multiply(BigDecimal.valueOf(held.size())), now);
        held.forEach(claim -> booking.addSeat(claim.getTripSeat()));
        String detail = "Booked seats " + labelsOf(held) + ".";
        booking.recordEvent(BookingEventType.CREATED, detail, userId, now);

        try {
            bookings.save(booking);
            // Mutating the managed rows the lock returned, rather than issuing a
            // bulk update: a bulk update would run before the booking insert and
            // break seat_claims' foreign key.
            held.forEach(claim -> claim.attachTo(booking.getId()));
            // Load-bearing. @UuidGenerator is not an identity generator, so the
            // inserts would otherwise defer to the commit and this catch could
            // never run; a lost race would escape as a 500.
            bookings.flush();
        } catch (DataIntegrityViolationException collision) {
            throw Problems.conflict("booking_creation_conflict",
                    "That booking could not be completed. Please try again.", collision);
        }

        // Inside this transaction, which is the point: this method is where the
        // outbox write belongs precisely because BookingService.create has no
        // transaction of its own to join. Recording there would leave the row
        // committed independently of the booking it describes — and every test
        // that only counts rows would still pass.
        outbox.record(OutboxEventType.BOOKING_CREATED, booking.getId(), userId,
                new BookingNotification(booking.getReference(), detail), now);

        return idempotency.record(userId, endpoint, key, requestHash, HttpStatus.CREATED.value(),
                BookingResponse.from(booking), now);
    }

    /**
     * Three questions, three answers. {@code SeatClaim.isHeldBy} folds "booked"
     * into "not yours", which would tell a student confirming their own hold
     * twice that the hold never existed.
     */
    private void assertConfirmable(List<SeatClaim> held, UUID userId, OffsetDateTime now) {
        // Someone else's hold answers exactly as a hold that never existed, so
        // the endpoint cannot be used to discover which hold ids are live.
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
