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

        List<UUID> reclaimed = Stream.concat(
                        claims.findHoldsOnTripBy(trip.getId(), userId).stream(),
                        onRequestedSeats.stream().filter(claim -> !claim.blocksSeatAt(now)))
                .map(SeatClaim::getId)
                .distinct()
                .toList();
        if (!reclaimed.isEmpty()) {
            // Bulk delete runs at once; a flush would order the inserts below first and collide.
            claims.deleteByIdIn(reclaimed);
        }

        UUID holdId = UUID.randomUUID();
        OffsetDateTime expiresAt = now.plus(properties.holdTtl());
        List<SeatClaim> held = seats.stream().map(seat -> new SeatClaim(seat, userId, holdId, expiresAt)).toList();
        try {
            claims.saveAll(held);
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            claims.flush();
        } catch (DataIntegrityViolationException exception) {
            throw Problems.conflict("seat_taken", SEAT_TAKEN, exception);
        }
        return SeatHoldResponse.from(holdId, trip.getId(), expiresAt, held);
    }

    @Transactional
    public void release(UUID userId, UUID holdId) {
        List<SeatClaim> held = claims.findByHoldId(holdId);
        if (held.isEmpty() || held.stream().anyMatch(claim -> !claim.isHeldBy(userId))) {
            throw Problems.notFound("hold_not_found", "Hold not found.");
        }
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
