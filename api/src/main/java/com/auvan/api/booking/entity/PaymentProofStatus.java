package com.auvan.api.booking.entity;

/**
 * Where a submitted proof stands. A proof is {@code SUBMITTED} until an
 * administrator decides, and the decision is final: a rejected proof stays
 * rejected, and a resubmission is a new row.
 */
public enum PaymentProofStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
