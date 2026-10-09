package com.auvan.api.booking.entity;

public enum BookingEventType {
    CREATED,
    PAYMENT_PROOF_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    CANCELLED,
    EXPIRED,
    TRIP_CANCELLED,
    TRIP_RESCHEDULED,
    REFUNDED
}
