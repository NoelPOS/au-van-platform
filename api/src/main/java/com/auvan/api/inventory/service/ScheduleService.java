package com.auvan.api.inventory.service;

import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.dto.ClearDayResponse;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.dto.SchedulePreviewResponse;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleService {
    static final String APPLY_ENDPOINT = "POST /api/v1/admin/schedule/apply";

    private final SchedulePlanner planner;
    private final ScheduleWriter writer;
    private final IdempotencyService idempotency;
    private final TripRepository trips;

    public ScheduleService(SchedulePlanner planner, ScheduleWriter writer, IdempotencyService idempotency,
                           TripRepository trips) {
        this.planner = planner;
        this.writer = writer;
        this.idempotency = idempotency;
        this.trips = trips;
    }

    @Transactional(readOnly = true)
    public SchedulePreviewResponse preview(SchedulePlanRequest request) {
        SchedulePlanner.Plan plan = planner.plan(request, OffsetDateTime.now());
        return new SchedulePreviewResponse(plan.hash(), plan.departures());
    }

    // Not @Transactional: a replay's write fails, so the stored answer needs its own transaction.
    public IdempotencyService.StoredResponse apply(UUID userId, String key, SchedulePlanRequest request) {
        if (request.planHash() == null || request.planHash().isBlank()) {
            throw Problems.badRequest("preview_required", "Preview the schedule before applying it.");
        }
        String requestHash = idempotency.fingerprint(request);
        try {
            return writer.apply(userId, APPLY_ENDPOINT, key, requestHash, request);
        } catch (RuntimeException failure) {
            return idempotency.find(userId, APPLY_ENDPOINT, key, requestHash).orElseThrow(() -> failure);
        }
    }

    @Transactional
    public ClearDayResponse clearDay(LocalDate date) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime start = date.atStartOfDay(SchedulePlanner.BANGKOK).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(SchedulePlanner.BANGKOK).toOffsetDateTime();
        if (!end.isAfter(now)) {
            throw Problems.badRequest("day_passed", "That day has already passed.");
        }
        OffsetDateTime after = start.isAfter(now) ? start : now;
        long ahead = trips.countByDepartureAtGreaterThanAndDepartureAtLessThan(after, end);
        List<Trip> unclaimed = trips.findUnclaimedBetween(after, end);
        try {
            trips.deleteAll(unclaimed);
            trips.flush();
        } catch (DataIntegrityViolationException claimedMeanwhile) {
            throw Problems.conflict("day_changed",
                    "A student started booking on this day while it was being cleared. Nothing was removed; try again.",
                    claimedMeanwhile);
        }
        return new ClearDayResponse(unclaimed.size(), (int) ahead - unclaimed.size());
    }
}
