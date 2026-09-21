package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.SeatClaim;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SeatHoldResponse(
        UUID holdId,
        UUID tripId,
        OffsetDateTime expiresAt,
        List<HeldSeat> seats) {
    public static SeatHoldResponse from(UUID holdId, UUID tripId, OffsetDateTime expiresAt, List<SeatClaim> claims) {
        return new SeatHoldResponse(holdId, tripId, expiresAt, claims.stream().map(HeldSeat::from).toList());
    }

    public record HeldSeat(UUID seatId, String label) {
        static HeldSeat from(SeatClaim claim) {
            return new HeldSeat(claim.getTripSeat().getId(), claim.getTripSeat().getLabel());
        }
    }
}
