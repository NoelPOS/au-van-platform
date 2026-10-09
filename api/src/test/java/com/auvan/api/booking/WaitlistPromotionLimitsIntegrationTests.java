package com.auvan.api.booking;

import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class WaitlistPromotionLimitsIntegrationTests extends WaitlistPromotionTestSupport {
    @Test
    void aSweepWithNoFreeSeatsPromotesNobodyAndWritesNothing() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(entry(first).getPromotionHoldId()).isNull();
        assertThat(events.count()).isZero();
        assertThat(claims.count()).isEqualTo(2);
    }

    @Test
    void aTripWhoseBookingHasClosedPromotesNobody() {
        Trip soon = createTrip("VAN-WP3", OffsetDateTime.now().plusDays(1), 1);
        fillEverySeatOf(soon);
        UUID first = join(studentA, soon, 1);
        departIn(soon, Duration.ofMinutes(80));
        lapseHoldOn(soon.getSeats().getFirst());

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
    }

    @Test
    void aTripPastItsDepartureBoundPromotesNobody() {
        Trip departing = createTrip("VAN-WP4", OffsetDateTime.now().plusDays(1), 1);
        fillEverySeatOf(departing);
        UUID first = join(studentA, departing, 1);
        departIn(departing, Duration.ofMinutes(30));
        lapseHoldOn(departing.getSeats().getFirst());

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
        assertThat(claims.findAll()).noneSatisfy(claim -> assertThat(claim.getUserId()).isEqualTo(studentA));
    }

    @Test
    void aPromotionWhoseClaimInsertFailsLeavesNoOutboxRowBehind() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        doReturn(List.of(seat(0))).when(availability).freeSeatsOf(any(Trip.class), any(OffsetDateTime.class));

        assertThat(promotion.sweep()).isZero();

        assertThat(events.count()).isZero();
        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(entry(first).getPromotionHoldId()).isNull();
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(holder));
    }

    @Test
    void aCandidateThatIsNoLongerWaitingIsNotPromoted() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        lapseHoldOn(0);
        waitlistService.leave(studentA, first);
        doReturn(List.of(trip.getId())).when(waitlist).findPromotableTripIds(any(OffsetDateTime.class),
                any(Pageable.class));
        doReturn(List.of(first)).when(waitlist).findNextWaiting(any(UUID.class), any(Pageable.class));

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(events.count()).isZero();
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(holder));
    }

    private void departIn(Trip on, Duration fromNow) {
        jdbc.update("update trips set departure_at = ? where id = ?", OffsetDateTime.now().plus(fromNow), on.getId());
    }
}
