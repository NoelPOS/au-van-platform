package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record WaitlistEntryResponse(
        UUID id,
        UUID tripId,
        int seatsWanted,
        WaitlistStatus status,
        Integer position,
        OffsetDateTime joinedAt) {
    public static WaitlistEntryResponse from(WaitlistEntry entry, Integer position) {
        return new WaitlistEntryResponse(entry.getId(), entry.getTrip().getId(), entry.getSeatsWanted(),
                entry.getStatus(), position, entry.getJoinedAt());
    }
}
