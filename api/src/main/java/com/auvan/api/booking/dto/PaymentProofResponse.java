package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of the administrator's review queue: enough booking context to make
 * the decision, and nothing that could reach the image without going back
 * through the API.
 *
 * <p><strong>There is no {@code objectKey}, bucket, or URL here, deliberately.</strong>
 * A proof is addressed by its own id and the image endpoint resolves the key
 * server-side; building this record straight off the entity would serialise
 * the key and give away the very thing ADR-009 keeps private.
 */
public record PaymentProofResponse(
        UUID id,
        UUID bookingId,
        String bookingReference,
        String passengerName,
        String passengerPhone,
        BigDecimal totalFare,
        BookingResponse.BookedTrip trip,
        UUID submittedByUserId,
        String contentType,
        long sizeBytes,
        PaymentProofStatus status,
        OffsetDateTime submittedAt) {

    public static PaymentProofResponse from(PaymentProof proof) {
        Booking booking = proof.getBooking();
        return new PaymentProofResponse(
                proof.getId(),
                booking.getId(),
                booking.getReference(),
                booking.getPassengerName(),
                booking.getPassengerPhone(),
                booking.getTotalFare(),
                BookingResponse.BookedTrip.from(booking.getTrip()),
                proof.getSubmittedByUserId(),
                proof.getContentType(),
                proof.getSizeBytes(),
                proof.getStatus(),
                proof.getCreatedAt());
    }
}
