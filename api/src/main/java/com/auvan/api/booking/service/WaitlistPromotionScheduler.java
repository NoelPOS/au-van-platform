package com.auvan.api.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The trigger, and nothing else, exactly as {@link BookingExpiryScheduler} is.
 *
 * <p>It is a class of its own so that {@link EnableScheduling} has the smallest
 * possible scope. On the application class it would start a scheduler in every
 * {@code @SpringBootTest} in the suite, and a live promotion sweep handing
 * seats out under a booking-concurrency fixture mid-assertion is a flaky
 * failure with no visible cause. Gated on {@code booking.waitlist.enabled},
 * which the test configuration sets to {@code false}: tests call
 * {@link WaitlistPromotionService#sweep()} directly.
 *
 * <p>Every instance may sweep. Nothing coordinates them and nothing needs to,
 * because each entry's row lock decides who promotes it — which is also why
 * this work needs no Redis lock and must not acquire one (ADR-010, ADR-011).
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "booking.waitlist", name = "enabled", havingValue = "true")
public class WaitlistPromotionScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionScheduler.class);

    private final WaitlistPromotionService promotion;

    public WaitlistPromotionScheduler(WaitlistPromotionService promotion) {
        this.promotion = promotion;
    }

    /**
     * {@code fixedDelay}, not {@code fixedRate}: a sweep that runs long must not
     * have the next one start on top of it.
     */
    @Scheduled(fixedDelayString = "${booking.waitlist.poll-interval}")
    void promoteWaiting() {
        try {
            promotion.sweep();
        } catch (RuntimeException failure) {
            // A sweep that throws — an insert that failed for a reason the
            // promoter does not treat as a lost race, say — must not cancel the
            // schedule and stop this instance sweeping for good.
            log.error("Waitlist promotion sweep failed; the next sweep will try again.", failure);
        }
    }
}
