package com.auvan.api.outbox.entity;

/**
 * What happened, from the point of view of the student who has to be told.
 *
 * <p>One value per booking or payment transition that owes outbound work. #63
 * adds the departure reminders.
 */
public enum OutboxEventType {
    BOOKING_CREATED,
    BOOKING_CANCELLED,
    /**
     * Distinct from {@code BOOKING_CANCELLED} although both leave the booking
     * {@code CANCELLED}: this one is the first thing in the system that takes
     * something away without the student acting, so the message it produces has
     * to explain itself rather than confirm something they just did.
     */
    BOOKING_EXPIRED,
    PAYMENT_PROOF_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED
}
