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
