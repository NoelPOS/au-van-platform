package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.dto.TripSummaryResponse;
import com.auvan.api.booking.service.SeatAvailabilityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trips")
public class TripCatalogController {
    private final SeatAvailabilityService seatAvailabilityService;

    public TripCatalogController(SeatAvailabilityService seatAvailabilityService) {
        this.seatAvailabilityService = seatAvailabilityService;
    }

    @GetMapping
    public List<TripSummaryResponse> list() {
        return seatAvailabilityService.listBookableTrips();
    }

    @GetMapping("/{tripId}/seats")
    public TripSeatMapResponse seats(@PathVariable UUID tripId, @AuthenticationPrincipal Jwt jwt) {
        return seatAvailabilityService.seatMap(tripId, UUID.fromString(jwt.getSubject()));
    }
}
