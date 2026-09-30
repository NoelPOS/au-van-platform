package com.auvan.api.outbox.service;

import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

// Not @Transactional: joins the caller's, so the event commits with its state change. Callers
// must flush before a clearAutomatically delete, which silently discards an unflushed row.
@Service
public class OutboxRecorder {
    private final OutboxEventRepository events;
    private final ObjectMapper json;

    public OutboxRecorder(OutboxEventRepository events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    public OutboxEvent record(OutboxEventType type, UUID aggregateId, UUID recipientUserId, Object payload,
                              OffsetDateTime now) {
        return events.save(new OutboxEvent(type, aggregateId, recipientUserId,
                json.writeValueAsString(payload), now));
    }

    public OutboxEvent schedule(OutboxEventType type, UUID aggregateId, UUID recipientUserId, Object payload,
                                String dedupeKey, OffsetDateTime dueAt, OffsetDateTime now) {
        if (events.existsByDedupeKey(dedupeKey)) {
            return null;
        }
        return events.save(new OutboxEvent(type, aggregateId, recipientUserId,
                json.writeValueAsString(payload), dedupeKey, dueAt, now));
    }
}
