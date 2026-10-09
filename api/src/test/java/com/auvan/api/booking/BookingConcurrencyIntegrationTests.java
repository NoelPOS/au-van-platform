package com.auvan.api.booking;

import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.entity.TripSeat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
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
import static org.mockito.Mockito.doAnswer;

class BookingConcurrencyIntegrationTests extends BookingConcurrencyTestSupport {
    @Test
    void theSecondConfirmationOfOneHoldIsRefusedAndOnlyOneBookingExists() throws Exception {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<IdempotencyService.StoredResponse>> loser = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    loser.set(pool.submit(() -> bookingService.create(student, "rival-key", request(holdId))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(claims).lockByHoldId(holdId);

            bookingService.create(student, "student-key", request(holdId));

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("hold_already_used");
                    });
        }

        assertThat(bookings.count()).isOne();
        assertThat(claims.findByHoldId(holdId))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isEqualTo(bookings.findAll().getFirst().getId()));
    }

    @Test
    void reSelectingSeatsCannotFreeAClaimThatHasJustBeenBooked() {
        TripSeat booked = seats.get(0);
        TripSeat wanted = seats.get(1);
        UUID holdId = holdOn(student, booked);
        AtomicReference<UUID> bookingId = new AtomicReference<>();
        List<SeatClaim> mine = claims.findHoldsOnTripBy(trip.getId(), student);

        doAnswer(invocation -> {
            bookingId.set(confirmOnAnotherThread(holdId));
            return mine;
        }).when(claims).findHoldsOnTripBy(trip.getId(), student);

        seatHoldService.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(wanted.getId())));

        assertThat(claims.findByBookingId(bookingId.get()))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getTripSeat().getId()).isEqualTo(booked.getId()));
        assertThat(claims.count()).isEqualTo(2);
    }

    @Test
    void losingTheRaceForAReferenceIsAConflictAndWritesNothing() {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean first = new AtomicBoolean(true);

        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                takeReferenceOnAnotherThread(invocation.<Booking>getArgument(0).getReference());
            }
            return real(invocation);
        }).when(bookings).save(any(Booking.class));

        assertThatThrownBy(() -> bookingService.create(student, "student-key", request(holdId)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(codeOf(conflict)).isEqualTo("booking_creation_conflict");
                });

        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isZero();
        assertThat(claims.findByHoldId(holdId))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isNull());
    }

    private UUID confirmOnAnotherThread(UUID holdId) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> bookingService.create(student, "other-thread-key", request(holdId)))
                    .get(30, TimeUnit.SECONDS);
        }
        return bookings.findAll().getFirst().getId();
    }

    private void takeReferenceOnAnotherThread(String reference) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> transactions.executeWithoutResult(status -> bookings.save(new Booking(
                            trip, rival, reference, "Rival Student", "0800000000",
                            new BigDecimal("35.00"), OffsetDateTime.now().plusHours(2),
                            OffsetDateTime.now()))))
                    .get(30, TimeUnit.SECONDS);
        }
    }
}
