package com.auvan.api.inventory.service;

import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.dto.DepartureLine;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.dto.SchedulePreviewResponse.Outcome;
import com.auvan.api.inventory.dto.SchedulePreviewResponse.PlannedDeparture;
import com.auvan.api.inventory.entity.RouteStatus;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SchedulePlanner {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm").withZone(BANGKOK);

    private final TripRepository trips;
    private final VanRouteRepository routes;
    private final VehicleRepository vehicles;
    private final IdempotencyService idempotency;

    public SchedulePlanner(TripRepository trips, VanRouteRepository routes, VehicleRepository vehicles,
                           IdempotencyService idempotency) {
        this.trips = trips;
        this.routes = routes;
        this.vehicles = vehicles;
        this.idempotency = idempotency;
    }

    public record Slot(PlannedDeparture departure, VanRoute route, Vehicle vehicle, OffsetDateTime departureAt) {
        boolean creates() { return departure.outcome() == Outcome.CREATE; }
    }

    public record Plan(List<Slot> slots, String hash) {
        public List<PlannedDeparture> departures() { return slots.stream().map(Slot::departure).toList(); }
        public List<Slot> creatable() { return slots.stream().filter(Slot::creates).toList(); }
    }

    private record Verdict(Outcome outcome, String reason) { }

    private record Run(OffsetDateTime start, OffsetDateTime end, boolean cancelled, String label) {
        boolean clashesWith(OffsetDateTime otherStart, OffsetDateTime otherEnd) {
            return start.isEqual(otherStart) || (!cancelled && start.isBefore(otherEnd) && otherStart.isBefore(end));
        }
    }

    public Plan plan(SchedulePlanRequest request, OffsetDateTime now) {
        Map<UUID, VanRoute> routeById = load(request, DepartureLine::routeId, routes::findAllById,
                VanRoute::getId, "Route not found.");
        Map<UUID, Vehicle> vehicleById = load(request, DepartureLine::vehicleId, vehicles::findAllById,
                Vehicle::getId, "Van not found.");
        List<LocalDate> dates = request.dates().stream().distinct().sorted().toList();
        List<DepartureLine> lines = request.departures().stream()
                .sorted(Comparator.comparing(DepartureLine::time)
                        .thenComparing(line -> vehicleById.get(line.vehicleId()).getCode()))
                .toList();
        Map<UUID, List<Run>> busy = runsOf(vehicleById.keySet(), dates);

        List<Slot> slots = new ArrayList<>();
        for (LocalDate date : dates) {
            for (DepartureLine line : lines) {
                VanRoute route = routeById.get(line.routeId());
                Vehicle vehicle = vehicleById.get(line.vehicleId());
                OffsetDateTime departureAt = date.atTime(line.time()).atZone(BANGKOK).toOffsetDateTime();
                OffsetDateTime arrivesAt = departureAt.plusMinutes(route.getDurationMinutes());
                List<Run> vanRuns = busy.computeIfAbsent(vehicle.getId(), id -> new ArrayList<>());
                Verdict verdict = judge(date, departureAt, arrivesAt, route, vehicle, vanRuns, now);
                if (verdict.outcome() == Outcome.CREATE) {
                    vanRuns.add(new Run(departureAt, arrivesAt, false, labelOf(route, departureAt)));
                }
                slots.add(new Slot(new PlannedDeparture(date, line.time(), route.getId(), vehicle.getId(),
                        verdict.outcome(), verdict.reason()), route, vehicle, departureAt));
            }
        }
        return new Plan(slots, idempotency.fingerprint(
                slots.stream().filter(Slot::creates).map(Slot::departure).toList()));
    }

    private Verdict judge(LocalDate date, OffsetDateTime departureAt, OffsetDateTime arrivesAt, VanRoute route,
                          Vehicle vehicle, List<Run> vanRuns, OffsetDateTime now) {
        if (!departureAt.isAfter(now)) {
            return new Verdict(Outcome.PAST, date.isBefore(now.atZoneSameInstant(BANGKOK).toLocalDate())
                    ? "This day has already passed."
                    : CLOCK.format(departureAt) + " has already passed.");
        }
        if (route.getStatus() != RouteStatus.ACTIVE) {
            return new Verdict(Outcome.UNAVAILABLE, route.getOrigin() + " → " + route.getDestination() + " is inactive.");
        }
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            return new Verdict(Outcome.UNAVAILABLE, vehicle.getCode() + " is out of service.");
        }
        return vanRuns.stream()
                .filter(run -> run.clashesWith(departureAt, arrivesAt))
                .findFirst()
                .map(run -> new Verdict(Outcome.CLASH, vehicle.getCode() + " is already on the " + run.label() + "."))
                .orElse(new Verdict(Outcome.CREATE, null));
    }

    private Map<UUID, List<Run>> runsOf(Set<UUID> vehicleIds, List<LocalDate> dates) {
        OffsetDateTime from = dates.getFirst().minusDays(1).atStartOfDay(BANGKOK).toOffsetDateTime();
        OffsetDateTime until = dates.getLast().plusDays(2).atStartOfDay(BANGKOK).toOffsetDateTime();
        Map<UUID, List<Run>> runs = new HashMap<>();
        for (Trip trip : trips.findForVehiclesBetween(vehicleIds, from, until)) {
            runs.computeIfAbsent(trip.getVehicle().getId(), id -> new ArrayList<>()).add(new Run(
                    trip.getDepartureAt(), trip.getDepartureAt().plusMinutes(trip.getDurationMinutes()),
                    trip.getStatus() == TripStatus.CANCELLED, labelOf(trip.getRoute(), trip.getDepartureAt())));
        }
        return runs;
    }

    private static String labelOf(VanRoute route, OffsetDateTime departureAt) {
        return CLOCK.format(departureAt) + " " + route.getOrigin() + " → " + route.getDestination();
    }

    private static <T> Map<UUID, T> load(SchedulePlanRequest request, Function<DepartureLine, UUID> idOf,
                                         Function<List<UUID>, List<T>> finder, Function<T, UUID> keyOf,
                                         String missing) {
        List<UUID> ids = request.departures().stream().map(idOf).distinct().toList();
        Map<UUID, T> found = finder.apply(ids).stream().collect(Collectors.toMap(keyOf, Function.identity()));
        if (found.size() != ids.size()) {
            throw Problems.badRequest("schedule_reference_missing", missing);
        }
        return found;
    }
}
