package com.auvan.api.booking;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProofStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.concurrent.Callable;
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

class PaymentProofReviewConcurrencyIntegrationTests extends PaymentProofConcurrencyTestSupport {
    @Test
    void aSecondApprovalInFlightIsRefusedAndTheBookingIsConfirmedOnce() throws Exception {
        UUID proofId = submittedProofId();
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseRivalAtTheLock(pool, () -> review.approve(administrator, proofId, null), loser);

            assertThat(review.approve(administrator, proofId, null).status())
                    .isEqualTo(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("payment_proof_already_decided");
                    });
        }

        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isOne();
    }

    @Test
    void anApprovalAndARejectionInFlightResolveToOneOutcome() throws Exception {
        UUID proofId = submittedProofId();
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseRivalAtTheLock(pool, () -> review.reject(administrator, proofId, "The slip is unreadable."),
                    loser);

            assertThat(review.approve(administrator, proofId, null).status())
                    .isEqualTo(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("payment_proof_already_decided");
                    });
        }

        assertThat(proofs.findById(proofId).orElseThrow()).satisfies(proof -> {
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
            assertThat(proof.getReviewedByUserId()).isEqualTo(administrator);
            assertThat(proof.getReviewNote()).isNull();
        });
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isOne();
        assertThat(eventsOfType(BookingEventType.PAYMENT_REJECTED)).isZero();
    }

    @Test
    void anApprovalRacingAStudentCancellationIsRefusedRatherThanConfirmingAReleasedBooking() throws Exception {
        UUID proofId = submittedProofId();
        AtomicBoolean firstCancellation = new AtomicBoolean(true);
        AtomicReference<Future<BookingResponse>> approval = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                atTheLock.countDown();
                return real(invocation);
            }).when(bookings).lockById(bookingId);
            doAnswer(invocation -> {
                if (firstCancellation.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    approval.set(pool.submit(() -> review.approve(administrator, proofId, null)));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                return real(invocation);
            }).when(bookings).lockByIdAndUserId(bookingId, student);

            assertThat(bookingService.cancel(student, bookingId).status()).isEqualTo(BookingStatus.CANCELLED);

            assertThatThrownBy(() -> approval.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("booking_not_under_review");
                    });
        }

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isZero();
    }

    private UUID submittedProofId() {
        assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        return proofs.findAll().getFirst().getId();
    }

    private void releaseRivalAtTheLock(ExecutorService pool, Callable<BookingResponse> rival,
                                       AtomicReference<Future<BookingResponse>> loser) {
        AtomicBoolean winner = new AtomicBoolean(true);
        CountDownLatch atTheLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (winner.compareAndSet(true, false)) {
                Object locked = real(invocation);
                loser.set(pool.submit(rival));
                assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                return locked;
            }
            atTheLock.countDown();
            return real(invocation);
        }).when(bookings).lockById(bookingId);
    }

    private long eventsOfType(BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .count();
    }
}
