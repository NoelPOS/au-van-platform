package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.DeadLetterResponse;
import com.auvan.api.booking.dto.TripOperationsResponse;
import com.auvan.api.booking.service.OperationsViewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The administrator's operational view: what a trip's bookings are doing, who
 * is queued behind it, and what outbound work was given up on.
 *
 * <p>Every path is under {@code /api/v1/admin/**}, which is the <em>only</em>
 * thing restricting it to {@code ROLE_ADMIN} — {@code SecurityConfiguration}
 * grants the rule by path prefix and there is no method-level annotation
 * anywhere behind it. A segment mistyped here would quietly publish one
 * student's queue position, another's booking counts and every delivery failure
 * in the system to any signed-in student, and nothing would fail. That is why
 * {@code OperationsIntegrationTests} exercises both paths with a student token
 * rather than trusting the prefix, exactly as
 * {@link PaymentProofAdminController} does for its four.
 *
 * <p><strong>Read-only, and it stays that way.</strong> There is no promote,
 * no retry and no cancel here: each already has an owner — the promotion sweep,
 * the outbox dispatcher's claim, the expiry sweep — and a second way in would
 * be a second place those rules can be got wrong.
 */
@RestController
@RequestMapping("/api/v1/admin/operations")
public class OperationsAdminController {
    private final OperationsViewService operations;

    public OperationsAdminController(OperationsViewService operations) {
        this.operations = operations;
    }

    @GetMapping("/trips/{tripId}")
    public TripOperationsResponse trip(@PathVariable UUID tripId) {
        return operations.trip(tripId);
    }

    @GetMapping("/dead-letters")
    public List<DeadLetterResponse> deadLetters() {
        return operations.deadLetters();
    }
}
