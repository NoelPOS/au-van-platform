package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.service.PaymentProofService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
public class PaymentProofController {
    private final PaymentProofService proofs;

    public PaymentProofController(PaymentProofService proofs) {
        this.proofs = proofs;
    }

    @PostMapping(path = "/api/v1/bookings/{bookingId}/payment-proof",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BookingResponse submit(@PathVariable UUID bookingId,
                                  @RequestPart("file") MultipartFile file,
                                  @AuthenticationPrincipal Jwt jwt) {
        return proofs.submit(UUID.fromString(jwt.getSubject()), bookingId, file);
    }
}
