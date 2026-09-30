package com.auvan.api.outbox.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxBackoffTests {
    private static final Duration BASE = Duration.ofSeconds(30);
    private static final Duration CAP = Duration.ofHours(1);

    @Test
    void theFirstFailureWaitsTheBaseAndEachOneAfterItWaitsTwiceAsLong() {
        assertThat(OutboxDispatcher.backoffFor(1, BASE, CAP)).isEqualTo(Duration.ofSeconds(30));
        assertThat(OutboxDispatcher.backoffFor(2, BASE, CAP)).isEqualTo(Duration.ofMinutes(1));
        assertThat(OutboxDispatcher.backoffFor(3, BASE, CAP)).isEqualTo(Duration.ofMinutes(2));
        assertThat(OutboxDispatcher.backoffFor(4, BASE, CAP)).isEqualTo(Duration.ofMinutes(4));
    }

    @Test
    void theWaitStopsGrowingAtTheCapAndNeverOverflows() {
        assertThat(OutboxDispatcher.backoffFor(8, BASE, CAP)).isEqualTo(CAP);
        assertThat(OutboxDispatcher.backoffFor(64, BASE, CAP)).isEqualTo(CAP);
        assertThat(OutboxDispatcher.backoffFor(Integer.MAX_VALUE, BASE, CAP)).isEqualTo(CAP);
    }

    @Test
    void fiveAttemptsSpanFarLessThanTheRetryKeyWindow() {
        Duration total = Duration.ZERO;
        for (int attempt = 1; attempt < 5; attempt++) {
            total = total.plus(OutboxDispatcher.backoffFor(attempt, BASE, CAP));
        }

        assertThat(total).isEqualTo(Duration.ofMinutes(7).plusSeconds(30));
        assertThat(total).isLessThan(Duration.ofHours(24));
    }
}
