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

/**
 * A confirmed seat purchase and the history of how it got there.
 *
 * <p>The booked seats live in {@code booking_seats} rather than being read back
 * from {@code seat_claims}, because cancelling a booking deletes its claims to
 * free the seats and the booking still has to say what was booked.
 *
 * <p>Mirrored from {@code V3} and {@code V4} under the same names, so the
 * mapping and the migrations cannot describe this table differently without it
 * showing in review.
 */
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
                   BigDecimal totalFare, OffsetDateTime now) {
        this.trip = trip;
        this.userId = userId;
        this.reference = reference;
        this.passengerName = passengerName;
        this.passengerPhone = passengerPhone;
        this.totalFare = totalFare;
        // Seats are secured, money is not. ADR-009 made the payment review the
        // only path to CONFIRMED, so creation can no longer produce one.
        this.status = BookingStatus.PENDING_PAYMENT;
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
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public List<BookingSeat> getSeats() { return List.copyOf(seats); }
    public List<BookingEvent> getEvents() { return List.copyOf(events); }

    public void addSeat(TripSeat seat) {
        seats.add(new BookingSeat(this, seat));
    }

    /** The history only ever grows; nothing removes or rewrites an event. */
    public void recordEvent(BookingEventType type, String detail, UUID actorUserId, OffsetDateTime now) {
        events.add(new BookingEvent(this, type, detail, actorUserId, now));
    }

    public boolean isCancelled() {
        return status == BookingStatus.CANCELLED;
    }

    /**
     * Only a booking still waiting for payment accepts a proof, and a rejected
     * one still is: ADR-009 made rejection a state the student resubmits from
     * rather than a dead end, and this method is the whole of that gate. A
     * booking already in front of an administrator is not waiting for anything.
     */
    public boolean isAwaitingPaymentProof() {
        return status == BookingStatus.PENDING_PAYMENT || status == BookingStatus.PAYMENT_REJECTED;
    }

    /** Only a booking an administrator is actually looking at can be decided. */
    public boolean isUnderPaymentReview() {
        return status == BookingStatus.PAYMENT_UNDER_REVIEW;
    }

    public void markPaymentUnderReview(OffsetDateTime now) {
        this.status = BookingStatus.PAYMENT_UNDER_REVIEW;
        this.updatedAt = now;
    }

    public void confirm(OffsetDateTime now) {
        this.status = BookingStatus.CONFIRMED;
        this.updatedAt = now;
    }

    /** The seats stay claimed: the student may submit another proof (ADR-009). */
    public void markPaymentRejected(OffsetDateTime now) {
        this.status = BookingStatus.PAYMENT_REJECTED;
        this.updatedAt = now;
    }

    public void cancel(OffsetDateTime now) {
        this.status = BookingStatus.CANCELLED;
        this.updatedAt = now;
    }
}
