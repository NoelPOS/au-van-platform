package com.auvan.api.booking;

import com.auvan.api.booking.config.BookingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deadline arithmetic, which is the only thing {@link BookingProperties}
 * does. {@code promotionDeadlineFor} is exercised here rather than through the
 * sweep because the sweep is issue #69: this branch owns the rule, so this
 * branch has to prove it.
 */
class BookingPropertiesTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-22T08:00:00Z");

    private final BookingProperties properties = new BookingProperties(Duration.ofMinutes(5), 4,
            Duration.ofHours(2), Duration.ofHours(1), Duration.ofHours(24),
            new BookingProperties.Expiry(true, Duration.ofMinutes(1), 50),
            new BookingProperties.Waitlist(true, Duration.ofMinutes(30), Duration.ofMinutes(1), 50));

    @Test
    void aPromotionOnADistantTripGetsTheWholePromotionWindow() {
        assertThat(properties.promotionDeadlineFor(NOW.plusDays(1), NOW)).isEqualTo(NOW.plusMinutes(30));
    }

    /**
     * The window is not the only bound. A trip departing inside it gets the
     * shorter deadline, so a promotion never outlives the seat's usefulness.
     */
    @Test
    void aPromotionIsCutShortByTheDepartureBound() {
        assertThat(properties.promotionDeadlineFor(NOW.plusMinutes(70), NOW)).isEqualTo(NOW.plusMinutes(10));
    }

    /**
     * A trip already past its departure bound produces a deadline in the past,
     * which is the point: ADR-011 makes that trip promote nobody rather than
     * having the arithmetic hide it.
     */
    @Test
    void aTripInsideTheDepartureCutoffGetsADeadlineThatHasAlreadyPassed() {
        assertThat(properties.promotionDeadlineFor(NOW.plusMinutes(20), NOW)).isBefore(NOW);
    }
}
