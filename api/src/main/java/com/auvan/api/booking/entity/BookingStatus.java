package com.auvan.api.booking.entity;

/**
 * The lifecycle of a booking. ADR-009 put a payment review between securing
 * seats and confirming a booking: creation produces {@code PENDING_PAYMENT},
 * a submitted proof moves the booking to {@code PAYMENT_UNDER_REVIEW}, and an
 * administrator's approval becomes the only path to {@code CONFIRMED}. That
 * approval, and the rejection state it can reach instead, arrive with #52.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    PAYMENT_UNDER_REVIEW,
    CONFIRMED,
    CANCELLED
}
