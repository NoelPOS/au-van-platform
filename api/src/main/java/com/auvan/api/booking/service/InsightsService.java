package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.InsightsResponse;
import com.auvan.api.booking.dto.InsightsResponse.DayRow;
import com.auvan.api.booking.dto.InsightsResponse.RouteRow;
import com.auvan.api.booking.dto.InsightsResponse.Totals;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.BookingRepository.TripFigures;
import com.auvan.api.inventory.entity.TripStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

@Service
public class InsightsService {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final int LONGEST_RANGE_DAYS = 366;

    private final BookingRepository bookings;

    public InsightsService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public InsightsResponse between(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw Problems.badRequest("insights_range_reversed", "The period must start on or before its last day.");
        }
        if (ChronoUnit.DAYS.between(from, to) >= LONGEST_RANGE_DAYS) {
            throw Problems.badRequest("insights_range_too_long", "The period can cover at most 366 days.");
        }
        List<TripFigures> figures = bookings.findTripFiguresBetween(startOf(from), startOf(to.plusDays(1)));
        List<TripFigures> running = figures.stream()
                .filter(trip -> trip.status() != TripStatus.CANCELLED)
                .toList();
        return new InsightsResponse(totalsOf(figures, running), byRoute(running), byDay(figures, from, to));
    }

    private static Totals totalsOf(List<TripFigures> figures, List<TripFigures> running) {
        long seatsSold = count(figures, TripFigures::seatsSold);
        long seatsOffered = count(running, TripFigures::seats);
        return new Totals(
                sum(figures, TripFigures::revenue),
                sum(figures, TripFigures::refundsDue),
                sum(figures, TripFigures::refunded),
                sum(figures, TripFigures::awaitingPayment),
                count(figures, TripFigures::bookings),
                seatsSold,
                seatsOffered,
                occupancy(seatsSold, seatsOffered),
                running.size(),
                figures.size() - running.size());
    }

    private static List<RouteRow> byRoute(List<TripFigures> running) {
        return running.stream()
                .collect(Collectors.groupingBy(TripFigures::routeId))
                .values().stream()
                .map(InsightsService::routeRowOf)
                .sorted(Comparator.comparing(RouteRow::revenue).reversed()
                        .thenComparing(RouteRow::origin)
                        .thenComparing(RouteRow::destination))
                .toList();
    }

    private static RouteRow routeRowOf(List<TripFigures> trips) {
        TripFigures route = trips.getFirst();
        long seatsSold = count(trips, TripFigures::seatsSold);
        long seatsOffered = count(trips, TripFigures::seats);
        return new RouteRow(route.routeId(), route.origin(), route.destination(), trips.size(),
                seatsSold, seatsOffered, occupancy(seatsSold, seatsOffered), sum(trips, TripFigures::revenue));
    }

    private static List<DayRow> byDay(List<TripFigures> figures, LocalDate from, LocalDate to) {
        Map<LocalDate, List<TripFigures>> byDate = figures.stream()
                .collect(Collectors.groupingBy(trip -> trip.departureAt().atZoneSameInstant(BANGKOK).toLocalDate()));
        return from.datesUntil(to.plusDays(1))
                .map(date -> {
                    List<TripFigures> trips = byDate.getOrDefault(date, List.of());
                    return new DayRow(date, sum(trips, TripFigures::revenue), count(trips, TripFigures::seatsSold));
                })
                .toList();
    }

    private static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(BANGKOK).toOffsetDateTime();
    }

    private static double occupancy(long seatsSold, long seatsOffered) {
        return seatsOffered == 0 ? 0 : (double) seatsSold / seatsOffered;
    }

    private static BigDecimal sum(List<TripFigures> trips, Function<TripFigures, BigDecimal> figure) {
        return trips.stream().map(figure).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static long count(List<TripFigures> trips, ToLongFunction<TripFigures> figure) {
        return trips.stream().mapToLong(figure).sum();
    }
}
