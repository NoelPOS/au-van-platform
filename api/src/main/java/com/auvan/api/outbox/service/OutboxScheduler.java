package com.auvan.api.outbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger, and nothing else.
 *
 * <p>It is a class of its own so that {@link EnableScheduling} has the smallest
 * possible scope. On the application class it would start a scheduler in every
 * {@code @SpringBootTest} in the suite, and a background sweep landing in the
 * middle of the booking and payment-proof concurrency tests would move their
 * fixtures under them — a flaky failure with no visible cause. Gated on
 * {@code outbox.dispatch.enabled}, which the test configuration sets to
 * {@code false}: tests call {@link OutboxDispatcher#dispatchBatch()} directly.
 *
 * <p>Every instance may poll. Nothing coordinates them and nothing needs to,
 * because the claim decides who gets a row — which is also why this work needs
 * no Redis lock and must not acquire one.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.dispatch", name = "enabled", havingValue = "true")
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxDispatcher dispatcher;

    public OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * {@code fixedDelay}, not {@code fixedRate}: a batch that runs long must
     * not have the next one start on top of it.
     */
    @Scheduled(fixedDelayString = "${outbox.dispatch.poll-interval}")
    void dispatchDue() {
        try {
            dispatcher.dispatchBatch();
        } catch (RuntimeException failure) {
            // A poll that throws — the database is unreachable, say — must not
            // cancel the schedule and stop this instance polling for good.
            log.error("Outbox dispatch failed; the next poll will try again.", failure);
        }
    }
}
