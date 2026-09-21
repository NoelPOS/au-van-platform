package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.PaymentProofResponse;
import com.auvan.api.booking.dto.ReviewDecisionRequest;
import com.auvan.api.booking.service.PaymentProofReviewService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

/**
 * The administrator's payment-proof review surface. Every path is under
 * {@code /api/v1/admin/**}, which is what
 * {@code SecurityConfiguration} already restricts to {@code ROLE_ADMIN} — a
 * path segment mistyped here would quietly make these endpoints reachable by
 * any signed-in student, which is why the tests exercise all four.
 */
@RestController
@RequestMapping("/api/v1/admin/payment-proofs")
public class PaymentProofAdminController {
    private final PaymentProofReviewService review;

    public PaymentProofAdminController(PaymentProofReviewService review) {
        this.review = review;
    }

    @GetMapping
    public List<PaymentProofResponse> list() {
        return review.list();
    }

    /**
     * The image itself, brokered by the API rather than handed out as a URL
     * (ADR-009). {@code no-store} because a bank slip does not belong in a
     * shared cache, and {@code inline} so the browser renders it rather than
     * offering to save it.
     */
    @GetMapping("/{proofId}/image")
    public ResponseEntity<byte[]> image(@PathVariable UUID proofId) {
        PaymentProofReviewService.ProofImage image = review.image(proofId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(image.content());
    }

    /** The updated booking comes back, so the client needs no follow-up read. */
    @PostMapping("/{proofId}/approve")
    public BookingResponse approve(@PathVariable UUID proofId,
                                   @RequestBody(required = false) ReviewDecisionRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        return review.approve(UUID.fromString(jwt.getSubject()), proofId, noteOf(request));
    }

    @PostMapping("/{proofId}/reject")
    public BookingResponse reject(@PathVariable UUID proofId,
                                  @RequestBody(required = false) ReviewDecisionRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        return review.reject(UUID.fromString(jwt.getSubject()), proofId, noteOf(request));
    }

    /** Approving needs no body at all; the service decides what a missing note means. */
    private static String noteOf(ReviewDecisionRequest request) {
        return request == null ? null : request.note();
    }
}
