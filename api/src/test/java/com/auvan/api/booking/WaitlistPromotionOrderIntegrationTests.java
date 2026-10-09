package com.auvan.api.booking;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class WaitlistPromotionOrderIntegrationTests extends WaitlistPromotionTestSupport {
    @Test
    void seatsFreedOneAtATimeGoToTheQueueInJoinOrder() {
        Trip roomy = createTrip("VAN-WP2", OffsetDateTime.now().plusDays(2), 3);
        fillEverySeatOf(roomy);
        UUID first = join(studentA, roomy, 1);
        UUID second = join(studentB, roomy, 1);
        UUID third = join(users.save(new AppUser("Uwait-c", "Waiting Student C")).getId(), roomy, 1);
        joinedAt(first, OffsetDateTime.now().minusMinutes(3));
        joinedAt(second, OffsetDateTime.now().minusMinutes(2));
        joinedAt(third, OffsetDateTime.now().minusMinutes(1));

        List<UUID> queue = List.of(first, second, third);
        for (int seatIndex = 0; seatIndex < queue.size(); seatIndex++) {
            lapseHoldOn(roomy.getSeats().get(seatIndex));
            assertThat(promotion.sweep()).isOne();
            assertThat(entry(queue.get(seatIndex)).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        }

        assertThat(entry(first).getPromotionHoldId())
                .isNotEqualTo(entry(second).getPromotionHoldId())
                .isNotEqualTo(entry(third).getPromotionHoldId());
    }

    @Test
    void anEntryWaitingForTwoSeatsIsPromotedOntoBothUnderOneHold() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 2);
        lapseHoldOn(0);
        lapseHoldOn(1);

        assertThat(promotion.sweep()).isOne();

        WaitlistEntry promoted = entry(first);
        assertThat(claims.findByHoldId(promoted.getPromotionHoldId())).hasSize(2)
                .allSatisfy(claim -> assertThat(claim.getUserId()).isEqualTo(studentA));
    }

    @Test
    void aLapsedPromotionEndsAndTheSeatGoesToTheNextEntry() {
        UUID first = promoteFirstStudentOntoSeatZero();
        UUID second = join(studentB, trip, 1);
        lapsePromotion(first);
        lapseHoldOn(0);

        assertThat(promotion.sweep()).isOne();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.EXPIRED);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTION_EXPIRED)).singleElement()
                .satisfies(event -> {
                    assertThat(event.getAggregateId()).isEqualTo(first);
                    assertThat(event.getRecipientUserId()).isEqualTo(studentA);
                    assertThat(event.getDedupeKey()).isNull();
                });
        assertThat(entry(second).getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(studentB));
    }

    @Test
    void oneFreeSeatIsLeftAloneWhenTheHeadOfTheQueueIsWaitingForTwo() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID head = join(studentA, trip, 2);
        UUID behind = join(studentB, trip, 1);
        joinedAt(head, OffsetDateTime.now().minusMinutes(2));
        joinedAt(behind, OffsetDateTime.now().minusMinutes(1));
        lapseHoldOn(0);

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(head).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(entry(behind).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
    }

    @Test
    void aLapseCandidateThatIsStillWaitingIsLeftAlone() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        doReturn(List.of(first)).when(waitlist).findLapsedPromotionIds(any(OffsetDateTime.class),
                any(Pageable.class));

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
    }

    private void joinedAt(UUID entryId, OffsetDateTime moment) {
        jdbc.update("update waitlist_entries set joined_at = ? where id = ?", moment, entryId);
    }
}
