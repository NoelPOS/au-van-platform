package com.auvan.api.live.service;

import com.auvan.api.live.dto.LiveSignal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@ConditionalOnProperty(prefix = "live-updates", name = "postgres-notify", havingValue = "false")
public class InProcessLiveSignalPublisher implements LiveSignalPublisher {
    private final LiveStreams streams;

    public InProcessLiveSignalPublisher(LiveStreams streams) {
        this.streams = streams;
    }

    @Override
    public void publish(LiveSignal signal) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            streams.deliver(signal);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streams.deliver(signal);
            }
        });
    }
}
