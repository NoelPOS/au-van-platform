package com.auvan.api.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(int batchSize, int maxAttempts, Duration backoffBase, Duration backoffCap,
                               Duration lease, Dispatch dispatch) {
    public record Dispatch(boolean enabled, Duration pollInterval) { }
}
