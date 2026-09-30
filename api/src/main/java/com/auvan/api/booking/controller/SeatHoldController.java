package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatHoldResponse;
import com.auvan.api.booking.service.SeatHoldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/seat-holds")
public class SeatHoldController {
    private final SeatHoldService seatHoldService;

    public SeatHoldController(SeatHoldService seatHoldService) {
        this.seatHoldService = seatHoldService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatHoldResponse create(@Valid @RequestBody CreateSeatHoldRequest request, @AuthenticationPrincipal Jwt jwt) {
        return seatHoldService.hold(UUID.fromString(jwt.getSubject()), request);
    }

    @PostMapping("/{holdId}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID holdId, @AuthenticationPrincipal Jwt jwt) {
        seatHoldService.release(UUID.fromString(jwt.getSubject()), holdId);
    }
}
