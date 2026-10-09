package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TripResponse(
        UUID id,
        UUID routeId,
        UUID vehicleId,
        OffsetDateTime departureAt,
        BigDecimal fare,
        int durationMinutes,
        TripStatus status,
        String cancellationReason,
        List<SeatResponse> seats) {
    public static TripResponse from(Trip trip) {
        return new TripResponse(trip.getId(), trip.getRoute().getId(), trip.getVehicle().getId(), trip.getDepartureAt(),
                trip.getFare(), trip.getDurationMinutes(), trip.getStatus(), trip.getCancellationReason(),
                trip.getSeats().stream().map(SeatResponse::from).toList());
    }

    public record SeatResponse(String label, int rowNumber, int columnNumber) {
        static SeatResponse from(TripSeat seat) {
            return new SeatResponse(seat.getLabel(), seat.getRowNumber(), seat.getColumnNumber());
        }
    }
}
