package com.auvan.api.booking.dto;

/**
 * How a seat looks to one student right now. Derived from the seat's claim and
 * the current time on every read, and never persisted: an expired hold reads as
 * {@code AVAILABLE} without anything having to sweep it away first.
 */
public enum SeatState {
    AVAILABLE,
    HELD,
    HELD_BY_YOU,
    BOOKED
}
