package com.auvan.api.booking.entity;

/**
 * Where a submitted proof stands. Submission is all this slice can produce;
 * the administrator's decision that reaches the other two arrives with #52.
 */
public enum PaymentProofStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
