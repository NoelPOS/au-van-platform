package com.auvan.api.outbox;

import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

class OutboxRecordingIntegrationTests extends OutboxTestSupport {
    @Test
    void aCommittedBookingCreationLeavesExactlyOnePendingOutboxRowForTheStudent() {
        UUID bookingId = createBooking("key-created");

        assertThat(events.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(OutboxEventType.BOOKING_CREATED);
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getProcessedAt()).isNull();
            assertThat(event.getNextAttemptAt()).isBeforeOrEqualTo(OffsetDateTime.now());
            assertThat(event.getPayload()).contains(bookings.findById(bookingId).orElseThrow().getReference())
                    .contains("\"origin\":\"AU\"", "\"destination\":\"Asok\"", "\"seats\":[\"A1\"]")
                    .containsPattern("\"fare\":35").containsPattern("\"departureAt\":\"20")
                    .containsPattern("\"paymentDeadlineAt\":\"20");
        });
    }

    @Test
    void aBookingWriteThatFailsAfterRecordingLeavesNoOutboxRowAndNoBooking() {
        UUID holdId = holdOn(trip.getSeats().getFirst());
        doThrow(new IllegalStateException("The idempotency record could not be written."))
                .when(idempotency).record(any(), any(), any(), any(), anyInt(), any(), any());

        assertThatThrownBy(() -> bookingService.create(student, "key-doomed",
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events.count()).isZero();
        assertThat(bookings.count()).isZero();
    }

    @Test
    void cancellingABookingLeavesItsOutboxRowDespiteTheClaimDeleteClearingThePersistenceContext() {
        UUID bookingId = createBooking("key-cancel");

        assertThat(bookingService.cancel(student, bookingId).status()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(eventsOfType(OutboxEventType.BOOKING_CANCELLED)).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
        });
    }

    @Test
    void aSubmissionAndTheDecisionOnItEachRecordOneRowAddressedToTheStudent() {
        UUID bookingId = createBooking("key-proof");

        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().getFirst().getId();
        review.approve(administrator, proofId, "Received in full.");

        assertThat(eventsOfType(OutboxEventType.PAYMENT_PROOF_SUBMITTED)).singleElement()
                .satisfies(event -> assertThat(event.getRecipientUserId()).isEqualTo(student));
        assertThat(eventsOfType(OutboxEventType.PAYMENT_APPROVED)).singleElement().satisfies(event -> {
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getPayload()).contains("Received in full.");
        });
    }

    @Test
    void aRejectionRecordsItsOwnRowCarryingTheReasonTheStudentHasToAct() {
        UUID bookingId = createBooking("key-reject");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().getFirst().getId();

        review.reject(administrator, proofId, "The slip is unreadable.");

        assertThat(eventsOfType(OutboxEventType.PAYMENT_REJECTED)).singleElement().satisfies(event -> {
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getPayload()).contains("The slip is unreadable.")
                    .containsPattern("\"paymentDeadlineAt\":\"20");
        });
    }

    private UUID createBooking(String idempotencyKey) {
        UUID holdId = holdOn(trip.getSeats().getFirst());
        bookingService.create(student, idempotencyKey, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findAll().getFirst().getId();
    }

    private UUID holdOn(TripSeat seat) {
        UUID holdId = UUID.randomUUID();
        claims.save(new SeatClaim(seat, student, holdId, OffsetDateTime.now().plus(Duration.ofMinutes(10))));
        return holdId;
    }

    private List<OutboxEvent> eventsOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }
}
