package com.auvan.api.booking.dto;

import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.TripStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One trip's operational picture: what its bookings are doing and who is
 * queued behind them.
 *
 * <p>Read-only, and administrator-only. Nothing here is a control surface —
 * there is deliberately no way to promote, cancel or retry anything from this
 * response, because every one of those already has an owner elsewhere and a
 * second path into them is a second place the rules can drift.
 *
 * @param claimedSeats seats blocked right now, by the same lazy-expiry rule the
 *                     seat map reads with ({@code SeatClaim.blocksSeatAt}), so a
 *                     hold that has simply lapsed does not count as claimed
 *                     here either
 */
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

    /**
     * Every {@link BookingStatus}, including the ones at zero. A status that
     * vanished when it had no bookings would make the operator work out whether
     * they are looking at "none cancelled" or at a column that stopped being
     * reported.
     */
    public record BookingStatusCount(BookingStatus status, long count) { }

    /**
     * One student's place, with whatever promotion they hold.
     *
     * <p>{@code promotionHoldId} and {@code promotionExpiresAt} are written by
     * the promotion sweep (#69) and are null until it runs; this view reads
     * whatever the columns say rather than assuming either state.
     *
     * @param position the place this entry occupies counting only queued
     *                 entries, or {@code null} for one that has ended — derived
     *                 on read exactly as the student's own view derives it, and
     *                 never stored
     */
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
