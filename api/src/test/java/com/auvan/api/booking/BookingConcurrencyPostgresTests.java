package com.auvan.api.booking;

import com.auvan.api.PostgresTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

/**
 * The two booking guards that only a real PostgreSQL can be said to prove, and
 * that {@link BookingConcurrencyIntegrationTests}' own Javadoc says it cannot.
 *
 * <p>The first is {@code SELECT … FOR UPDATE}. Two confirmations of one hold both
 * merely {@code UPDATE} rows that already exist, so
 * {@code seat_claims_trip_seat_unique} is satisfied by each of them and nothing in
 * the schema objects to two bookings sharing a seat. What refuses the second one is
 * PostgreSQL's READ COMMITTED behaviour under a row lock: the loser blocks on the
 * winner's lock and, once it is granted, reads the <em>committed</em> row rather
 * than the version its own snapshot began with. That re-read is the entire guard,
 * and no assertion on H2 has ever exercised it.
 *
 * <p>The second is {@code booking_id is null} in {@code deleteByIdIn}, under real
 * MVCC: a reclaim that selected its rows before a confirmation committed must
 * refuse to free the seat that confirmation sold.
 *
 * <p>Must not be {@code @Transactional}: a test-managed transaction would put every
 * thread on one connection and there would be no race left to observe.
 */
@SpringBootTest
class BookingConcurrencyPostgresTests extends PostgresTestSupport {
    private static final int CONTENDERS = 8;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    // A spy, not a mock: every call runs for real unless a test stubs the one
    // lookup whose timing it needs to control.
    @MockitoSpyBean
    private SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

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
    private List<TripSeat> seats;
    private UUID student;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        seats = trip.getSeats();
        student = users.save(new AppUser("Upg-booking", "Student")).getId();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
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
     * Eight simultaneous confirmations of one hold. Exactly one booking exists
     * afterwards and the other seven are refused with the documented code.
     *
     * <p>No stubbing and no latch beyond the starting gun: the lock itself makes
     * this deterministic. Seven threads queue on the winner's row lock, and each is
     * granted it only after the winner has committed, at which point the re-read
     * shows a claim that already carries a booking. Take
     * {@code @Lock(LockModeType.PESSIMISTIC_WRITE)} off
     * {@code SeatClaimRepository.lockByHoldId} and the reads stop queueing: several
     * threads read the same unbooked claim, each writes its own booking, and
     * {@code booking_seats_seat_unique} is no help because it is scoped per booking.
     */
    @Test
    void eightSimultaneousConfirmationsOfOneHoldLeaveExactlyOneBooking() throws Exception {
        UUID holdId = seatHoldService
                .hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(seats.getFirst().getId())))
                .holdId();
        CountDownLatch ready = new CountDownLatch(CONTENDERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONTENDERS);
        AtomicInteger confirmed = new AtomicInteger();
        Queue<Exception> refusals = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            for (int index = 0; index < CONTENDERS; index++) {
                // A key of its own each, so nothing here is answered from the
                // idempotency record: every thread reaches the hold's row lock.
                String key = "pg-confirm-" + index;
                pool.execute(() -> {
                    try {
                        ready.countDown();
                        start.await();
                        bookingService.create(student, key, request(holdId));
                        confirmed.incrementAndGet();
                    } catch (Exception expectedForLosers) {
                        refusals.add(expectedForLosers);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(finished.await(60, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(confirmed.get()).isOne();
        assertThat(bookings.count()).isOne();
        assertThat(claims.findByHoldId(holdId))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isEqualTo(bookings.findAll().getFirst().getId()));
        assertThat(refusals).hasSize(CONTENDERS - 1);
        assertThat(refusals).allSatisfy(refusal -> assertThat(refusal)
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getBody().getProperties().get("code")).isEqualTo("hold_already_used");
                }));
    }

    /**
     * A reclaim must not free a seat that has just been sold.
     *
     * <p>Re-selecting seats deletes the caller's existing holds on the trip, chosen
     * from a read taken earlier in the transaction. A confirmation that commits in
     * between turns one of those rows into a booked seat, and the delete would still
     * match it by id. The row lock does not help — the reclaimer simply waits and
     * then deletes the sold row — so {@code booking_id is null} in the delete is the
     * only thing that refuses, and here it is refusing against real MVCC rather than
     * an emulation of it.
     */
    @Test
    void reSelectingSeatsCannotFreeAClaimSoldBetweenTheCandidateReadAndTheDelete() {
        TripSeat sold = seats.get(0);
        TripSeat wanted = seats.get(1);
        UUID holdId = seatHoldService
                .hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(sold.getId()))).holdId();
        AtomicReference<UUID> bookingId = new AtomicReference<>();
        // A true reading, taken while the claim really was an unbooked hold. It is
        // what the reclaim would have read, and it is stale by the time the delete
        // it feeds runs.
        List<SeatClaim> mine = claims.findHoldsOnTripBy(trip.getId(), student);

        doAnswer(invocation -> {
            bookingId.set(confirmOnAnotherThread(holdId));
            return mine;
        }).when(claims).findHoldsOnTripBy(trip.getId(), student);

        seatHoldService.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(wanted.getId())));

        assertThat(claims.findByBookingId(bookingId.get()))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getTripSeat().getId()).isEqualTo(sold.getId()));
        assertThat(bookings.count()).isOne();
        assertThat(claims.count()).isEqualTo(2);
    }

    // Fixtures

    private static CreateBookingRequest request(UUID holdId) {
        return new CreateBookingRequest(holdId, "Somchai P.", "0812345678");
    }

    /** Confirms the hold from outside the caller's transaction, and waits for it. */
    private UUID confirmOnAnotherThread(UUID holdId) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> bookingService.create(student, "other-thread-key", request(holdId)))
                    .get(30, TimeUnit.SECONDS);
        }
        return bookings.findAll().getFirst().getId();
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PGBOOK", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
