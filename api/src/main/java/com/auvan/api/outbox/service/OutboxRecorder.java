package com.auvan.api.outbox.service;

import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Records what a committed transaction owes the outside world.
 *
 * <p><strong>Deliberately not annotated {@code @Transactional}</strong>: this
 * has to join the caller's transaction rather than open one of its own, exactly
 * as {@code IdempotencyService.record} does and for the same reason. It is the
 * whole of the guarantee — the event and the state change it describes are one
 * commit, so a rolled-back booking leaves no message owed and a committed one
 * leaves exactly one. Give this its own transaction and that stops being true
 * while every test that only counts rows keeps passing.
 *
 * <p>Callers that go on to run a {@code clearAutomatically} bulk delete — the
 * cancellation path does — must flush between this call and that delete.
 * Clearing the persistence context discards an unflushed insert silently, so
 * the row would simply not be there and nothing would say why.
 */
@Service
public class OutboxRecorder {
    private final OutboxEventRepository events;
    private final ObjectMapper json;

    public OutboxRecorder(OutboxEventRepository events, ObjectMapper json) {
        this.events = events;
        this.json = json;
    }

    /**
     * @param aggregateId     the booking the event is about
     * @param recipientUserId the student the message is for, which is the
     *                        booking's owner and never the actor: an
     *                        administrator's approval is news for the student
     * @param payload         serialised as the row's JSON payload; the handler
     *                        that renders the message is the only thing that
     *                        reads it back
     */
    public OutboxEvent record(OutboxEventType type, UUID aggregateId, UUID recipientUserId, Object payload,
                              OffsetDateTime now) {
        return events.save(new OutboxEvent(type, aggregateId, recipientUserId,
                json.writeValueAsString(payload), now));
    }
}
