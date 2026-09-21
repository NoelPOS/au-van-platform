package com.auvan.api.booking.entity;

/**
 * The lifecycle of a booking. ADR-009 put a payment review between securing
 * seats and confirming a booking: creation produces {@code PENDING_PAYMENT},
 * a submitted proof moves the booking to {@code PAYMENT_UNDER_REVIEW}, and an
 * administrator's approval is the only path to {@code CONFIRMED}.
 *
 * <p>{@code PAYMENT_REJECTED} is not a dead end. The booking keeps its seats
 * and the student may submit another proof against it, which puts it back
 * under review.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    PAYMENT_UNDER_REVIEW,
    PAYMENT_REJECTED,
    CONFIRMED,
    CANCELLED
}
