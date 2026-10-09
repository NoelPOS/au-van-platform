package com.auvan.api.inventory.controller;

import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.dto.ClearDayRequest;
import com.auvan.api.inventory.dto.ClearDayResponse;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.dto.SchedulePreviewResponse;
import com.auvan.api.inventory.service.ScheduleService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/schedule")
public class ScheduleAdminController {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final ScheduleService schedule;

    public ScheduleAdminController(ScheduleService schedule) {
        this.schedule = schedule;
    }

    @PostMapping("/preview")
    public SchedulePreviewResponse preview(@Valid @RequestBody SchedulePlanRequest request) {
        return schedule.preview(request);
    }

    @PostMapping("/apply")
    public ResponseEntity<String> apply(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                        @Valid @RequestBody SchedulePlanRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        IdempotencyService.StoredResponse response =
                schedule.apply(UUID.fromString(jwt.getSubject()), validated(key), request);
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @PostMapping("/clear-day")
    public ClearDayResponse clearDay(@Valid @RequestBody ClearDayRequest request) {
        return schedule.clearDay(request.date());
    }

    private static String validated(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw Problems.badRequest("idempotency_key_required",
                    "An Idempotency-Key header of 1 to " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters is required.");
        }
        return key;
    }
}
