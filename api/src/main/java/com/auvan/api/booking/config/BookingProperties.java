package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * The booking rules that are configuration rather than code.
 *
 * <p>{@code paymentWindow} and {@code departureCutoff} are a <strong>new
 * product rule</strong>, not one ported from the legacy application, which
 * creates an unpaid booking and never expires it. ADR-010 records the decision
 * and the two defaults: two hours is long enough for a bank transfer and short
 * enough to recycle a seat, and an hour before departure is the point past
 * which an unpaid seat helps nobody.
 *
 * <p>The two deadline methods live here because this is the one place both
 * durations exist, and because the rule is arithmetic over them rather than
 * anything a service decides.
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdTtl, int maxSeatsPerHold, Duration paymentWindow,
                                Duration departureCutoff, Duration idempotencyKeyRetention, Expiry expiry) {
    /**
     * The scheduled sweep. {@code enabled} is {@code false} in the test
     * configuration for the reason {@code OutboxProperties.Dispatch} gives: a
     * live sweep would cancel the booking and payment-proof concurrency tests'
     * own fixtures mid-assertion. Tests call {@code BookingExpiryService.sweep()}
     * directly.
     */
    public record Expiry(boolean enabled, Duration pollInterval, int batchSize) { }

    /**
     * The deadline a booking gets when it starts waiting for a payment:
     * {@code min(now + paymentWindow, departureAt - departureCutoff)}.
     *
     * <p>A booking created close to departure legitimately gets a deadline that
     * has already passed and is expired by the next sweep. That is the point —
     * an unpaid seat twenty minutes before departure helps nobody — and it is
     * why the deadline is in the booking response rather than left implicit.
     */
    public OffsetDateTime paymentDeadlineFor(OffsetDateTime departureAt, OffsetDateTime now) {
        OffsetDateTime window = now.plus(paymentWindow);
        OffsetDateTime departureBound = departureBoundFor(departureAt);
        return window.isBefore(departureBound) ? window : departureBound;
    }

    /**
     * The deadline a booking gets once its proof is with an administrator: the
     * departure bound alone, with no timer. A student waiting on a reviewer is
     * not the one holding things up, so only the seat's remaining usefulness
     * bounds them.
     */
    public OffsetDateTime departureBoundFor(OffsetDateTime departureAt) {
        return departureAt.minus(departureCutoff);
    }
}
