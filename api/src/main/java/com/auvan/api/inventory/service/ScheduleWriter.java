package com.auvan.api.inventory.service;

import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.dto.ScheduleApplyResponse;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.repository.TripRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ScheduleWriter {
    static final String SCHEDULE_CHANGED = "The schedule changed since your preview. Check the new preview and confirm again.";

    private final SchedulePlanner planner;
    private final TripRepository trips;
    private final IdempotencyService idempotency;

    public ScheduleWriter(SchedulePlanner planner, TripRepository trips, IdempotencyService idempotency) {
        this.planner = planner;
        this.trips = trips;
        this.idempotency = idempotency;
    }

    @Transactional
    public IdempotencyService.StoredResponse apply(UUID userId, String endpoint, String key, String requestHash,
                                                   SchedulePlanRequest request) {
        OffsetDateTime now = OffsetDateTime.now();
        SchedulePlanner.Plan plan = planner.plan(request, now);
        if (!plan.hash().equals(request.planHash())) {
            throw Problems.conflict("schedule_changed", SCHEDULE_CHANGED);
        }
        List<Trip> created = plan.creatable().stream()
                .map(slot -> new Trip(slot.route(), slot.vehicle(), slot.departureAt()))
                .toList();
        try {
            trips.saveAll(created);
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            trips.flush();
        } catch (DataIntegrityViolationException | PessimisticLockingFailureException collision) {
            throw Problems.conflict("schedule_changed", SCHEDULE_CHANGED, collision);
        }
        ScheduleApplyResponse response = new ScheduleApplyResponse(created.size(), plan.slots().size() - created.size());
        return idempotency.record(userId, endpoint, key, requestHash, HttpStatus.CREATED.value(), response, now);
    }
}
