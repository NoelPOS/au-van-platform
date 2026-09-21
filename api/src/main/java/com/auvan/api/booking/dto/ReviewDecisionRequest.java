package com.auvan.api.booking.dto;

/**
 * The administrator's note on a decision: optional when approving, required
 * when rejecting, 500 characters at most.
 *
 * <p>One record for both endpoints, and no bean-validation annotations: those
 * answer {@code 400} with no {@code code}, and every other refusal in this
 * module carries one. The rules live in the service for that reason.
 */
public record ReviewDecisionRequest(String note) { }
