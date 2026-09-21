package com.auvan.api.outbox.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One piece of outbound work, recorded in the transaction that caused it.
 *
 * <p>Nothing here mutates the row after it is created. Every state change a
 * dispatch makes — the claim and the outcome — is a conditional {@code UPDATE}
 * in {@link com.auvan.api.outbox.repository.OutboxEventRepository}, because
 * each one has to be decided by the database rather than by a value some worker
 * read a moment ago.
 *
 * <p>Mirrored from {@code V7} under the same names and widths, so the mapping
 * and the migration cannot describe this table differently without it showing
 * in review.
 */
@Entity
@Table(name = "outbox_events", uniqueConstraints =
        @UniqueConstraint(name = "outbox_events_dedupe_key_unique", columnNames = "dedupe_key"))
public class OutboxEvent {
    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private OutboxEventType eventType;

    /** The booking this is about. A plain column: see {@code V7} on why there is no foreign key. */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    /** The {@code app_users} row the message is for, frozen at record time. */
    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Column(nullable = false, length = 2000)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    /**
     * Null for a state-change event, which legitimately repeats, and set for a
     * scheduled departure reminder, where the unique constraint on this column
     * is what makes scheduling idempotent — the legacy application's own
     * {@code unique (bookingId, type)} ({@code src/models/ReminderJob.ts:46}).
     *
     * <p>It is therefore also the column that says <em>which rows are
     * reminders</em>, which is what
     * {@link com.auvan.api.outbox.repository.OutboxEventRepository#cancelScheduled}
     * selects on when a booking stops being eligible for one.
     */
    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    protected OutboxEvent() { }

    /** Due immediately: a notification is owed the moment its transaction commits. */
    public OutboxEvent(OutboxEventType eventType, UUID aggregateId, UUID recipientUserId, String payload,
                       OffsetDateTime now) {
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.recipientUserId = recipientUserId;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.attempts = 0;
        this.nextAttemptAt = now;
        this.createdAt = now;
    }

    /**
     * A reminder: the same row, due in the future and carrying a dedupe key.
     *
     * <p>That is the whole of what makes a scheduled reminder different from a
     * notification, and the reason ADR-010 rejected a second {@code reminder_jobs}
     * table — the claim, the backoff and the dead-letter rule are already
     * written here and would otherwise be written twice.
     *
     * @param dueAt when the reminder should reach the student, which is always
     *              in the future: a reminder whose time has already passed is
     *              not scheduled at all
     */
    public OutboxEvent(OutboxEventType eventType, UUID aggregateId, UUID recipientUserId, String payload,
                       String dedupeKey, OffsetDateTime dueAt, OffsetDateTime now) {
        this(eventType, aggregateId, recipientUserId, payload, now);
        this.dedupeKey = dedupeKey;
        this.nextAttemptAt = dueAt;
    }

    public UUID getId() { return id; }
    public OutboxEventType getEventType() { return eventType; }
    public UUID getAggregateId() { return aggregateId; }
    public UUID getRecipientUserId() { return recipientUserId; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public OffsetDateTime getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public String getDedupeKey() { return dedupeKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getProcessedAt() { return processedAt; }
}
