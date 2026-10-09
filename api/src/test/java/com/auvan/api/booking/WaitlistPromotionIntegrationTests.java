package com.auvan.api.booking;

import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.service.WaitlistPromotionScheduler;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WaitlistPromotionIntegrationTests extends WaitlistPromotionTestSupport {
    @Test
    void cancellingABookingThenSweepingPromotesTheFirstWaitingEntry() {
        UUID bookingId = book(holder, 0);
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        UUID second = join(studentB, trip, 1);

        bookingService.cancel(holder, bookingId);
        assertThat(promotion.sweep()).isOne();

        WaitlistEntry promoted = entry(first);
        assertThat(promoted.getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(promoted.getPromotionHoldId()).isNotNull();
        assertThat(claims.findByHoldId(promoted.getPromotionHoldId())).singleElement().satisfies(claim -> {
            assertThat(claim.getUserId()).isEqualTo(studentA);
            assertThat(claim.getTripSeat().getId()).isEqualTo(seat(0).getId());
            assertThat(claim.getBookingId()).isNull();
            assertThat(claim.getExpiresAt()).isEqualTo(promoted.getPromotionExpiresAt());
        });
        assertThat(entry(second).getStatus()).isEqualTo(WaitlistStatus.WAITING);
    }

    @Test
    void aHoldThatLapsedWithNothingRunningIsStillSweptAndPromoted() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        lapseHoldOn(0);

        assertThat(promotion.sweep()).isOne();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(studentA));
    }

    @Test
    void expiringAnUnpaidBookingThenSweepingPromotes() {
        UUID bookingId = book(holder, 0);
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), bookingId);

        assertThat(expiry.sweep()).isOne();
        assertThat(promotion.sweep()).isOne();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(studentA));
    }

    @Test
    void aPromotedStudentBooksTheSeatAndTheSweepMarksTheEntryFulfilled() {
        UUID first = promoteFirstStudentOntoSeatZero();
        UUID holdId = entry(first).getPromotionHoldId();

        bookingService.create(studentA, "key-promoted",
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        lapsePromotion(first);
        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.FULFILLED);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTION_EXPIRED)).isEmpty();
        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(studentA)).hasSize(1);
        assertThat(claims.findByHoldId(holdId)).singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isNotNull());
    }

    @Test
    void aPromotionWritesAnOutboxRowForThePromotedStudentThatRendersAMessage() {
        UUID first = promoteFirstStudentOntoSeatZero();

        OutboxEvent recorded = outboxOfType(OutboxEventType.WAITLIST_PROMOTED).getFirst();
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(first);
            assertThat(event.getRecipientUserId()).isEqualTo(studentA);
            assertThat(event.getDedupeKey()).isNull();
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getPayload()).contains("\"origin\":\"AU\"", "\"destination\":\"Asok\"")
                    .containsPattern("\"seats\":\\[\"A").containsPattern("\"fare\":\\d")
                    .containsPattern("\"offerExpiresAt\":\"20");
        });

        // Aged a minute back: the column keeps less precision than the clock, so a row
        // recorded and claimed at the same now can read as not yet due.
        dueAt(recorded.getId(), OffsetDateTime.now().minusMinutes(1));

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(events.findById(recorded.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Uwait-a");
            assertThat(message.message().altText()).contains("AU-Van waitlist")
                    .contains("held for you")
                    .contains("AU to Asok")
                    .contains("Take the seats by");
        });
    }

    @Test
    void theSchedulerIsNotWiredUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(WaitlistPromotionScheduler.class)).isEmpty();
    }

    private UUID book(UUID userId, int seatIndex) {
        UUID holdId = holds.hold(userId, new CreateSeatHoldRequest(trip.getId(),
                List.of(seat(seatIndex).getId()))).holdId();
        bookingService.create(userId, "key-" + userId + "-" + seatIndex,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).getFirst().getId();
    }

    private void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }
}
