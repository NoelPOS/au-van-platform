package com.auvan.api.booking.entity;

/** What happened to a booking. The history is append-only, so this never changes. */
public enum BookingEventType {
    CREATED,
    PAYMENT_PROOF_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    CANCELLED,
    /**
     * The sweep released an unpaid booking's seats. Its event carries no actor:
     * {@code booking_events.actor_user_id} is nullable for exactly this, and
     * {@link BookingEvent}'s own javadoc names system-driven transitions as the
     * reason. ADR-010 chose this over a new {@code BookingStatus}.
     */
    EXPIRED
}
