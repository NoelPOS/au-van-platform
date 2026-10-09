package com.auvan.api.booking;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatState;
import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.IdempotencyKey;
import com.auvan.api.booking.service.BookingExpiryScheduler;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class BookingExpiryIntegrationTests extends BookingExpiryTestSupport {
    @Test
    void anOverduePendingPaymentBookingIsCancelledAndItsSeatBecomesAvailableAgain() {
        UUID bookingId = createBooking("key-pending");
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow()).satisfies(booking -> {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getPaymentDeadlineAt()).isNull();
        });
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(seatState(trip.getSeats().getFirst())).isEqualTo(SeatState.AVAILABLE);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).singleElement().satisfies(event -> {
            assertThat(event.actorUserId()).isNull();
            assertThat(event.detail()).contains("Expired unpaid").contains("A1");
        });
    }

    @Test
    void anOverdueRejectedBookingIsExpiredToo() {
        UUID bookingId = createBooking("key-rejected");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PAYMENT_REJECTED);
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
    }

    @Test
    void anUnderReviewBookingIsNeitherAnExpiryCandidateNorExpirableEvenWithAStaleDeadline() {
        UUID bookingId = createBooking("key-under-review");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        overdue(bookingId);
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(bookings.findExpirable(now, PageRequest.of(0, 50))).doesNotContain(bookingId);
        assertThat(expiryWriter.expire(bookingId, now)).isFalse();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(review.list()).hasSize(1);
    }

    @Test
    void eachExpiredBookingLeavesExactlyOnePendingOutboxRowAddressedToItsOwner() {
        UUID first = createBooking("key-outbox-1");
        UUID second = createBooking("key-outbox-2", trip.getSeats().get(1), otherStudent);
        overdue(first);
        overdue(second);

        assertThat(expiry.sweep()).isEqualTo(2);

        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).hasSize(2)
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING))
                .extracting(OutboxEvent::getAggregateId, OutboxEvent::getRecipientUserId)
                .containsExactlyInAnyOrder(tuple(first, student), tuple(second, otherStudent));
    }

    @Test
    void theSweepPrunesSpentIdempotencyKeysAndLeavesRecentOnesAlone() {
        UUID recent = storedKey("recent-key", OffsetDateTime.now().minusHours(1));
        UUID spent = storedKey("spent-key", OffsetDateTime.now().minusHours(25));

        expiry.sweep();

        assertThat(idempotencyKeys.findById(recent)).isPresent();
        assertThat(idempotencyKeys.findById(spent)).isEmpty();
    }

    @Test
    void aSeatFreedByExpiryCanBeHeldAndBookedByAnotherStudent() {
        TripSeat seat = trip.getSeats().getFirst();
        UUID abandoned = createBooking("key-abandoned");
        overdue(abandoned);

        assertThat(expiry.sweep()).isOne();

        UUID holdId = holds.hold(otherStudent,
                new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
        bookingService.create(otherStudent, "key-rebooked",
                new CreateBookingRequest(holdId, "Nok S.", "0899999999"));

        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(otherStudent)).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT));
        assertThat(claims.findBySeatIdIn(List.of(seat.getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(otherStudent));
    }

    @Test
    void aConfirmedBookingIsNeverExpiredWhateverItsDeadlineSays() {
        UUID bookingId = createBooking("key-confirmed");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        overdue(bookingId);

        assertThat(expiry.sweep()).isZero();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).isEmpty();
    }

    @Test
    void anAlreadyCancelledBookingIsNotExpiredASecondTime() {
        UUID bookingId = createBooking("key-twice");
        overdue(bookingId);
        assertThat(expiry.sweep()).isOne();

        overdue(bookingId);
        assertThat(expiry.sweep()).isZero();

        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).hasSize(1);
    }

    @Test
    void aBookingWhoseDeadlineHasNotPassedIsLeftAlone() {
        UUID bookingId = createBooking("key-in-time");

        assertThat(expiry.sweep()).isZero();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).isEmpty();
    }

    @Test
    void noSweeperRunsUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(BookingExpiryScheduler.class)).isEmpty();
    }

    private UUID storedKey(String key, OffsetDateTime createdAt) {
        return idempotencyKeys.save(new IdempotencyKey(student, "POST /api/v1/bookings", key,
                "0".repeat(64), 201, "{}", createdAt)).getId();
    }

    private SeatState seatState(TripSeat seat) {
        return availability.seatMap(trip.getId(), otherStudent).seats().stream()
                .filter(mapped -> mapped.id().equals(seat.getId()))
                .map(TripSeatMapResponse.SeatResponse::state)
                .findFirst()
                .orElseThrow();
    }

    private List<BookingResponse.BookingEventResponse> bookingEventsOfType(UUID bookingId, BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }
}
