package com.auvan.api.booking;

import com.auvan.api.booking.service.IdempotencyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IdempotencyConcurrencyIntegrationTests extends BookingConcurrencyTestSupport {
    @Test
    void aDuplicateInFlightRequestIsAnsweredWithTheResponseTheWinnerStored() throws Exception {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<IdempotencyService.StoredResponse>> duplicate = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    duplicate.set(pool.submit(() -> bookingService.create(student, "one-key", request(holdId))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(claims).lockByHoldId(holdId);

            IdempotencyService.StoredResponse sent = bookingService.create(student, "one-key", request(holdId));

            assertThat(duplicate.get().get(30, TimeUnit.SECONDS)).isEqualTo(sent);
        }

        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isOne();
    }

    @Test
    void aReplayIsAnsweredWithoutTouchingTheHold() {
        UUID holdId = holdOn(student, seats.getFirst());
        IdempotencyService.StoredResponse sent = bookingService.create(student, "one-key", request(holdId));
        clearInvocations(claims);

        assertThat(bookingService.create(student, "one-key", request(holdId))).isEqualTo(sent);

        verify(claims, never()).lockByHoldId(any());
    }

    @Test
    void aSecondRecordUnderOneKeyIsAConflictRatherThanAFailureAtCommit() {
        OffsetDateTime now = OffsetDateTime.now();
        transactions.executeWithoutResult(status ->
                idempotency.record(student, ENDPOINT, "one-key", "hash-of-the-first", 201, "first", now));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                idempotency.record(student, ENDPOINT, "one-key", "hash-of-the-second", 201, "second", now)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(codeOf(conflict)).isEqualTo("idempotency_conflict");
                });

        assertThat(idempotencyKeys.count()).isOne();
    }
}
