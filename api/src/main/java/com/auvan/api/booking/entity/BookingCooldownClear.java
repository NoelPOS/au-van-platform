package com.auvan.api.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_cooldown_clears")
public class BookingCooldownClear {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "cleared_by_user_id", nullable = false)
    private UUID clearedByUserId;

    @Column(name = "cleared_at", nullable = false)
    private OffsetDateTime clearedAt;

    protected BookingCooldownClear() { }

    public BookingCooldownClear(UUID userId, UUID clearedByUserId, OffsetDateTime clearedAt) {
        this.userId = userId;
        this.clearedByUserId = clearedByUserId;
        this.clearedAt = clearedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getClearedByUserId() { return clearedByUserId; }
    public OffsetDateTime getClearedAt() { return clearedAt; }
}
