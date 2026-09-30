package com.auvan.api.booking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "booking.expiry", name = "enabled", havingValue = "true")
public class BookingExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(BookingExpiryScheduler.class);

    private final BookingExpiryService expiry;

    public BookingExpiryScheduler(BookingExpiryService expiry) {
        this.expiry = expiry;
    }

    @Scheduled(fixedDelayString = "${booking.expiry.poll-interval}")
    void expireOverdue() {
        try {
            expiry.sweep();
        } catch (RuntimeException failure) {
            log.error("Booking expiry sweep failed; the next sweep will try again.", failure);
        }
    }
}
