package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.Booking;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BookingEligibilityResponse(
        boolean canBook,
        String reason,
        String message,
        OffsetDateTime retryAt,
        UUID unpaidBookingId,
        String unpaidBookingReference) {
    public static BookingEligibilityResponse eligible() {
        return new BookingEligibilityResponse(true, null, null, null, null, null);
    }

    public static BookingEligibilityResponse unpaid(Booking booking, String message) {
        return new BookingEligibilityResponse(false, "unpaid_booking_exists", message, null, booking.getId(),
                booking.getReference());
    }
}
