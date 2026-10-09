package com.auvan.api.live.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "live-updates")
public record LiveUpdatesProperties(boolean postgresNotify, Duration ticketTtl, Duration heartbeatInterval) { }
