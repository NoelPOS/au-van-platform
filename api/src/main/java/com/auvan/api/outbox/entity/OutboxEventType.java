package com.auvan.api.outbox.entity;

/**
 * What happened, from the point of view of the student who has to be told.
 *
 * <p>One value per booking or payment transition that owes outbound work. #62
 * adds the expiry of an unpaid booking and #63 the departure reminders.
 */
public enum OutboxEventType {
    BOOKING_CREATED,
    BOOKING_CANCELLED,
    PAYMENT_PROOF_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED
}
