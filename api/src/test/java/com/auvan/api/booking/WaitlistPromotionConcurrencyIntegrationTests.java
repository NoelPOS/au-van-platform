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

/**
 * The highest-risk part of #69: what happens when the promotion sweep and
 * somebody else reach the same seat, or the same entry, at the same moment.
 * Acceptance criterion 3 is the one on trial — a promotion must never oversell
 * a seat, never free a seat somebody has paid for, and never leave a hold
 * standing behind an entry that has ended.
 *
 * <p>Like the four concurrency classes before it, this one must
 * <strong>not</strong> be {@code @Transactional}: a test-managed transaction
 * would put both threads on one connection and there would be no race left to
 * observe.
 *
 * <p>Each test lets its rival commit while the promoter still believes what it
 * read a moment earlier — the interleaving a real race produces only sometimes,
 * made to happen every run. The stubs control timing only; the production path
 * runs in both threads.
 *
 * <p>The entries are written through the repository rather than through
 * {@code WaitlistService.join}, because joining a trip with a free seat is
 * refused and every test here needs both a queued student and a free seat.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour.
 * Coverage against real PostgreSQL belongs to issue #10.
 */
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

    // Spies, not mocks: every call runs for real, and the stubs exist only to
    // decide when the rival gets to commit.
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
        // The second seat stays claimed throughout, so the only seat any of
        // these tests can promote onto is the contested one.
        claims.save(new SeatClaim(trip.getSeats().get(1), holder, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(2)));
        entryId = waitlist.save(new WaitlistEntry(trip, student, 1, OffsetDateTime.now())).getId();
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

    /**
     * A student takes the seat through the ordinary hold path while the
     * promoter is between reading availability and inserting its claim.
     *
     * <p>{@code seat_claims_trip_seat_unique} decides it, exactly as it decides
     * two students racing for a seat today, and the promoter losing is an
     * ordinary outcome rather than an error: the entry is left alone for the
     * next sweep.
     */
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
        // And the next sweep does not hand the seat out a second time: it is
        // somebody else's now, so there is nothing free to promote onto.
        assertThat(promotion.sweep()).isZero();
        assertThat(entry().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(claimsOnTheContestedSeat()).hasSize(1);
    }

    /**
     * The seat was freed by a cancellation and two parties want it: the
     * promoter, and a student who is confirming a hold on it. The promoter must
     * never attach itself to a seat {@code BookingWriter.create} is in the
     * middle of claiming.
     */
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

    /**
     * Two instances sweep at once and both name the same entry. Exactly one
     * promotes; the other reads {@code PROMOTED} from behind the lock and does
     * nothing.
     *
     * <p>Remove {@code @Lock(PESSIMISTIC_WRITE)} from
     * {@code WaitlistEntryRepository.lockById} and the second sweeper reads
     * {@code WAITING} from under the first's uncommitted promotion, tries to
     * take the same seat, and fails on the unique constraint instead of
     * returning {@code false}.
     */
    @Test
    void twoSweepersRacingOneEntryPromoteItExactlyOnce() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        AtomicReference<Future<Boolean>> second = new AtomicReference<>();
        CountDownLatch secondAtTheLock = new CountDownLatch(1);
        AtomicBoolean firstLock = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            // Both promoters lock through lockById. The first call is this
            // thread's; the second is the pool's, and it waits on the row until
            // this thread commits.
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

    /**
     * The student leaves the queue in the same instant the sweep gives them a
     * seat. Their {@code leave} waits on the entry's row, reads the promotion
     * the sweep just committed, and gives the seat back — an entry that has
     * ended must not leave a hold standing behind it.
     */
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

    /**
     * The promoter planned to reclaim a lapsed hold and a confirmation sold
     * that very row in between.
     *
     * <p>{@code SeatClaimRepository.deleteByIdIn} matches on
     * {@code booking_id is null}, so the delete simply frees nothing and the
     * insert that follows loses to the unique constraint. Widen that guard and
     * the promoter deletes a seat somebody has paid for, which this test is the
     * only thing that would notice.
     *
     * <p>The promoter is swept for ten minutes from now: that is how a real
     * promoter comes to plan this reclaim at all — it reads one {@code now},
     * and the confirmation it is racing started while the hold was still live.
     */
    @Test
    void aPromoterDoesNotReclaimASeatAConfirmationHasJustSold() throws Exception {
        UUID holdId = holds.hold(rival, new CreateSeatHoldRequest(trip.getId(), List.of(contestedSeat))).holdId();
        OffsetDateTime later = OffsetDateTime.now().plusMinutes(10);
        AtomicBoolean firstRead = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            // The rival commits after the promoter has read the row it means to
            // reclaim and before it issues the delete.
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

    // Fixtures

    /**
     * Lets the rival take the contested seat, and commit, while the promoter
     * still holds the availability reading that said the seat was free.
     */
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

    /**
     * Runs the call the stub intercepted for real.
     *
     * <p>{@code invocation.callRealMethod()} cannot do this for a Spring Data
     * repository: the method is an interface method with no body, and the spy
     * keeps the actual repository in its default answer rather than as a spied
     * instance.
     */
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
