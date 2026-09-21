package com.auvan.api.booking.dto;

import com.auvan.api.inventory.entity.Trip;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TripSummaryResponse(
        UUID id,
        UUID routeId,
        String origin,
        String destination,
        OffsetDateTime departureAt,
        BigDecimal fare,
        int durationMinutes,
        int totalSeats,
        int availableSeats) {
    public static TripSummaryResponse from(Trip trip, long claimedSeats) {
        int totalSeats = trip.getSeats().size();
        return new TripSummaryResponse(trip.getId(), trip.getRoute().getId(), trip.getRoute().getOrigin(),
                trip.getRoute().getDestination(), trip.getDepartureAt(), trip.getFare(), trip.getDurationMinutes(),
                totalSeats, totalSeats - (int) claimedSeats);
    }
}
