package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.RefundRequest;
import com.auvan.api.booking.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/refunds")
public class RefundAdminController {
    private final RefundService refunds;

    public RefundAdminController(RefundService refunds) {
        this.refunds = refunds;
    }

    @GetMapping
    public List<BookingResponse> due() {
        return refunds.due();
    }

    @PostMapping("/{bookingId}/mark-refunded")
    public BookingResponse markRefunded(@PathVariable UUID bookingId,
                                        @Valid @RequestBody(required = false) RefundRequest request,
                                        @AuthenticationPrincipal Jwt jwt) {
        return refunds.markRefunded(UUID.fromString(jwt.getSubject()), bookingId,
                request == null ? null : request.note());
    }
}
