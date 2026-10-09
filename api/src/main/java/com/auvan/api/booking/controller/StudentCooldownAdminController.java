package com.auvan.api.booking.controller;

import com.auvan.api.booking.service.BookingEligibilityService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/students")
public class StudentCooldownAdminController {
    private final BookingEligibilityService eligibility;

    public StudentCooldownAdminController(BookingEligibilityService eligibility) {
        this.eligibility = eligibility;
    }

    @PostMapping("/{userId}/cooldown/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        eligibility.clearCooldown(UUID.fromString(jwt.getSubject()), userId);
    }
}
