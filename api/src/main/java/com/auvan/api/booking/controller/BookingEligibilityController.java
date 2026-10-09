package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.BookingEligibilityResponse;
import com.auvan.api.booking.service.BookingEligibilityService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
public class BookingEligibilityController {
    private final BookingEligibilityService eligibility;

    public BookingEligibilityController(BookingEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @GetMapping("/api/v1/me/booking-eligibility")
    public BookingEligibilityResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return eligibility.eligibilityOf(UUID.fromString(jwt.getSubject()), OffsetDateTime.now());
    }
}
