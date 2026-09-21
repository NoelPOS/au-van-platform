package com.auvan.api.notification.dto;

/**
 * The facts an outbox row carries about a booking, and the whole of its
 * {@code payload} column.
 *
 * <p>Facts, not prose: {@code detail} is the same sentence the booking's own
 * history entry carries, and the message a student finally reads is composed
 * from it by {@code BookingNotificationHandler}. Keeping the wording out of the
 * row means a change to how a message is phrased does not have to be migrated
 * into rows that were written before it.
 */
public record BookingNotification(String reference, String detail) { }
