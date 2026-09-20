package com.auvan.api.inventory.controller;

import com.auvan.api.inventory.dto.CreateTripRequest;
import com.auvan.api.inventory.dto.TripResponse;
import com.auvan.api.inventory.dto.UpdateTripRequest;
import com.auvan.api.inventory.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/trips")
public class TripAdminController {
    private final TripService tripService;

    public TripAdminController(TripService tripService) {
        this.tripService = tripService;
    }

    @GetMapping
    public List<TripResponse> list() {
        return tripService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@Valid @RequestBody CreateTripRequest request) {
        return tripService.create(request);
    }

    @PutMapping("/{id}")
    public TripResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateTripRequest request) {
        return tripService.update(id, request);
    }
}
