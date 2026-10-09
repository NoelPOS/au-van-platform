package com.auvan.api.outbox.service;

import com.auvan.api.notification.service.BookingNotificationHandler;
import com.auvan.api.outbox.config.OutboxProperties;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.exception.PermanentFailureException;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Never run inside a transaction: the send is an HTTP call and would hold row locks across it.
@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final OutboxEventRepository events;
    private final BookingNotificationHandler handler;
    private final OutboxProperties properties;

    public OutboxDispatcher(OutboxEventRepository events, BookingNotificationHandler handler,
                            OutboxProperties properties) {
        this.events = events;
        this.handler = handler;
        this.properties = properties;
    }

    public int dispatchBatch() {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> due = events.findDispatchable(now, PageRequest.of(0, properties.batchSize()));
        int sent = 0;
        for (UUID id : due) {
            if (dispatch(id, now)) {
                sent++;
            }
        }
        return sent;
    }

    private boolean dispatch(UUID id, OffsetDateTime now) {
        if (events.claim(id, now, now.plus(properties.lease())) == 0) {
            return false;
        }
        // Read the row only after the claim, so attempts and status are the ones the claim wrote.
        OutboxEvent claimed = events.findById(id).orElseThrow();
        try {
            handler.handle(claimed);
            events.markSent(id, OffsetDateTime.now());
            return true;
        } catch (RuntimeException failure) {
            recordFailure(claimed, failure);
            return false;
        }
    }

    private void recordFailure(OutboxEvent claimed, RuntimeException failure) {
        String error = truncated(failure.toString());
        if (failure instanceof PermanentFailureException) {
            log.warn("Outbox event {} cannot be delivered and is dead on attempt {}: {}",
                    claimed.getId(), claimed.getAttempts(), error);
            events.markDead(claimed.getId(), OffsetDateTime.now(), error);
            return;
        }
        if (claimed.getAttempts() >= properties.maxAttempts()) {
            log.warn("Outbox event {} failed on attempt {} and is dead: {}",
                    claimed.getId(), claimed.getAttempts(), error);
            events.markDead(claimed.getId(), OffsetDateTime.now(), error);
            return;
        }
        OffsetDateTime nextAttemptAt = OffsetDateTime.now()
                .plus(backoffFor(claimed.getAttempts(), properties.backoffBase(), properties.backoffCap()));
        log.warn("Outbox event {} failed on attempt {}, retrying after {}: {}",
                claimed.getId(), claimed.getAttempts(), nextAttemptAt, error);
        events.markForRetry(claimed.getId(), nextAttemptAt, error);
    }

    static Duration backoffFor(int attempts, Duration base, Duration cap) {
        // Capped at 30 doublings so neither the shift nor multipliedBy can overflow.
        int doublings = Math.min(Math.max(attempts - 1, 0), 30);
        Duration backoff = base.multipliedBy(1L << doublings);
        return backoff.compareTo(cap) > 0 ? cap : backoff;
    }

    // outbox_events.last_error is VARCHAR(1000).
    private static String truncated(String error) {
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
