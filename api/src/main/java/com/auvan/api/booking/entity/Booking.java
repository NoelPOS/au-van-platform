package com.auvan.api.booking.entity;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bookings", uniqueConstraints =
        @UniqueConstraint(name = "bookings_reference_unique", columnNames = "reference"))
public class Booking {
    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String reference;

    @Column(name = "passenger_name", nullable = false)
    private String passengerName;

    @Column(name = "passenger_phone", nullable = false, length = 50)
    private String passengerPhone;

    @Column(name = "total_fare", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalFare;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Column(name = "payment_deadline_at")
    private OffsetDateTime paymentDeadlineAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BookingSeat> seats = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<BookingEvent> events = new ArrayList<>();

    protected Booking() { }

    public Booking(Trip trip, UUID userId, String reference, String passengerName, String passengerPhone,
                   BigDecimal totalFare, OffsetDateTime paymentDeadlineAt, OffsetDateTime now) {
        this.trip = trip;
        this.userId = userId;
        this.reference = reference;
        this.passengerName = passengerName;
        this.passengerPhone = passengerPhone;
        this.totalFare = totalFare;
        this.status = BookingStatus.PENDING_PAYMENT;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Trip getTrip() { return trip; }
    public UUID getUserId() { return userId; }
    public String getReference() { return reference; }
    public String getPassengerName() { return passengerName; }
    public String getPassengerPhone() { return passengerPhone; }
    public BigDecimal getTotalFare() { return totalFare; }
    public BookingStatus getStatus() { return status; }
    public OffsetDateTime getPaymentDeadlineAt() { return paymentDeadlineAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<BookingSeat> getSeats() { return List.copyOf(seats); }
    public List<BookingEvent> getEvents() { return List.copyOf(events); }

    public void addSeat(TripSeat seat) {
        seats.add(new BookingSeat(this, seat));
    }

    public void recordEvent(BookingEventType type, String detail, UUID actorUserId, OffsetDateTime now) {
        events.add(new BookingEvent(this, type, detail, actorUserId, now));
    }

    public boolean isCancelled() {
        return status == BookingStatus.CANCELLED;
    }

    public boolean isAwaitingPaymentProof() {
        return status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.PAYMENT_REJECTED;
    }

    public boolean isUnderPaymentReview() {
        return status == BookingStatus.PAYMENT_UNDER_REVIEW;
    }

    public boolean isExpirable(OffsetDateTime now) {
        return (status == BookingStatus.PENDING_PAYMENT
                || status == BookingStatus.PAYMENT_UNDER_REVIEW
                || status == BookingStatus.PAYMENT_REJECTED)
                && paymentDeadlineAt != null && !paymentDeadlineAt.isAfter(now);
    }

    public void markPaymentUnderReview(OffsetDateTime paymentDeadlineAt, OffsetDateTime now) {
        this.status = BookingStatus.PAYMENT_UNDER_REVIEW;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.updatedAt = now;
    }

    public void confirm(OffsetDateTime now) {
        this.status = BookingStatus.CONFIRMED;
        this.paymentDeadlineAt = null;
        this.updatedAt = now;
    }

    public void markPaymentRejected(OffsetDateTime paymentDeadlineAt, OffsetDateTime now) {
        this.status = BookingStatus.PAYMENT_REJECTED;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        this.status = BookingStatus.CANCELLED;
        this.paymentDeadlineAt = null;
        this.updatedAt = now;
    }

    public void expire(OffsetDateTime now) {
        cancel(now);
    }
}
