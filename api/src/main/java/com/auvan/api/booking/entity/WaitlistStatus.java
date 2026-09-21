package com.auvan.api.booking.entity;

/**
 * Where a student stands in a trip's queue.
 *
 * <p>{@code WAITING} and {@code PROMOTED} are the queued states — the ones that
 * count towards a position and the ones a student can leave. The other three
 * are terminal: the student booked the seat they were offered, left of their
 * own accord, or did not act before their promotion lapsed (ADR-011).
 *
 * <p>{@code WAITING} and {@code WITHDRAWN} are the student's own doing;
 * {@code PROMOTED}, {@code FULFILLED} and {@code EXPIRED} are written only by
 * the promotion sweep, each from behind the entry's row lock.
 */
public enum WaitlistStatus {
    WAITING,
    PROMOTED,
    FULFILLED,
    WITHDRAWN,
    EXPIRED
}
