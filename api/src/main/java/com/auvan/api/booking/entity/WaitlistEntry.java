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

/**
 * One student's place in one trip's queue.
 *
 * <p>There is no position column: a place is {@code joined_at} and nothing
 * else, derived on every read, for the reason {@code seat_claims} has no status
 * column (ADR-011). Nothing has to be kept in sync and nothing can drift.
 *
 * <p>The unique constraint is declared here as well as in {@code V9}, under the
 * same name, so the two descriptions of the table cannot drift apart.
 */
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

    /** Written by the promotion sweep (#69); null everywhere else. */
    @Column(name = "promotion_hold_id")
    private UUID promotionHoldId;

    /** Written by the promotion sweep (#69); null everywhere else. */
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

    /**
     * Whether this entry still occupies a place in the queue. Only a queued
     * entry counts towards anyone's position, and only a queued entry has one.
     */
    public boolean isQueued() {
        return status == WaitlistStatus.WAITING || status == WaitlistStatus.PROMOTED;
    }

    /**
     * Re-joining after the entry has ended. The plain unique constraint means
     * there is one row per student per trip forever, so a student who left and
     * came back reuses this row with a fresh {@code joinedAt} — and therefore
     * goes to the back of the queue. ADR-011 records that as a deliberate
     * consequence of refusing a partial index rather than an accident.
     *
     * <p>An entry that is still queued is never re-joined: a second join is the
     * same join, and resetting {@code joinedAt} would cost a student their place
     * for nothing more than tapping the button twice.
     */
    public void rejoin(int seatsWanted, OffsetDateTime now) {
        this.seatsWanted = seatsWanted;
        this.status = WaitlistStatus.WAITING;
        this.joinedAt = now;
        this.promotionHoldId = null;
        this.promotionExpiresAt = null;
        this.updatedAt = now;
    }

    /** The student left. Terminal, and the students behind them move up. */
    public void withdraw(OffsetDateTime now) {
        this.status = WaitlistStatus.WITHDRAWN;
        this.promotionHoldId = null;
        this.promotionExpiresAt = null;
        this.updatedAt = now;
    }
}
