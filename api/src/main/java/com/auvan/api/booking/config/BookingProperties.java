package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.OffsetDateTime;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdTtl, int maxSeatsPerHold, Duration paymentWindow,
                                Duration departureCutoff, Duration closesBeforeDeparture, Duration resubmitWindow,
                                Duration idempotencyKeyRetention, Expiry expiry, Waitlist waitlist,
                                Cooldown cooldown) {
    public record Expiry(boolean enabled, Duration pollInterval, int batchSize) { }

    public record Waitlist(boolean enabled, Duration promotionWindow, Duration pollInterval, int batchSize) { }

    public record Cooldown(int expiries, Duration lookback, Duration duration) { }

    public OffsetDateTime paymentDeadlineFor(OffsetDateTime departureAt, OffsetDateTime now) {
        return earlierOf(now.plus(paymentWindow), departureBoundFor(departureAt));
    }

    public OffsetDateTime resubmitDeadlineFor(OffsetDateTime departureAt, OffsetDateTime bookedAt,
                                              OffsetDateTime now) {
        OffsetDateTime original = paymentDeadlineFor(departureAt, bookedAt);
        OffsetDateTime window = now.plus(resubmitWindow);
        return earlierOf(original.isAfter(window) ? original : window, departureBoundFor(departureAt));
    }

    public OffsetDateTime departureBoundFor(OffsetDateTime departureAt) {
        return departureAt.minus(departureCutoff);
    }

    public OffsetDateTime bookingClosesAt(OffsetDateTime departureAt) {
        return departureAt.minus(closesBeforeDeparture);
    }

    public OffsetDateTime promotionDeadlineFor(OffsetDateTime departureAt, OffsetDateTime now) {
        return earlierOf(now.plus(waitlist.promotionWindow()), departureBoundFor(departureAt));
    }

    private static OffsetDateTime earlierOf(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }
}
