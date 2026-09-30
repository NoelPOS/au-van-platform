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

    public boolean blocksSeatAt(OffsetDateTime moment) {
        return isBooked() || expiresAt.isAfter(moment);
    }

    public boolean isHeldBy(UUID candidate) {
        return !isBooked() && userId.equals(candidate);
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public boolean hasExpiredAt(OffsetDateTime moment) {
        return !expiresAt.isAfter(moment);
    }

    public void attachTo(UUID bookingId) {
        this.bookingId = bookingId;
    }
}
