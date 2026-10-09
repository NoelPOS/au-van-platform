package com.auvan.api.outbox.service;

import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
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
    private final LiveSignalPublisher live;

    public OutboxRecorder(OutboxEventRepository events, ObjectMapper json, LiveSignalPublisher live) {
        this.events = events;
        this.json = json;
        this.live = live;
    }

    public OutboxEvent record(OutboxEventType type, UUID aggregateId, UUID recipientUserId, Object payload,
                              OffsetDateTime now) {
        live.publish(LiveSignal.booking(aggregateId, recipientUserId));
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
