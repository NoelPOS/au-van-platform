package com.auvan.api.booking.entity;

/** The lifecycle of a booking in this slice. Payment states arrive with #7. */
public enum BookingStatus {
    CONFIRMED,
    CANCELLED
}
