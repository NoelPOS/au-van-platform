package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Never add an object key, bucket or URL: the image is reached only through the API.
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
        OffsetDateTime submittedAt,
        List<SameSlipBooking> sameSlipBookings) {

    public record SameSlipBooking(UUID bookingId, String bookingReference) { }

    public static PaymentProofResponse from(PaymentProof proof, List<SameSlipBooking> sameSlipBookings) {
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
                proof.getCreatedAt(),
                sameSlipBookings);
    }
}
