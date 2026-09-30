package com.auvan.api.booking.entity;

import com.auvan.api.inventory.entity.Trip;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "waitlist_entries", uniqueConstraints =
        @UniqueConstraint(name = "waitlist_entries_trip_user_unique", columnNames = {"trip_id", "user_id"}))
public class WaitlistEntry {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "seats_wanted", nullable = false)
    private int seatsWanted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WaitlistStatus status;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "promotion_hold_id")
    private UUID promotionHoldId;

    @Column(name = "promotion_expires_at")
    private OffsetDateTime promotionExpiresAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected WaitlistEntry() { }

    public WaitlistEntry(Trip trip, UUID userId, int seatsWanted, OffsetDateTime now) {
        this.trip = trip;
        this.userId = userId;
        this.seatsWanted = seatsWanted;
        this.status = WaitlistStatus.WAITING;
        this.joinedAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Trip getTrip() { return trip; }
    public UUID getUserId() { return userId; }
    public int getSeatsWanted() { return seatsWanted; }
    public WaitlistStatus getStatus() { return status; }
    public OffsetDateTime getJoinedAt() { return joinedAt; }
    public UUID getPromotionHoldId() { return promotionHoldId; }
    public OffsetDateTime getPromotionExpiresAt() { return promotionExpiresAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public boolean isQueued() {
        return status == WaitlistStatus.WAITING || status == WaitlistStatus.PROMOTED;
    }

    public boolean isWaiting() {
        return status == WaitlistStatus.WAITING;
    }

    public boolean hasLapsedAt(OffsetDateTime moment) {
        return status == WaitlistStatus.PROMOTED && !promotionExpiresAt.isAfter(moment);
    }

    public void promote(UUID holdId, OffsetDateTime expiresAt, OffsetDateTime now) {
        this.status = WaitlistStatus.PROMOTED;
        this.promotionHoldId = holdId;
        this.promotionExpiresAt = expiresAt;
        this.updatedAt = now;
    }

    public void fulfil(OffsetDateTime now) {
        this.status = WaitlistStatus.FULFILLED;
        this.updatedAt = now;
    }

    public void expire(OffsetDateTime now) {
        this.status = WaitlistStatus.EXPIRED;
        this.updatedAt = now;
    }

    public void rejoin(int seatsWanted, OffsetDateTime now) {
        this.seatsWanted = seatsWanted;
        this.status = WaitlistStatus.WAITING;
        this.joinedAt = now;
        this.promotionHoldId = null;
        this.promotionExpiresAt = null;
        this.updatedAt = now;
    }

    public void withdraw(OffsetDateTime now) {
        this.status = WaitlistStatus.WITHDRAWN;
        this.promotionHoldId = null;
        this.promotionExpiresAt = null;
        this.updatedAt = now;
    }
}
