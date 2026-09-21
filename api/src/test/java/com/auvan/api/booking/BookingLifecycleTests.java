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

/**
 * Where a booking starts and how a payment proof moves it. ADR-009 made the
 * review the only path to {@code CONFIRMED}, so creation producing a confirmed
 * booking is the regression this class exists to catch.
 */
class BookingLifecycleTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-21T10:00:00Z");
    /** What booking.payment-window would give a booking made at {@link #NOW}. */
    private static final OffsetDateTime DEADLINE = NOW.plusHours(2);
    /** What the departure cutoff would give it: an hour before {@code NOW.plusDays(1)}. */
    private static final OffsetDateTime DEPARTURE_BOUND = NOW.plusDays(1).minusHours(1);

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

        booking.markPaymentUnderReview(DEPARTURE_BOUND, NOW.plusMinutes(3));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(3));
        // Bounded by departure, not by a fresh timer: a slow reviewer must not
        // cost the student their booking (ADR-010).
        assertThat(booking.getPaymentDeadlineAt()).isEqualTo(DEPARTURE_BOUND);
        assertThat(booking.isUnderPaymentReview()).isTrue();
        // A resubmission arrives from a rejection, not from a booking already
        // sitting in front of an administrator.
        assertThat(booking.isAwaitingPaymentProof()).isFalse();
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
        booking.markPaymentUnderReview(DEPARTURE_BOUND, NOW.plusMinutes(3));

        booking.confirm(NOW.plusMinutes(9));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(9));
        assertThat(booking.isUnderPaymentReview()).isFalse();
        // Terminal, so it carries no deadline and can never be expirable.
        assertThat(booking.getPaymentDeadlineAt()).isNull();
        assertThat(booking.isExpirable(NOW.plusYears(1))).isFalse();
    }

    /**
     * The predicate the sweep decides on behind the row lock, and the one thing
     * about it that is not obvious: a null deadline is never expirable, which is
     * what {@code NULL} means on the column and the right answer for every
     * terminal row.
     */
    @Test
    void onlyANonTerminalBookingWhoseDeadlineHasPassedIsExpirable() {
        Booking booking = newBooking();

        assertThat(booking.isExpirable(DEADLINE.minusSeconds(1))).isFalse();
        // The boundary belongs to the sweep: the predicate is deadline <= now.
        assertThat(booking.isExpirable(DEADLINE)).isTrue();
        assertThat(booking.isExpirable(DEADLINE.plusSeconds(1))).isTrue();

        booking.cancel(DEADLINE.plusMinutes(1));
        assertThat(booking.isExpirable(DEADLINE.plusHours(1))).isFalse();
    }

    /**
     * ADR-010 rejected a separate {@code EXPIRED} status: the seats are released
     * and the booking is over either way, so expiry lands on {@code CANCELLED}
     * and the reason lives in the history instead.
     */
    @Test
    void anExpiredBookingIsCancelledAndCarriesNoDeadlineToExpireAgainst() {
        Booking booking = newBooking();

        booking.expire(DEADLINE.plusMinutes(1));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getUpdatedAt()).isEqualTo(DEADLINE.plusMinutes(1));
        assertThat(booking.getPaymentDeadlineAt()).isNull();
        assertThat(booking.isAwaitingPaymentProof()).isFalse();
    }

    /**
     * Rejection is not a dead end (ADR-009): the booking keeps its seats and
     * accepts another proof, which is the whole of the resubmission gate.
     */
    @Test
    void aRejectedBookingAcceptsAnotherProof() {
        Booking booking = newBooking();
        booking.markPaymentUnderReview(DEPARTURE_BOUND, NOW.plusMinutes(3));

        booking.markPaymentRejected(NOW.plusMinutes(9).plusHours(2), NOW.plusMinutes(9));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_REJECTED);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(9));
        // A fresh window: a student told to send a better slip needs time to.
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
