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

    /**
     * When this booking stops holding its seats if it is still unpaid, or
     * {@code null} for one that never expires. Only the transitions below write
     * it, which is the whole of ADR-010's rule: nothing else may touch it, and
     * in particular it is deliberately not derived from {@link #updatedAt},
     * where any future write would silently reset a student's payment clock.
     */
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

    /**
     * @param paymentDeadlineAt when these seats are released if the booking is
     *                          still unpaid. A booking created close to
     *                          departure legitimately gets one that has already
     *                          passed, and is expired by the next sweep.
     */
    public Booking(Trip trip, UUID userId, String reference, String passengerName, String passengerPhone,
                   BigDecimal totalFare, OffsetDateTime paymentDeadlineAt, OffsetDateTime now) {
        this.trip = trip;
        this.userId = userId;
        this.reference = reference;
        this.passengerName = passengerName;
        this.passengerPhone = passengerPhone;
        this.totalFare = totalFare;
        // Seats are secured, money is not. ADR-009 made the payment review the
        // only path to CONFIRMED, so creation can no longer produce one.
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

    /**
     * Whether the sweep may expire this booking, decided from the two facts
     * {@code BookingRepository.findExpirable} selects on. It exists so that the
     * check made <em>behind the lock</em> is the same rule the candidate query
     * applied, rather than a second wording of it that can drift.
     *
     * <p>A null deadline is never expirable: that is what {@code NULL} means on
     * the column, and it is the right answer for every terminal row.
     */
    public boolean isExpirable(OffsetDateTime now) {
        return (status == BookingStatus.PENDING_PAYMENT
                || status == BookingStatus.PAYMENT_UNDER_REVIEW
                || status == BookingStatus.PAYMENT_REJECTED)
                && paymentDeadlineAt != null && !paymentDeadlineAt.isAfter(now);
    }

    /**
     * @param paymentDeadlineAt bounded by departure rather than by a fresh
     *                          timer, so a slow reviewer never costs a student
     *                          their booking while the seat is still worth
     *                          recycling (ADR-010)
     */
    public void markPaymentUnderReview(OffsetDateTime paymentDeadlineAt, OffsetDateTime now) {
        this.status = BookingStatus.PAYMENT_UNDER_REVIEW;
        this.paymentDeadlineAt = paymentDeadlineAt;
        this.updatedAt = now;
    }

    /** Terminal, so the deadline goes: a confirmed booking never expires. */
    public void confirm(OffsetDateTime now) {
        this.status = BookingStatus.CONFIRMED;
        this.paymentDeadlineAt = null;
        this.updatedAt = now;
    }

    /**
     * The seats stay claimed: the student may submit another proof (ADR-009).
     *
     * @param paymentDeadlineAt a fresh window, because a student who is told to
     *                          send a better slip needs time to send one
     */
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

    /**
     * The same terminal state as {@link #cancel}, under the name of what
     * actually happened. ADR-010 rejected a separate {@code EXPIRED} status —
     * the seats are released and the booking is over either way, and a new
     * status would be a public contract change for no operational difference —
     * but the call site still has to read as an expiry rather than as a
     * cancellation nobody asked for. The {@code why} lives in the history, as a
     * {@link BookingEventType#EXPIRED} event with no actor.
     */
    public void expire(OffsetDateTime now) {
        cancel(now);
    }
}
