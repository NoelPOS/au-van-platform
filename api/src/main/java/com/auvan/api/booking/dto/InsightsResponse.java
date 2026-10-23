package com.auvan.api.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InsightsResponse(Totals totals, List<RouteRow> byRoute, List<DayRow> byDay) {

    public record Totals(
            BigDecimal revenue,
            BigDecimal refundsDue,
            BigDecimal refunded,
            BigDecimal awaitingPayment,
            long bookings,
            long seatsSold,
            long seatsOffered,
            double occupancy,
            long trips,
            long cancelledTrips) { }

    public record RouteRow(
            UUID routeId,
            String origin,
            String destination,
            long trips,
            long seatsSold,
            long seatsOffered,
            double occupancy,
            BigDecimal revenue) { }

    public record DayRow(LocalDate date, BigDecimal revenue, long seatsSold) { }
}
