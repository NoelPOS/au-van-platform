package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
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

@Service
public class SeatAvailabilityService {
    private final TripRepository trips;
    private final SeatClaimRepository claims;
    private final BookingProperties properties;

    public SeatAvailabilityService(TripRepository trips, SeatClaimRepository claims, BookingProperties properties) {
        this.trips = trips;
        this.claims = claims;
        this.properties = properties;
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
                .map(trip -> TripSummaryResponse.from(trip, properties.bookingClosesAt(trip.getDepartureAt()),
                        claimedPerTrip.getOrDefault(trip.getId(), 0L)))
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
        return new TripSeatMapResponse(trip.getId(), trip.getDepartureAt(),
                properties.bookingClosesAt(trip.getDepartureAt()), trip.getFare(), seats);
    }

    @Transactional(readOnly = true)
    public List<TripSeat> freeSeatsOf(Trip trip, OffsetDateTime now) {
        Map<UUID, SeatClaim> blocking = blockingClaimsBySeat(trip.getId(), now);
        return trip.getSeats().stream().filter(seat -> !blocking.containsKey(seat.getId())).toList();
    }

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
