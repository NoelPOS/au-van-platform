package com.auvan.api.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "booking_events")
public class BookingEvent {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private BookingEventType eventType;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected BookingEvent() { }

    BookingEvent(Booking booking, BookingEventType eventType, String detail, UUID actorUserId, OffsetDateTime now) {
        this.booking = booking;
        this.eventType = eventType;
        this.detail = detail;
        this.actorUserId = actorUserId;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public Booking getBooking() { return booking; }
    public UUID getActorUserId() { return actorUserId; }
    public BookingEventType getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
