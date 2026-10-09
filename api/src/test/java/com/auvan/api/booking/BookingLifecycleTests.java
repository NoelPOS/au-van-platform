package com.auvan.api.booking;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingLifecycleTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-21T10:00:00Z");
    private static final OffsetDateTime DEADLINE = NOW.plusHours(2);

    @Test
    void aNewBookingWaitsForPaymentRatherThanBeingConfirmed() {
        Booking booking = newBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.isAwaitingPaymentProof()).isTrue();
        assertThat(booking.getPaymentDeadlineAt()).isEqualTo(DEADLINE);
    }

    @Test
    void aSubmittedProofPutsTheBookingUnderReview() {
        Booking booking = newBooking();

        booking.markPaymentUnderReview(NOW.plusMinutes(3));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(3));
        assertThat(booking.getPaymentDeadlineAt()).isNull();
        assertThat(booking.isUnderPaymentReview()).isTrue();
        assertThat(booking.isAwaitingPaymentProof()).isFalse();
        assertThat(booking.isExpirable(NOW.plusYears(1))).isFalse();
    }

    @Test
    void aCancelledBookingAcceptsNoProof() {
        Booking booking = newBooking();

        booking.cancel(NOW.plusMinutes(1));

        assertThat(booking.isAwaitingPaymentProof()).isFalse();
        assertThat(booking.isUnderPaymentReview()).isFalse();
    }

    @Test
    void anApprovedPaymentConfirmsTheBooking() {
        Booking booking = newBooking();
        booking.markPaymentUnderReview(NOW.plusMinutes(3));

        booking.confirm(NOW.plusMinutes(9));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(9));
        assertThat(booking.isUnderPaymentReview()).isFalse();
        assertThat(booking.getPaymentDeadlineAt()).isNull();
        assertThat(booking.isExpirable(NOW.plusYears(1))).isFalse();
    }

    @Test
    void onlyANonTerminalBookingWhoseDeadlineHasPassedIsExpirable() {
        Booking booking = newBooking();

        assertThat(booking.isExpirable(DEADLINE.minusSeconds(1))).isFalse();
        assertThat(booking.isExpirable(DEADLINE)).isTrue();
        assertThat(booking.isExpirable(DEADLINE.plusSeconds(1))).isTrue();

        booking.cancel(DEADLINE.plusMinutes(1));
        assertThat(booking.isExpirable(DEADLINE.plusHours(1))).isFalse();
    }

    @Test
    void anExpiredBookingIsCancelledAndCarriesNoDeadlineToExpireAgainst() {
        Booking booking = newBooking();

        booking.expire(DEADLINE.plusMinutes(1));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getUpdatedAt()).isEqualTo(DEADLINE.plusMinutes(1));
        assertThat(booking.getPaymentDeadlineAt()).isNull();
        assertThat(booking.isAwaitingPaymentProof()).isFalse();
    }

    @Test
    void aRejectedBookingAcceptsAnotherProof() {
        Booking booking = newBooking();
        booking.markPaymentUnderReview(NOW.plusMinutes(3));

        booking.markPaymentRejected(NOW.plusMinutes(9).plusHours(2), NOW.plusMinutes(9));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_REJECTED);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(9));
        assertThat(booking.getPaymentDeadlineAt()).isEqualTo(NOW.plusMinutes(9).plusHours(2));
        assertThat(booking.isAwaitingPaymentProof()).isTrue();
        assertThat(booking.isUnderPaymentReview()).isFalse();
    }

    @Test
    void anApprovedProofRecordsWhoDecidedItAndWhen() {
        UUID administrator = UUID.randomUUID();
        PaymentProof proof = newProof();

        proof.approve(administrator, null, NOW.plusMinutes(9));

        assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(proof.getReviewedByUserId()).isEqualTo(administrator);
        assertThat(proof.getReviewedAt()).isEqualTo(NOW.plusMinutes(9));
        assertThat(proof.getReviewNote()).isNull();
        assertThat(proof.isSubmitted()).isFalse();
    }

    @Test
    void aRejectedProofKeepsTheReasonItWasRejectedFor() {
        UUID administrator = UUID.randomUUID();
        PaymentProof proof = newProof();

        proof.reject(administrator, "The slip is too blurred to read.", NOW.plusMinutes(9));

        assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.REJECTED);
        assertThat(proof.getReviewedByUserId()).isEqualTo(administrator);
        assertThat(proof.getReviewedAt()).isEqualTo(NOW.plusMinutes(9));
        assertThat(proof.getReviewNote()).isEqualTo("The slip is too blurred to read.");
        assertThat(proof.isSubmitted()).isFalse();
    }

    private static PaymentProof newProof() {
        PaymentProof proof = new PaymentProof(newBooking(), UUID.randomUUID(),
                "payment-proofs/booking/slip.jpg", "image/jpeg", 12, NOW.plusMinutes(3));
        assertThat(proof.isSubmitted()).isTrue();
        return proof;
    }

    private static Booking newBooking() {
        VanRoute route = new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45);
        SeatLayout layout = new SeatLayout("Layout VAN-01", List.of(new SeatLayoutSeat("A1", 1, 1)));
        Trip trip = new Trip(route, new Vehicle("VAN-01", "Toyota Commuter", layout), NOW.plusDays(1));
        return new Booking(trip, UUID.randomUUID(), "AUV-260921-7KQ2M4XR", "Somchai P.", "0812345678",
                new BigDecimal("35.00"), DEADLINE, NOW);
    }
}
