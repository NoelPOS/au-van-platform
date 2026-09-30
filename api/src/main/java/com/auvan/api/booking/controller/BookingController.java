package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 255;

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    public ResponseEntity<String> create(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                         @Valid @RequestBody CreateBookingRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        IdempotencyService.StoredResponse response =
                bookings.create(UUID.fromString(jwt.getSubject()), validated(key), request);
        return ResponseEntity.status(response.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(response.body());
    }

    @GetMapping
    public List<BookingResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return bookings.list(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/{bookingId}")
    public BookingResponse get(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt jwt) {
        return bookings.get(UUID.fromString(jwt.getSubject()), bookingId);
    }

    @PostMapping("/{bookingId}/cancel")
    public BookingResponse cancel(@PathVariable UUID bookingId, @AuthenticationPrincipal Jwt jwt) {
        return bookings.cancel(UUID.fromString(jwt.getSubject()), bookingId);
    }

    private static String validated(String key) {
        if (key == null || key.isBlank() || key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw Problems.badRequest("idempotency_key_required",
                    "An Idempotency-Key header of 1 to " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters is required.");
        }
        return key;
    }
}
