package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.OffsetDateTime;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdTtl, int maxSeatsPerHold, Duration paymentWindow,
                                Duration departureCutoff, Duration idempotencyKeyRetention, Expiry expiry,
                                Waitlist waitlist) {
    public record Expiry(boolean enabled, Duration pollInterval, int batchSize) { }

    public record Waitlist(boolean enabled, Duration promotionWindow, Duration pollInterval, int batchSize) { }

    public OffsetDateTime paymentDeadlineFor(OffsetDateTime departureAt, OffsetDateTime now) {
        OffsetDateTime window = now.plus(paymentWindow);
        OffsetDateTime departureBound = departureBoundFor(departureAt);
        return window.isBefore(departureBound) ? window : departureBound;
    }

    public OffsetDateTime departureBoundFor(OffsetDateTime departureAt) {
        return departureAt.minus(departureCutoff);
    }

    public OffsetDateTime promotionDeadlineFor(OffsetDateTime departureAt, OffsetDateTime now) {
        OffsetDateTime window = now.plus(waitlist.promotionWindow());
        OffsetDateTime departureBound = departureBoundFor(departureAt);
        return window.isBefore(departureBound) ? window : departureBound;
    }
}
