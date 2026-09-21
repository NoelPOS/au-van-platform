package com.auvan.api.booking.entity;

/** What happened to a booking. The history is append-only, so this never changes. */
public enum BookingEventType {
    CREATED,
    PAYMENT_PROOF_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    CANCELLED
}
