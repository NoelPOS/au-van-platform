package com.auvan.api.booking;

import com.auvan.api.booking.config.BookingProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void aPromotionIsCutShortByTheDepartureBound() {
        assertThat(properties.promotionDeadlineFor(NOW.plusMinutes(70), NOW)).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void aTripInsideTheDepartureCutoffGetsADeadlineThatHasAlreadyPassed() {
        assertThat(properties.promotionDeadlineFor(NOW.plusMinutes(20), NOW)).isBefore(NOW);
    }
}
