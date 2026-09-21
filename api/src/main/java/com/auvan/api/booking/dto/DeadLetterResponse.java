package com.auvan.api.booking.dto;

import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One piece of outbound work that will never be delivered on its own.
 *
 * <p>{@code OutboxEventRepository.markDead} keeps the row precisely so somebody
 * can find it; this is that somebody's view of it. The row is left exactly as
 * the dispatcher left it — this surface reads and never writes.
 *
 * <p>There is no {@code payload} here. The type, the aggregate and the last
 * error are what an operator acts on, and the payload carries a student's name
 * and trip detail into a screen that does not need it.
 *
 * @param aggregateId the thing the event is about — a booking today, and a
 *                    waitlist entry once #69 records promotions. The column
 *                    carries no foreign key by deliberate design, so this is an
 *                    identifier to look up, not a link to follow.
 */
public record DeadLetterResponse(
        UUID id,
        OutboxEventType eventType,
        UUID aggregateId,
        UUID recipientUserId,
        int attempts,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime processedAt) {

    public static DeadLetterResponse from(OutboxEvent event) {
        return new DeadLetterResponse(event.getId(), event.getEventType(), event.getAggregateId(),
                event.getRecipientUserId(), event.getAttempts(), event.getLastError(), event.getCreatedAt(),
                event.getProcessedAt());
    }
}
