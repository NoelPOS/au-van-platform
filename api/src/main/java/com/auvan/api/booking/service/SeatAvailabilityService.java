package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.SeatState;
import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.dto.TripSummaryResponse;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reads seat state. Expiry is lazy: these methods compare {@code expires_at} to
 * the current time and write nothing, so an expired hold reads as available
 * whether or not anything has reclaimed its row yet.
 */
@Service
public class SeatAvailabilityService {
    private final TripRepository trips;
    private final SeatClaimRepository claims;

    public SeatAvailabilityService(TripRepository trips, SeatClaimRepository claims) {
        this.trips = trips;
        this.claims = claims;
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listBookableTrips() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Trip> bookable = trips.findBookable(TripStatus.ACTIVE, now);
        if (bookable.isEmpty()) {
            return List.of();
        }
        Map<UUID, Long> claimedPerTrip = claims.findByTripIdIn(bookable.stream().map(Trip::getId).toList()).stream()
                .filter(claim -> claim.blocksSeatAt(now))
                .collect(Collectors.groupingBy(claim -> claim.getTripSeat().getTrip().getId(), Collectors.counting()));
        return bookable.stream()
                .map(trip -> TripSummaryResponse.from(trip, claimedPerTrip.getOrDefault(trip.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public TripSeatMapResponse seatMap(UUID tripId, UUID userId) {
        OffsetDateTime now = OffsetDateTime.now();
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        Map<UUID, SeatClaim> claimsBySeat = claims.findByTripIdIn(List.of(tripId)).stream()
                .collect(Collectors.toMap(claim -> claim.getTripSeat().getId(), Function.identity()));
        List<TripSeatMapResponse.SeatResponse> seats = trip.getSeats().stream()
                .map(seat -> new TripSeatMapResponse.SeatResponse(seat.getId(), seat.getLabel(), seat.getRowNumber(),
                        seat.getColumnNumber(), stateOf(claimsBySeat.get(seat.getId()), userId, now)))
                .toList();
        return new TripSeatMapResponse(trip.getId(), trip.getDepartureAt(), trip.getFare(), seats);
    }

    private static SeatState stateOf(SeatClaim claim, UUID userId, OffsetDateTime now) {
        if (claim == null || !claim.blocksSeatAt(now)) {
            return SeatState.AVAILABLE;
        }
        if (claim.isBooked()) {
            return SeatState.BOOKED;
        }
        return claim.isHeldBy(userId) ? SeatState.HELD_BY_YOU : SeatState.HELD;
    }
}
