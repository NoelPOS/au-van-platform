package com.auvan.api.notification.dto;

/**
 * The facts an outbox row carries about a booking, and the whole of its
 * {@code payload} column.
 *
 * <p>Facts, not prose: for a state change {@code detail} is the same sentence
 * the booking's own history entry carries, and for a departure reminder it is
 * the route and the departure time. The message a student finally reads is
 * composed from it by {@code BookingNotificationHandler}, which is the only
 * thing that knows what a message says. Keeping the wording out of the row
 * means a change to how a message is phrased does not have to be migrated into
 * rows that were written before it — which matters most for a reminder, whose
 * row can be written days before it is read.
 */
public record BookingNotification(String reference, String detail) { }
