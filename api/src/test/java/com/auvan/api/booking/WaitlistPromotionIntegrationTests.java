package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatAvailabilityService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.WaitlistPromotionScheduler;
import com.auvan.api.booking.service.WaitlistPromotionService;
import com.auvan.api.booking.service.WaitlistService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.outbox.RecordingLineMessageSender;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * The promotion sweep: who gets a seat that has come free, what they get, what
 * they are told, and what happens when they do nothing with it.
 *
 * <p>Not {@code @Transactional}. The sweep opens a transaction per entry and a
 * test-managed one around it would hide every rollback this class asserts on.
 * Rows are aged with {@link JdbcTemplate} rather than by waiting, for the
 * reason {@code OutboxIntegrationTests} gives: nothing injects a {@code Clock}
 * and the entities have no setters beyond the transitions that own them.
 *
 * <p>{@code booking.waitlist.enabled} is false in the test configuration, so
 * every sweep here is one this class asked for.
 */
@SpringBootTest
class WaitlistPromotionIntegrationTests extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeSenderConfiguration {
        @Bean
        @Primary
        RecordingLineMessageSender recordingLineMessageSender() {
            return new RecordingLineMessageSender();
        }
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WaitlistPromotionService promotion;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpiryService expiry;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private RecordingLineMessageSender sender;

    // Spies, not mocks: every call runs for real except the one a test stubs to
    // put the promoter in the state a lost race leaves it in.
    @MockitoSpyBean
    private WaitlistEntryRepository waitlist;

    @MockitoSpyBean
    private SeatAvailabilityService availability;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    private JdbcTemplate jdbc;

    private Trip trip;
    private UUID studentA;
    private UUID studentB;
    private UUID holder;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        trip = createTrip("VAN-WP1", OffsetDateTime.now().plusDays(1), 2);
        studentA = users.save(new AppUser("Uwait-a", "Waiting Student A")).getId();
        studentB = users.save(new AppUser("Uwait-b", "Waiting Student B")).getId();
        holder = users.save(new AppUser("Uwait-holder", "Holding Student")).getId();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        events.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    // Success paths

    /**
     * A cancellation frees a seat and the next sweep gives it to the student who
     * has been waiting longest — as a hold of their own, not as a booking
     * (ADR-011), so they walk the ordinary confirm-and-pay path from here.
     */
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

    /**
     * <strong>The test that decides the design.</strong> A hold that simply
     * lapses runs no code at all — ADR-006 made expiry lazy — so this seat came
     * free with nothing to hang a hook on, and it is the commonest release of
     * all. Move promotion to the three call sites that do run code and only
     * this test reddens.
     *
     * <p>It also proves the reclaim: the lapsed row is still sitting on the
     * seat, and one row per seat is all {@code seat_claims} permits, so a
     * promoter that did not clear it could never insert.
     */
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

    /** The other sweep frees the seat; this one hands it on. */
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

    /**
     * A promoted hold is an ordinary hold: {@code BookingService.create} takes
     * it with no special case anywhere in the booking path, which is the whole
     * reason ADR-011 promotes into {@code seat_claims} rather than creating a
     * booking on the student's behalf.
     *
     * <p>The entry becomes {@code FULFILLED} when its window runs out and the
     * sweep finds the seats booked. That is the only place the booking path and
     * the waitlist meet, and it is a read: the alternative was a waitlist hook
     * inside {@code BookingWriter}, which would have made "unchanged" false.
     */
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

