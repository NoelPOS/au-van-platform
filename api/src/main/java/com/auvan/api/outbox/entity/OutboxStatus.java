package com.auvan.api.outbox.entity;

public enum OutboxStatus {
    PENDING,
    IN_FLIGHT,
    SENT,
    DEAD
}
