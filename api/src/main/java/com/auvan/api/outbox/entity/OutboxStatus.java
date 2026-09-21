package com.auvan.api.outbox.entity;

/**
 * Where a piece of outbound work has got to.
 *
 * <p>{@code PENDING} and {@code IN_FLIGHT} are both claimable — an
 * {@code IN_FLIGHT} row whose lease has run out belongs to a worker that died
 * mid-send, and reclaiming it is how this design recovers without a second
 * mechanism. {@code SENT} and {@code DEAD} are terminal and are never claimed
 * again, which is what stops a delivered message being delivered twice.
 */
public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    SENT,
    DEAD
}
