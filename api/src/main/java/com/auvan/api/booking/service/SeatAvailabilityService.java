package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.SeatState;
import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.dto.TripSummaryResponse;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
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
        Map<UUID, SeatClaim> blocking = blockingClaimsBySeat(tripId, now);
        List<TripSeatMapResponse.SeatResponse> seats = trip.getSeats().stream()
                .map(seat -> new TripSeatMapResponse.SeatResponse(seat.getId(), seat.getLabel(), seat.getRowNumber(),
                        seat.getColumnNumber(), stateOf(blocking.get(seat.getId()), userId)))
                .toList();
        return new TripSeatMapResponse(trip.getId(), trip.getDepartureAt(), trip.getFare(), seats);
    }

    /**
     * The seats of one trip that nothing is blocking right now, in seat order.
     *
     * <p>This is the promotion sweep's definition of "free" and it is the seat
     * map's, because it is the same derivation: both read
     * {@link #blockingClaimsBySeat}. ADR-011 asked for exactly that. A second,
     * slightly different predicate in the promoter would let it promote onto a
     * seat somebody is holding, {@code seat_claims_trip_seat_unique} would
     * refuse the insert, and the bug would show up as a promotion that silently
     * never happens rather than as an oversell — far harder to notice.
     *
     * <p>Expiry is lazy, so a seat whose hold has lapsed is free although the
     * lapsed row is still sitting on it. Whoever takes the seat reclaims that
     * row; this read writes nothing.
     */
    @Transactional(readOnly = true)
    public List<TripSeat> freeSeatsOf(Trip trip, OffsetDateTime now) {
        Map<UUID, SeatClaim> blocking = blockingClaimsBySeat(trip.getId(), now);
        return trip.getSeats().stream().filter(seat -> !blocking.containsKey(seat.getId())).toList();
    }

    /**
     * The claims that are blocking a seat of this trip at {@code now}, by seat
     * id. A seat missing from the map is free, by definition and by
     * {@link SeatClaim#blocksSeatAt} — the one predicate every reader of seat
     * state in this application goes through.
     */
    private Map<UUID, SeatClaim> blockingClaimsBySeat(UUID tripId, OffsetDateTime now) {
        return claims.findByTripIdIn(List.of(tripId)).stream()
                .filter(claim -> claim.blocksSeatAt(now))
                .collect(Collectors.toMap(claim -> claim.getTripSeat().getId(), Function.identity()));
    }

    private static SeatState stateOf(SeatClaim blocking, UUID userId) {
        if (blocking == null) {
            return SeatState.AVAILABLE;
        }
        if (blocking.isBooked()) {
            return SeatState.BOOKED;
        }
        return blocking.isHeldBy(userId) ? SeatState.HELD_BY_YOU : SeatState.HELD;
    }
}
