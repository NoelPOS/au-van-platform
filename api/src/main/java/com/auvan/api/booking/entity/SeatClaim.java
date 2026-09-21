package com.auvan.api.booking.entity;

import com.auvan.api.inventory.entity.TripSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One claimed seat, either held for a few minutes or attached to a booking.
 * The unique constraint on {@code trip_seat_id} is what prevents overselling,
 * so a claim is released by deleting its row rather than by changing a status.
 * {@code bookingId} is a plain column until the booking entity exists.
 *
 * <p>The constraint is declared here as well as in {@code V3}, under the same
 * name, so the two descriptions of the table cannot drift apart.
 */
@Entity
@Table(name = "seat_claims", uniqueConstraints =
        @UniqueConstraint(name = "seat_claims_trip_seat_unique", columnNames = "trip_seat_id"))
public class SeatClaim {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_seat_id", nullable = false)
    private TripSeat tripSeat;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "hold_id", nullable = false)
    private UUID holdId;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected SeatClaim() { }

    public SeatClaim(TripSeat tripSeat, UUID userId, UUID holdId, OffsetDateTime expiresAt) {
        this.tripSeat = tripSeat;
        this.userId = userId;
        this.holdId = holdId;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public UUID getId() { return id; }
    public TripSeat getTripSeat() { return tripSeat; }
    public UUID getUserId() { return userId; }
    public UUID getHoldId() { return holdId; }
    public UUID getBookingId() { return bookingId; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }

    public boolean isBooked() {
        return bookingId != null;
    }

    /** A booked seat is claimed forever; a hold only until it expires. */
    public boolean blocksSeatAt(OffsetDateTime moment) {
        return isBooked() || expiresAt.isAfter(moment);
    }

    /**
     * True only for an unbooked claim this user owns, expired or not.
     *
     * <p>Not for the confirmation path: it folds "booked" and "not yours" into
     * one answer, which would tell a student confirming their own hold a second
     * time that the hold does not exist. Confirmation asks the three questions
     * separately.
     */
    public boolean isHeldBy(UUID candidate) {
        return !isBooked() && userId.equals(candidate);
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public boolean hasExpiredAt(OffsetDateTime moment) {
        return !expiresAt.isAfter(moment);
    }

    /** Turns a hold into a booked seat in place, so the seat is never unprotected. */
    public void attachTo(UUID bookingId) {
        this.bookingId = bookingId;
    }
}
