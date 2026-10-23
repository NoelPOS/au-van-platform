package com.auvan.api.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "inventory.rolling-schedule")
public record RollingScheduleProperties(boolean enabled, String template, int daysAhead, String cron) { }
