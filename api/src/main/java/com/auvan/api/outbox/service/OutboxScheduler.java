package com.auvan.api.outbox.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "outbox.dispatch", name = "enabled", havingValue = "true")
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxDispatcher dispatcher;

    public OutboxScheduler(OutboxDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${outbox.dispatch.poll-interval}")
    void dispatchDue() {
        try {
            dispatcher.dispatchBatch();
        } catch (RuntimeException failure) {
            log.error("Outbox dispatch failed; the next poll will try again.", failure);
        }
    }
}
