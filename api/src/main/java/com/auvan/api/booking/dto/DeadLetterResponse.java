package com.auvan.api.booking.dto;

import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;

import java.time.OffsetDateTime;
import java.util.UUID;

// No payload: it carries a student's personal data to a screen that does not need it.
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
