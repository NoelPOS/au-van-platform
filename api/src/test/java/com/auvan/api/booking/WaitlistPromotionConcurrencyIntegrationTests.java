package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatAvailabilityService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.WaitlistPromotionService;
import com.auvan.api.booking.service.WaitlistPromotionWriter;
import com.auvan.api.booking.service.WaitlistService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
class WaitlistPromotionConcurrencyIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private WaitlistPromotionService promotion;

    @Autowired
    private WaitlistPromotionWriter writer;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private BookingService bookingService;

    @MockitoSpyBean
    private SeatAvailabilityService availability;

    @MockitoSpyBean
    private SeatClaimRepository claims;

    @MockitoSpyBean
    private WaitlistEntryRepository waitlist;

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

    private Trip trip;
    private UUID student;
    private UUID rival;
    private UUID entryId;
    private UUID contestedSeat;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-promorace", "Waiting Student")).getId();
        rival = users.save(new AppUser("Urival-promorace", "Rival Student")).getId();
        UUID holder = users.save(new AppUser("Uholder-promorace", "Holding Student")).getId();
        contestedSeat = trip.getSeats().getFirst().getId();
        claims.save(new SeatClaim(trip.getSeats().get(1), holder, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(2)));
        entryId = waitlist.save(new WaitlistEntry(trip, student, 1, OffsetDateTime.now())).getId();
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
    void aPromotionRacingADirectHoldLosesTheSeatAndLeavesTheEntryWaiting() throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheRivalOnceThePromoterHasReadAvailability(pool, () ->
                    holds.hold(rival, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))));

            assertThat(promotion.sweep()).isZero();
        }

        assertThat(claimsOnTheContestedSeat()).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(rival));
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(events.count()).isZero();
        assertThat(promotion.sweep()).isZero();
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(claimsOnTheContestedSeat()).hasSize(1);
    }

    @Test
    void aPromotionRacingABookingConfirmationOnAFreedSeatLeavesExactlyOneClaim() throws Exception {
        UUID cancelled = book(rival, "key-promorace-first");
        bookingService.cancel(rival, cancelled);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheRivalOnceThePromoterHasReadAvailability(pool, () -> book(rival, "key-promorace-second"));

            assertThat(promotion.sweep()).isZero();
        }

        UUID sold = bookings.findByUserIdOrderByCreatedAtDesc(rival).getFirst().getId();
        assertThat(claims.findByBookingId(sold)).hasSize(1);
        assertThat(claimsOnTheContestedSeat()).singleElement().satisfies(claim -> {
            assertThat(claim.getUserId()).isEqualTo(rival);
            assertThat(claim.getBookingId()).isEqualTo(sold);
        });
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).isEmpty();
    }

    @Test
    void twoSweepersRacingOneEntryPromoteItExactlyOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AtomicReference<Future<Boolean>> second = new AtomicReference<>();
        CountDownLatch secondAtTheLock = new CountDownLatch(1);
        AtomicBoolean firstLock = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (firstLock.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    second.set(pool.submit(() -> writer.promote(entryId, now)));
                    assertThat(secondAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                secondAtTheLock.countDown();
                return real(invocation);
            }).when(waitlist).lockById(entryId);

            assertThat(writer.promote(entryId, now)).isTrue();
            assertThat(second.get().get(30, TimeUnit.SECONDS)).isFalse();
        }

        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.PROMOTED);
        assertThat(claimsOnTheContestedSeat()).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).hasSize(1);
    }

    @Test
    void aPromotionRacingTheStudentsOwnLeaveLeavesNoHoldBehindTheWithdrawnEntry() throws Exception {
        AtomicReference<Future<?>> leaving = new AtomicReference<>();
        CountDownLatch leaveAtTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                leaveAtTheLock.countDown();
                return real(invocation);
            }).when(waitlist).lockByIdAndUserId(entryId, student);
            doAnswer(invocation -> {
                Object locked = real(invocation);
                leaving.set(pool.submit(() -> waitlistService.leave(student, entryId)));
                assertThat(leaveAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                return locked;
            }).when(waitlist).lockById(entryId);

            assertThat(promotion.sweep()).isOne();
            leaving.get().get(30, TimeUnit.SECONDS);
        }

        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(entry().getPromotionHoldId()).isNull();
        assertThat(claimsOnTheContestedSeat()).isEmpty();
    }

    @Test
    void aPromoterDoesNotReclaimASeatAConfirmationHasJustSold() throws Exception {
        UUID holdId = holds.hold(rival, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))).holdId();
        OffsetDateTime later = OffsetDateTime.now().plusMinutes(10);
        AtomicBoolean firstRead = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                Object onSeats = real(invocation);
                if (firstRead.compareAndSet(true, false)) {
                    pool.submit(() -> bookingService.create(rival, "key-promorace-reclaim",
                            new CreateBookingRequest(holdId, "Somchai P.", "0812345678")))
                            .get(10, TimeUnit.SECONDS);
                }
                return onSeats;
            }).when(claims).findBySeatIdIn(List.of(contestedSeat));

            assertThatThrownBy(() -> writer.promote(entryId, later)).hasMessageContaining("lost the race");
        }

        UUID sold = bookings.findByUserIdOrderByCreatedAtDesc(rival).getFirst().getId();
        assertThat(claims.findByBookingId(sold)).hasSize(1);
        assertThat(claimsOnTheContestedSeat()).singleElement().satisfies(claim -> {
            assertThat(claim.getUserId()).isEqualTo(rival);
            assertThat(claim.getBookingId()).isEqualTo(sold);
        });
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(outboxOfType(OutboxEventType.WAITLIST_PROMOTED)).isEmpty();
    }

    private void releaseTheRivalOnceThePromoterHasReadAvailability(ExecutorService pool, Callable<?> rivalAction) {
        AtomicBoolean firstRead = new AtomicBoolean(true);
        doAnswer(invocation -> {
            Object free = real(invocation);
            if (firstRead.compareAndSet(true, false)) {
                pool.submit(rivalAction).get(10, TimeUnit.SECONDS);
            }
            return free;
        }).when(availability).freeSeatsOf(any(Trip.class), any(OffsetDateTime.class));
    }

    private UUID book(UUID userId, String idempotencyKey) {
        UUID holdId = holds.hold(userId, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))).holdId();
        bookingService.create(userId, idempotencyKey,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).getFirst().getId();
    }

    private WaitlistEntry entry() {
        return waitlist.findById(entryId).orElseThrow();
    }

    private List<SeatClaim> claimsOnTheContestedSeat() {
        return claims.findAll().stream()
                .filter(claim -> claim.getTripSeat().getId().equals(contestedSeat))
                .toList();
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Promotion race layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PROMORACE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
