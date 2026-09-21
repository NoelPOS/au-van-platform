package com.auvan.api.booking.entity;

/**
 * Where a student stands in a trip's queue.
 *
 * <p>{@code WAITING} and {@code PROMOTED} are the queued states — the ones that
 * count towards a position and the ones a student can leave. The other three
 * are terminal: the student booked the seat they were offered, left of their
 * own accord, or did not act before their promotion lapsed (ADR-011).
 *
 * <p>Only {@code WAITING} and {@code WITHDRAWN} are reachable on this branch.
 * The promotion sweep that writes the other three is issue #69.
 */
public enum WaitlistStatus {
    WAITING,
    PROMOTED,
    FULFILLED,
    WITHDRAWN,
    EXPIRED
}
