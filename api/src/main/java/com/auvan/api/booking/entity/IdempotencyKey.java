package com.auvan.api.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The response a critical write already produced, so that a retry of the same
 * request returns it again instead of writing a second time.
 *
 * <p>The response is stored rather than re-rendered. Re-rendering on replay
 * would return whatever the booking looks like now, so a retry that arrived
 * after a cancellation would answer {@code 201 Created} with a cancelled
 * booking.
 *
 * <p>{@code requestHash} is what tells a genuine retry from a key reused for a
 * different payload. The unique constraint on
 * {@code (user_id, endpoint, idempotency_key)} is what makes the write happen
 * at most once, so two racing duplicates cannot both be stored.
 */
@Entity
@Table(name = "idempotency_keys", uniqueConstraints = @UniqueConstraint(
        name = "idempotency_keys_scope_unique", columnNames = {"user_id", "endpoint", "idempotency_key"}))
public class IdempotencyKey {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String endpoint;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, length = 4000)
    private String responseBody;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected IdempotencyKey() { }

    public IdempotencyKey(UUID userId, String endpoint, String idempotencyKey, String requestHash,
                          int responseStatus, String responseBody, OffsetDateTime now) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public int getResponseStatus() { return responseStatus; }
    public String getResponseBody() { return responseBody; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
