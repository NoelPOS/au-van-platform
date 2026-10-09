package com.auvan.api.booking;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.inventory.entity.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PaymentDeadlineIntegrationTests extends BookingExpiryTestSupport {
    @Test
    void aNewBookingGetsTheShorterOfThePaymentWindowAndTheDepartureBound() {
        UUID bookingId = createBooking("key-window");

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void aBookingMadeCloseToDepartureIsBoundedByTheDepartureCutoffInstead() {
        Trip soon = trips.save(new Trip(trip.getRoute(), trip.getVehicle(), OffsetDateTime.now().plusMinutes(100)));
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(soon.getId(),
                List.of(soon.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, "key-soon", new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));

        UUID bookingId = bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(soon.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void submittingAProofStopsTheDeadlineSoASlowReviewNeverExpiresTheBooking() {
        UUID bookingId = createBooking("key-submit");

        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt()).isNull();
        assertThat(expiry.sweep()).isZero();
    }

    @Test
    void theV10MigrationClearsTheDeadlineOfEveryBookingAlreadyUnderReview() throws Exception {
        UUID waiting = createBooking("key-v10-waiting", trip.getSeats().get(1), otherStudent);
        UUID underReview = createBooking("key-v10-under-review");
        paymentProofs.submit(student, underReview, jpeg("the-slip"));
        overdue(underReview);

        jdbc.update(statementOf("db/migration/V10__stop_deadline_under_payment_review.sql", "UPDATE bookings"));

        assertThat(bookings.findById(underReview).orElseThrow().getPaymentDeadlineAt()).isNull();
        assertThat(bookings.findById(waiting).orElseThrow().getPaymentDeadlineAt()).isNotNull();
    }

    @Test
    void aQuickRejectionKeepsTheOriginalDeadlineRatherThanStartingAFreshWindow() {
        UUID bookingId = createBooking("key-reject-early");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        Booking rejected = bookings.findById(bookingId).orElseThrow();
        assertThat(rejected.getPaymentDeadlineAt())
                .isCloseTo(rejected.getCreatedAt().plusHours(2), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void aRejectionAfterTheOriginalDeadlineGivesThirtyMinutesToResubmit() {
        UUID bookingId = createBooking("key-reject-late");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        jdbc.update("update bookings set created_at = ? where id = ?", OffsetDateTime.now().minusHours(3), bookingId);

        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusMinutes(30), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void theV8BackfillBoundsEachNonTerminalStatusByItsOwnFormulaAndLeavesTerminalOnesNull() throws Exception {
        UUID waiting = createBooking("key-backfill-waiting");
        UUID confirmed = createBooking("key-backfill-confirmed", trip.getSeats().get(1), otherStudent);
        paymentProofs.submit(otherStudent, confirmed, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        UUID thirdStudent = users.save(new AppUser("Uthird-expiry", "Third Student")).getId();
        UUID underReview = createBooking("key-backfill-under-review", trip.getSeats().get(2), thirdStudent);
        paymentProofs.submit(thirdStudent, underReview, jpeg("the-slip"));
        jdbc.update("update bookings set payment_deadline_at = null");

        jdbc.update(statementOf("db/migration/V8__add_booking_payment_deadline.sql", "UPDATE bookings"));

        assertThat(bookings.findById(waiting).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(underReview).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(confirmed).orElseThrow().getPaymentDeadlineAt()).isNull();
    }

    private static String statementOf(String path, String opening) throws Exception {
        String migration = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int start = migration.indexOf(opening);
        assertThat(start).isNotNegative();
        return migration.substring(start, migration.indexOf(';', start) + 1);
    }
}
