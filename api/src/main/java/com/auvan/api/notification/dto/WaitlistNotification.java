package com.auvan.api.notification.dto;

/**
 * The facts an outbox row carries about a waitlist promotion, and the whole of
 * its {@code payload} column.
 *
 * <p>The shape of {@link BookingNotification} for a thing that is not a
 * booking: a promotion happens before any booking exists, so there is no
 * reference to quote and the trip has to identify itself.
 *
 * <p>Facts, not prose, for the same reason: {@code trip} is the route and the
 * departure time, {@code detail} is the deadline the student is working to or
 * the one they missed, and the sentence around them belongs to
 * {@code BookingNotificationHandler}, which is the only thing that knows what a
 * message says. Both fields are short on purpose — {@code outbox_events.payload}
 * is {@code VARCHAR(2000)} and nothing here should start listing seats.
 */
public record WaitlistNotification(String trip, String detail) { }
