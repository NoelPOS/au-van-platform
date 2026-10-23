package com.auvan.api.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "inventory.rolling-schedule", name = "enabled", havingValue = "true")
public class RollingScheduleJob {
    private static final Logger log = LoggerFactory.getLogger(RollingScheduleJob.class);

    private final RollingSchedule schedule;

    public RollingScheduleJob(RollingSchedule schedule) {
        this.schedule = schedule;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "${inventory.rolling-schedule.cron}", zone = "Asia/Bangkok")
    void topUp() {
        try {
            int created = schedule.topUp(OffsetDateTime.now());
            log.info("Rolling schedule added {} departures.", created);
        } catch (RuntimeException failure) {
            log.error("Rolling schedule top-up failed; the next run will try again.", failure);
        }
    }
}
