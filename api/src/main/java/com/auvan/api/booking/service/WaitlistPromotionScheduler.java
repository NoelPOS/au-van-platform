package com.auvan.api.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "booking.waitlist", name = "enabled", havingValue = "true")
public class WaitlistPromotionScheduler {
    private static final Logger log = LoggerFactory.getLogger(WaitlistPromotionScheduler.class);

    private final WaitlistPromotionService promotion;

    public WaitlistPromotionScheduler(WaitlistPromotionService promotion) {
        this.promotion = promotion;
    }

    @Scheduled(fixedDelayString = "${booking.waitlist.poll-interval}")
    void promoteWaiting() {
        try {
            promotion.sweep();
        } catch (RuntimeException failure) {
            log.error("Waitlist promotion sweep failed; the next sweep will try again.", failure);
        }
    }
}
