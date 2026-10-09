package com.auvan.api.booking;

import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

class WaitlistPromotionConcurrencyIntegrationTests extends WaitlistPromotionConcurrencyTestSupport {
    @Test
    void aPromotionRacingADirectHoldLosesTheSeatAndLeavesTheEntryWaiting() throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheRivalOnceThePromoterHasReadAvailability(pool, () ->
                    holds.hold(rival, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))));

            assertThat(promotion.sweep()).isZero();
        }

        assertThat(claimsOnTheContestedSeat()).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(rival));
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
        assertThat(promotion.sweep()).isZero();
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(claimsOnTheContestedSeat()).hasSize(1);
    }

    @Test
    void aPromotionRacingABookingConfirmationOnAFreedSeatLeavesExactlyOneClaim() throws Exception {
        UUID cancelled = book(rival, "key-promorace-first");
        bookingService.cancel(rival, cancelled);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheRivalOnceThePromoterHasReadAvailability(pool, () -> book(rival, "key-promorace-second"));

            assertThat(promotion.sweep()).isZero();
        }

        UUID sold = bookings.findByUserIdOrderByCreatedAtDesc(rival).getFirst().getId();
        assertThat(claims.findByBookingId(sold)).hasSize(1);
        assertThat(claimsOnTheContestedSeat()).singleElement().satisfies(claim -> {
            assertThat(claim.getUserId()).isEqualTo(rival);
            assertThat(claim.getBookingId()).isEqualTo(sold);
        });
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).isEmpty();
    }

    @Test
    void aPromoterDoesNotReclaimASeatAConfirmationHasJustSold() throws Exception {
        UUID holdId = holds.hold(rival, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))).holdId();
        OffsetDateTime later = OffsetDateTime.now().plusMinutes(10);
        AtomicBoolean firstRead = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                Object onSeats = real(invocation);
                if (firstRead.compareAndSet(true, false)) {
                    pool.submit(() -> bookingService.create(rival, "key-promorace-reclaim",
                            new CreateBookingRequest(holdId, "Somchai P.", "0812345678")))
                            .get(10, TimeUnit.SECONDS);
                }
                return onSeats;
            }).when(claims).findBySeatIdIn(List.of(contestedSeat));

            assertThatThrownBy(() -> writer.promote(entryId, later)).hasMessageContaining("lost the race");
        }

        UUID sold = bookings.findByUserIdOrderByCreatedAtDesc(rival).getFirst().getId();
        assertThat(claims.findByBookingId(sold)).hasSize(1);
        assertThat(claimsOnTheContestedSeat()).singleElement().satisfies(claim -> {
            assertThat(claim.getUserId()).isEqualTo(rival);
            assertThat(claim.getBookingId()).isEqualTo(sold);
        });
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).isEmpty();
    }

    private void releaseTheRivalOnceThePromoterHasReadAvailability(ExecutorService pool, Callable<?> rivalAction) {
        AtomicBoolean firstRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object free = real(invocation);
            if (firstRead.compareAndSet(true, false)) {
                pool.submit(rivalAction).get(10, TimeUnit.SECONDS);
            }
            return free;
        }).when(availability).freeSeatsOf(any(Trip.class), any(OffsetDateTime.class));
    }

    private UUID book(UUID userId, String idempotencyKey) {
        UUID holdId = holds.hold(userId, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))).holdId();
        bookingService.create(userId, idempotencyKey,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).getFirst().getId();
    }
}
