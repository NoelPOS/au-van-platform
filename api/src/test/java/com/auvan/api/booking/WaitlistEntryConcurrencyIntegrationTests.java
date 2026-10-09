package com.auvan.api.booking;

import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

class WaitlistEntryConcurrencyIntegrationTests extends WaitlistPromotionConcurrencyTestSupport {
    @Test
    void twoSweepersRacingOneEntryPromoteItExactlyOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AtomicReference<Future<Boolean>> second = new AtomicReference<>();
        CountDownLatch secondAtTheLock = new CountDownLatch(1);
        AtomicBoolean firstLock = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (firstLock.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    second.set(pool.submit(() -> writer.promote(entryId, now)));
                    assertThat(secondAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                secondAtTheLock.countDown();
                return real(invocation);
            }).when(waitlist).lockById(entryId);

            assertThat(writer.promote(entryId, now)).isTrue();
            assertThat(second.get().get(30, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(claimsOnTheContestedSeat()).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).hasSize(1);
    }

    @Test
    void aPromotionRacingTheStudentsOwnLeaveLeavesNoHoldBehindTheWithdrawnEntry() throws Exception {
        AtomicReference<Future<?>> leaving = new AtomicReference<>();
        CountDownLatch leaveAtTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                leaveAtTheLock.countDown();
                return real(invocation);
            }).when(waitlist).lockByIdAndUserId(entryId, student);
            doAnswer(invocation -> {
                Object locked = real(invocation);
                leaving.set(pool.submit(() -> waitlistService.leave(student, entryId)));
                assertThat(leaveAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                return locked;
            }).when(waitlist).lockById(entryId);

            assertThat(promotion.sweep()).isOne();
            leaving.get().get(30, TimeUnit.SECONDS);
        }

        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(entry().getPromotionHoldId()).isNull();
        assertThat(claimsOnTheContestedSeat()).isEmpty();
    }
}
