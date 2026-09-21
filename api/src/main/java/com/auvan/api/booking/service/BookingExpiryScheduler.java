package com.auvan.api.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger, and nothing else, exactly as {@code OutboxScheduler} is.
 *
 * <p>It is a class of its own so that {@link EnableScheduling} has the smallest
 * possible scope. On the application class it would start a scheduler in every
 * {@code @SpringBootTest} in the suite, and a background sweep cancelling a
 * booking-concurrency fixture mid-assertion is a flaky failure with no visible
 * cause. Gated on {@code booking.expiry.enabled}, which the test configuration
 * sets to {@code false}: tests call {@link BookingExpiryService#sweep()}
 * directly.
 *
 * <p>Every instance may sweep. Nothing coordinates them and nothing needs to,
 * because each booking's row lock decides who expires it — which is also why
 * this work needs no Redis lock and must not acquire one (ADR-010).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "booking.expiry", name = "enabled", havingValue = "true")
public class BookingExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingExpiryService expiry;

    public BookingExpiryScheduler(BookingExpiryService expiry) {
        this.expiry = expiry;
    }

    /**
     * {@code fixedDelay}, not {@code fixedRate}: a sweep that runs long must not
     * have the next one start on top of it.
     */
    @Scheduled(fixedDelayString = "${booking.expiry.poll-interval}")
    void expireOverdue() {
        try {
            expiry.sweep();
        } catch (RuntimeException failure) {
            // A sweep that throws — the database is unreachable, say — must not
            // cancel the schedule and stop this instance sweeping for good.
            log.error("Booking expiry sweep failed; the next sweep will try again.", failure);
        }
    }
}
