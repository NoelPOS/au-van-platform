package com.auvan.api.booking.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TripSeatMapResponse(
        UUID tripId,
        OffsetDateTime departureAt,
        OffsetDateTime bookingClosesAt,
        BigDecimal fare,
        List<SeatResponse> seats) {
    public record SeatResponse(UUID id, String label, int rowNumber, int columnNumber, SeatState state) { }
}
