package com.auvan.api.outbox.entity;

/**
 * What happened, from the point of view of the student who has to be told.
 *
 * <p>One value per booking or payment transition that owes outbound work, plus
 * the two departure reminders, which owe it at a time of their own.
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
    PAYMENT_REJECTED,
    /**
     * The two departure reminders, ported from the legacy application's
     * {@code departure_24h} and {@code departure_1h}
     * ({@code src/services/reminder.service.ts:40-43}). Unlike every value
     * above, these describe nothing that has happened: they are scheduled when
     * a booking is approved and become due at {@code departureAt} minus the
     * offset their name gives.
     */
    DEPARTURE_REMINDER_24H,
    DEPARTURE_REMINDER_1H
}
