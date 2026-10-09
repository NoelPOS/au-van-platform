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
import com.auvan.api.outbox.entity.OutboxStatus;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

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

    @Test
    void theSchedulerIsNotWiredUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(WaitlistPromotionScheduler.class)).isEmpty();
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

    private void lapseHoldOn(int seatIndex) {
        lapseHoldOn(seat(seatIndex));
    }

    private void lapseHoldOn(TripSeat seat) {
        jdbc.update("update seat_claims set expires_at = ? where trip_seat_id = ?",
                OffsetDateTime.now().minusMinutes(1), seat.getId());
    }

    private void departIn(Trip on, Duration fromNow) {
        jdbc.update("update trips set departure_at = ? where id = ?", OffsetDateTime.now().plus(fromNow), on.getId());
    }

    private void lapsePromotion(UUID entryId) {
        jdbc.update("update waitlist_entries set promotion_expires_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), entryId);
    }

    private void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }

    private void joinedAt(UUID entryId, OffsetDateTime moment) {
        jdbc.update("update waitlist_entries set joined_at = ? where id = ?", moment, entryId);
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

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
