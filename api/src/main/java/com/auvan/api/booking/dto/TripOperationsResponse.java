package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.TripStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record TripOperationsResponse(
        UUID tripId,
        String origin,
        String destination,
        OffsetDateTime departureAt,
        TripStatus tripStatus,
        int totalSeats,
        int claimedSeats,
        List<BookingStatusCount> bookingsByStatus,
        List<WaitlistPlace> waitlist) {

    public record BookingStatusCount(BookingStatus status, long count) { }

    public record WaitlistPlace(
            UUID entryId,
            UUID userId,
            String displayName,
            int seatsWanted,
            WaitlistStatus status,
            Integer position,
            OffsetDateTime joinedAt,
            UUID promotionHoldId,
            OffsetDateTime promotionExpiresAt) { }
}
