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
    DEPARTURE_REMINDER_1H,
    /**
     * A seat came free on a trip the student was queued for and the promotion
     * sweep has put a hold on it in their name (ADR-011). Their
     * {@code aggregate_id} is a {@code waitlist_entries} row rather than a
     * booking — see {@code OutboxEvent.aggregateId} — because a promotion
     * happens before any booking exists.
     *
     * <p>Neither of these two carries a {@code dedupe_key}. That column is
     * unique across the whole table and is also what marks a row as a
     * reminder, so a waitlist notification carrying one could be killed by
     * {@code OutboxEventRepository.cancelScheduled} on an unrelated
     * cancellation.
     */
    WAITLIST_PROMOTED,
    /**
     * The student did nothing before their promotion ran out, so the entry has
     * ended and the seat goes to the next in line. Like {@code BOOKING_EXPIRED}
     * this takes something away with nobody asking, so the message has to
     * explain itself rather than confirm anything.
     */
    WAITLIST_PROMOTION_EXPIRED
}
