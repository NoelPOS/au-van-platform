package com.auvan.api.booking;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

class PaymentProofSubmissionConcurrencyIntegrationTests extends PaymentProofConcurrencyTestSupport {
    @Test
    void aSecondSubmissionInFlightIsRefusedAndOnlyOneProofExists() throws Exception {
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    loser.set(pool.submit(() -> paymentProofs.submit(student, bookingId, jpeg("the-retry"))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(bookings).lockByIdAndUserId(bookingId, student);

            assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                    .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("booking_not_awaiting_payment");
                    });
        }

        assertThat(proofs.count()).isOne();
        assertThat(storage.objects()).hasSize(1);
        assertThat(storage.objects().values()).singleElement()
                .satisfies(object -> assertThat(object.content())
                        .isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8)));
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus())
                        .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW));
    }
}
