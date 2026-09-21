package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatHoldResponse;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates and releases seat holds. The unique constraint on
 * {@code seat_claims.trip_seat_id} is the only thing that decides a race, so
 * the checks here exist to produce a readable message, not to prevent oversell.
 */
@Service
public class SeatHoldService {
    private static final String SEAT_TAKEN = "Someone else just took one of those seats. Please refresh and pick again.";

    private final SeatClaimRepository claims;
    private final TripRepository trips;
    private final BookingProperties properties;

    public SeatHoldService(SeatClaimRepository claims, TripRepository trips, BookingProperties properties) {
        this.claims = claims;
        this.trips = trips;
        this.properties = properties;
    }

    @Transactional
    public SeatHoldResponse hold(UUID userId, CreateSeatHoldRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = trips.findById(request.tripId())
                .orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        assertBookable(trip, now);
        List<TripSeat> seats = seatsOf(trip, request.seatIds());

        List<SeatClaim> onRequestedSeats = claims.findBySeatIdIn(request.seatIds());
        if (onRequestedSeats.stream().anyMatch(claim -> claim.blocksSeatAt(now) && !claim.isHeldBy(userId))) {
            throw Problems.conflict("seat_taken", SEAT_TAKEN);
        }

        // Re-selecting replaces the caller's whole hold on this trip, and any
        // expired claim on a requested seat is reclaimed in the same breath. The
        // two sets overlap, so they are deduplicated by id rather than by
        // instance identity.
        List<UUID> reclaimed = Stream.concat(
                        claims.findHoldsOnTripBy(trip.getId(), userId).stream(),
                        onRequestedSeats.stream().filter(claim -> !claim.blocksSeatAt(now)))
                .map(SeatClaim::getId)
                .distinct()
                .toList();
        if (!reclaimed.isEmpty()) {
            // A bulk delete issues its statement at once, so the reclaim reaches the
            // database before the inserts below. Left to a normal flush, Hibernate
            // would order the inserts first and a student re-selecting a seat they
            // already hold would collide with their own row.
            claims.deleteByIdIn(reclaimed);
        }

        UUID holdId = UUID.randomUUID();
        OffsetDateTime expiresAt = now.plus(properties.holdTtl());
        List<SeatClaim> held = seats.stream().map(seat -> new SeatClaim(seat, userId, holdId, expiresAt)).toList();
        try {
            claims.saveAll(held);
            // @UuidGenerator is not an identity generator, so the insert would
            // otherwise defer to the commit-time flush, long after this catch is
            // out of scope, and a lost race would surface as a 500.
            claims.flush();
        } catch (DataIntegrityViolationException exception) {
            throw Problems.conflict("seat_taken", SEAT_TAKEN, exception);
        }
        return SeatHoldResponse.from(holdId, trip.getId(), expiresAt, held);
    }

    @Transactional
    public void release(UUID userId, UUID holdId) {
        List<SeatClaim> held = claims.findByHoldId(holdId);
        // Someone else's hold answers exactly as a hold that never existed, so the
        // endpoint cannot be used to find out which hold ids are live.
        if (held.isEmpty() || held.stream().anyMatch(claim -> !claim.isHeldBy(userId))) {
            throw Problems.notFound("hold_not_found", "Hold not found.");
        }
        // Bulk delete for the same reason as the reclaim above: deleting managed
        // entities row-count-checks, so two simultaneous releases of one hold give
        // the loser an untranslated failure rather than a quiet no-op.
        claims.deleteByIdIn(held.stream().map(SeatClaim::getId).toList());
    }

    private void assertBookable(Trip trip, OffsetDateTime now) {
        if (trip.getStatus() != TripStatus.ACTIVE) {
            throw Problems.conflict("trip_not_available", "This trip is no longer available.");
        }
        if (!trip.getDepartureAt().isAfter(now)) {
            throw Problems.conflict("trip_departed", "This trip has already departed.");
        }
    }

    private List<TripSeat> seatsOf(Trip trip, List<UUID> seatIds) {
        if (seatIds.size() > properties.maxSeatsPerHold()) {
            throw Problems.badRequest("too_many_seats",
                    "You can hold at most " + properties.maxSeatsPerHold() + " seats at a time.");
        }
        if (seatIds.stream().distinct().count() != seatIds.size()) {
            throw Problems.badRequest("duplicate_seat", "Each seat can only be selected once.");
        }
        Map<UUID, TripSeat> seatsOfTrip = trip.getSeats().stream()
                .collect(Collectors.toMap(TripSeat::getId, Function.identity()));
        return seatIds.stream()
                .map(seatId -> {
                    TripSeat seat = seatsOfTrip.get(seatId);
                    if (seat == null) {
                        throw Problems.badRequest("seat_not_on_trip",
                                "Those seats do not belong to this trip.");
                    }
                    return seat;
                })
                .toList();
    }
}