    /**
     * The student is told through the outbox and nothing else, and the row
     * carries <strong>no dedupe key</strong>: that column is unique table-wide
     * and is what {@code OutboxEventRepository.cancelScheduled} treats as "this
     * is a reminder", so a waitlist row with one could be killed by an
     * unrelated booking cancellation.
     */
    @Test
    void aPromotionWritesAnOutboxRowForThePromotedStudentThatRendersAMessage() {
        UUID first = promoteFirstStudentOntoSeatZero();

        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(first);
            assertThat(event.getRecipientUserId()).isEqualTo(studentA);
            assertThat(event.getDedupeKey()).isNull();
        });

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Uwait-a");
            assertThat(message.text()).contains("AU-Van waitlist")
                    .contains("held for you")
                    .contains("AU to Asok")
                    .contains("Take the seats by");
        });
    }

    /**
     * Seats freed one at a time go to the queue in join order. The ordering is
     * {@code joined_at} and nothing else, so the three entries are aged apart
     * rather than left to the clock's resolution.
     */
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

    /** An entry waiting for two seats is promoted onto both, under one hold. */
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

    // Failure paths

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

    /**
     * One chance per promotion. The student did nothing, so the entry ends, an
     * outbox row says so, and the seat goes to the next in line.
     *
     * <p>The seat reaches the next student in the pass that ends the lapsed
     * promotion rather than the one after it. Nothing chains them — the lapsed
     * hold stopped blocking its seat the moment it expired, so the promotion
     * half simply derives a free seat like any other, and the ordering inside
     * {@code sweep()} is what puts the ending first.
     */
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

    /**
     * A promotion is bounded by departure exactly as a payment deadline is, so
     * it never outlives the seat's usefulness.
     */
    @Test
    void aPromotionCloseToDepartureGetsTheShorterDeadline() {
        Trip soon = createTrip("VAN-WP3", OffsetDateTime.now().plusMinutes(70), 1);
        fillEverySeatOf(soon);
        UUID first = join(studentA, soon, 1);
        lapseHoldOn(soon.getSeats().getFirst());

        assertThat(promotion.sweep()).isOne();

        OffsetDateTime deadline = entry(first).getPromotionExpiresAt();
        // departureAt - booking.departure-cutoff, which is ten minutes away and
        // therefore sooner than the thirty-minute window.
        assertThat(deadline).isCloseTo(soon.getDepartureAt().minusHours(1), within(2, ChronoUnit.SECONDS));
        assertThat(deadline).isBefore(OffsetDateTime.now().plusMinutes(30));
    }

    /**
     * Past {@code departureAt - departure-cutoff} the deadline comes back in
     * the past, and a hold that has expired before it is written helps nobody.
     * ADR-011 chose that over special-casing the trip.
     */
    @Test
    void aTripPastItsDepartureBoundPromotesNobody() {
        Trip departing = createTrip("VAN-WP4", OffsetDateTime.now().plusMinutes(30), 1);
        fillEverySeatOf(departing);
        UUID first = join(studentA, departing, 1);
        lapseHoldOn(departing.getSeats().getFirst());

        assertThat(promotion.sweep()).isZero();

        assertThat(entry(first).getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
        assertThat(claims.findAll()).noneSatisfy(claim -> assertThat(claim.getUserId()).isEqualTo(studentA));
    }

    /**
     * <strong>The rollback proof.</strong> The promoter is told a seat is free
     * that somebody is in fact holding — the state a lost race leaves it in —
     * so the real insert meets {@code seat_claims_trip_seat_unique} and the
     * whole transaction goes back.
     *
     * <p>Nothing may survive it: no {@code outbox_events} row, no promotion on
     * the entry, and the holder's claim untouched. Give {@code OutboxRecorder}
     * a transaction of its own and the row outlives the promotion that owed it,
     * which is the divergence the outbox exists to prevent and which only this
     * test would notice.
     */
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

    /**
     * Strict first in, first out. One seat is free and the head of the queue is
     * waiting for two, so nobody is promoted: giving it to the student behind
     * them would be the one thing FIFO must not do.
     */
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

    /**
     * The candidate list is a hint and the lock decides. Here it names an entry
     * the student has since left, which is what a real sweep reads whenever a
     * {@code leave} commits between the two statements.
     */
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
        // Only the lapsed row the promoter would have reclaimed, untouched.
        assertThat(claims.findBySeatIdIn(List.of(seat(0).getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(holder));
    }

    /**
     * The gate, from the side that protects every other test in the suite.
     * Remove {@code @ConditionalOnProperty} from
     * {@code WaitlistPromotionScheduler} and a live sweeper exists in every
     * {@code @SpringBootTest} context, handing seats out under the booking and
     * payment-proof concurrency fixtures mid-assertion. This assertion and
     * {@code WaitlistPromotionSchedulingIntegrationTests}' opposite one are the
     * two halves of that gate.
     */
    @Test
    void theSchedulerIsNotWiredUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(WaitlistPromotionScheduler.class)).isEmpty();
    }

    /** The same discipline on the other half of the sweep. */
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

    // Fixtures

    /** Fills the trip, queues student A, and frees seat zero by letting its hold lapse. */
    private UUID promoteFirstStudentOntoSeatZero() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        lapseHoldOn(0);
        assertThat(promotion.sweep()).isOne();
        return first;
    }

    private UUID join(UUID userId, Trip on, int seatsWanted) {
        return waitlistService.join(userId, new JoinWaitlistRequest(on.getId(), seatsWanted)).id();
    }

    private WaitlistEntry entry(UUID entryId) {
        return waitlist.findById(entryId).orElseThrow();
    }

    private TripSeat seat(int index) {
        return trip.getSeats().get(index);
    }

    private UUID book(UUID userId, int seatIndex) {
        UUID holdId = holds.hold(userId, new CreateSeatHoldRequest(trip.getId(),
                List.of(seat(seatIndex).getId()))).holdId();
        bookingService.create(userId, "key-" + userId + "-" + seatIndex,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).getFirst().getId();
    }

    private void holdSeat(UUID userId, int seatIndex, Duration ttl) {
        claims.save(new SeatClaim(seat(seatIndex), userId, UUID.randomUUID(), OffsetDateTime.now().plus(ttl)));
    }

    /**
     * Ages a hold past its expiry with nothing running, which is how a seat
     * most often comes free: ADR-006 made expiry lazy, so the row stays and
     * simply stops blocking.
     */
    private void lapseHoldOn(int seatIndex) {
        lapseHoldOn(seat(seatIndex));
    }

    private void lapseHoldOn(TripSeat seat) {
        jdbc.update("update seat_claims set expires_at = ? where trip_seat_id = ?",
                OffsetDateTime.now().minusMinutes(1), seat.getId());
    }

    /** The promotion window running out, and its hold with it. */
    private void lapsePromotion(UUID entryId) {
        jdbc.update("update waitlist_entries set promotion_expires_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), entryId);
    }

    private void joinedAt(UUID entryId, OffsetDateTime moment) {
        jdbc.update("update waitlist_entries set joined_at = ? where id = ?", moment, entryId);
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    /** Claims every seat for somebody else, which is what "full" means here. */
    private void fillEverySeatOf(Trip on) {
        UUID filler = users.save(new AppUser("Ufiller-" + on.getId(), "Holding Student")).getId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);
        on.getSeats().forEach(seat -> claims.save(new SeatClaim(seat, filler, UUID.randomUUID(), expiresAt)));
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, int seatCount) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= seatCount; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, departureAt));
    }
}
