package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.dto.WaitlistEntryResponse;
import com.auvan.api.booking.service.WaitlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/waitlist")
public class WaitlistController {
    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WaitlistEntryResponse join(@Valid @RequestBody JoinWaitlistRequest request,
                                      @AuthenticationPrincipal Jwt jwt) {
        return waitlistService.join(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping
    public List<WaitlistEntryResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return waitlistService.mine(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/{entryId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable UUID entryId, @AuthenticationPrincipal Jwt jwt) {
        waitlistService.leave(UUID.fromString(jwt.getSubject()), entryId);
    }
}
