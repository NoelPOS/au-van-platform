package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatHoldResponse;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.SeatClaimRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

/**
 * Concurrency cover for seat holds. This class must not be {@code @Transactional}:
 * a test-managed transaction would put every thread on one connection and there
 * would be no race left to observe.
 *
 * <p>The racing test shows that eight students claiming one seat at the same
 * instant leave exactly one claim behind, but it cannot say which guard stopped
 * the losers: the optimistic read in {@link SeatHoldService} catches whichever
 * threads arrive after the winner has committed, and in practice that is most of
 * them. The three tests after it therefore stub one repository lookup each, so
 * that the interleaving a race only sometimes produces happens every time — the
 * rival landing after the check, two students reclaiming the same expired claim,
 * and the bare constraint itself.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour, and the
 * racing test cannot pin the losers' HTTP status either: H2 surfaces unique-key
 * contention as a constraint violation or as a lock timeout depending on timing,
 * and only the first becomes a 409. {@link SeatHoldConcurrencyPostgresTests} runs
 * the same race against a real PostgreSQL 17, where unique-key contention is always
 * a unique violation, and does assert every loser's status and code. It stays a
 * suite of its own — tagged {@code postgres} and excluded from
 * {@code ./gradlew test} — because putting Testcontainers here would put Docker on
 * the critical path of every build.
 */
@SpringBootTest
class SeatHoldConcurrencyIntegrationTests extends AuthenticationTestSupport {
    private static final int CONTENDERS = 8;

    @Autowired
    private SeatHoldService seatHoldService;

    // A spy, not a mock: every call runs for real unless a test stubs the one
    // lookup whose timing it needs to control.
    @MockitoSpyBean
    private SeatClaimRepository claims;

    @Autowired
    private TransactionTemplate transactions;

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
    private List<UUID> students;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        seats = trip.getSeats();
        students = new ArrayList<>();
        for (int index = 0; index < CONTENDERS; index++) {
            students.add(users.save(new AppUser("Ustudent-" + index, "Student " + index)).getId());
        }
    }

    @AfterEach
    void clearData() {
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void onlyOneOfEightSimultaneousStudentsEndsUpHoldingTheSeat() throws Exception {
        UUID contendedSeat = seats.getFirst().getId();
        CountDownLatch ready = new CountDownLatch(CONTENDERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(CONTENDERS);
        AtomicInteger succeeded = new AtomicInteger();
        Queue<Exception> losses = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS)) {
            for (int index = 0; index < CONTENDERS; index++) {
                UUID student = students.get(index);
                UUID ownSeat = seats.get(index + 1).getId();
                pool.execute(() -> {
                    try {
                        // Warm the whole path on a seat nobody else wants, so the
                        // contended attempt is not competing with class loading,
                        // JIT, and connection setup on other threads.
                        warmUp(student, ownSeat);
                        ready.countDown();
                        start.await();
                        seatHoldService.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(contendedSeat)));
                        succeeded.incrementAndGet();
                    } catch (Exception expectedForLosers) {
                        losses.add(expectedForLosers);
                    } finally {
                        finished.countDown();
                    }
                });
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(succeeded.get()).isOne();
        assertThat(claims.findBySeatIdIn(List.of(contendedSeat)))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getTripSeat().getId()).isEqualTo(contendedSeat));
        // Losers stopped by either guard must have been told the same thing. A loser
        // that H2 failed some other way is not a ResponseStatusException at all, and
        // is out of this test's reach; the stubbed test below covers that branch.
        assertThat(losses).hasSize(CONTENDERS - 1);
        assertThat(losses)
                .filteredOn(ResponseStatusException.class::isInstance)
                .allSatisfy(loss -> assertThat(((ResponseStatusException) loss).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    /**
     * Reaches the one branch a race cannot be relied on to reach: the rival claim
     * lands after the optimistic read has already found the seat free, so only the
     * unique index can stop the insert. Stubbing the read is what makes the timing
     * certain instead of lucky.
     */
    @Test
    void aClaimThatLandsAfterTheCheckIsCaughtByTheConstraintAndReportedAsAConflict() {
        TripSeat seat = seats.getFirst();
        UUID latecomer = students.get(0);
        UUID rival = students.get(1);
        // The rival commits while the check is in flight. Returning an empty list is
        // exactly what the real query would have returned a moment earlier, so the
        // service proceeds on a true-but-already-stale reading of the seat.
        doAnswer(invocation -> {
            claimSeatOnAnotherThread(rival, seat);
            return List.of();
        }).when(claims).findBySeatIdIn(List.of(seat.getId()));

        assertThatThrownBy(() -> seatHoldService.hold(latecomer,
                new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getReason()).contains("refresh");
                });

        assertThat(claims.findByTripIdIn(List.of(trip.getId())))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(rival));
    }

    /**
     * A seat freed by expiry is the contended case, because every student watching
     * the seat map sees it come free at the same moment. Both readers then try to
     * delete the same expired row. The loser must be told to refresh, not handed a
     * 500: deleting it as a managed entity would make Hibernate's row-count check
     * raise a {@code StaleStateException}, which is not a
     * {@code DataIntegrityViolationException} and escapes the handler.
     */
    @Test
    void losingTheRaceToReclaimAnExpiredClaimIsAConflictRatherThanACrash() {
        TripSeat seat = seats.getFirst();
        UUID latecomer = students.get(0);
        UUID rival = students.get(1);
        SeatClaim expired = claims.saveAndFlush(
                new SeatClaim(seat, students.get(2), UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1)));

        // The latecomer has already read the expired claim for real by the time this
        // runs, so it is holding a managed entity whose row the rival is about to
        // delete out from under it.
        doAnswer(invocation -> {
            reclaimOnAnotherThread(rival, seat, expired.getId());
            return List.of();
        }).when(claims).findHoldsOnTripBy(trip.getId(), latecomer);

        assertThatThrownBy(() -> seatHoldService.hold(latecomer,
                new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getReason()).contains("refresh");
                });

        assertThat(claims.findByTripIdIn(List.of(trip.getId())))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(rival));
    }

    @Test
    void theUniqueConstraintRefusesASecondClaimOnTheSameSeat() {
        TripSeat seat = seats.getFirst();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(5);
        claims.saveAndFlush(new SeatClaim(seat, students.get(0), UUID.randomUUID(), expiresAt));

        assertThatThrownBy(() -> claims.saveAndFlush(
                new SeatClaim(seat, students.get(1), UUID.randomUUID(), expiresAt)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Reclaims an expired claim and takes its seat, from outside the caller's transaction. */
    private void reclaimOnAnotherThread(UUID student, TripSeat seat, UUID expiredClaimId) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> transactions.executeWithoutResult(status -> {
                claims.deleteByIdIn(List.of(expiredClaimId));
                claims.save(new SeatClaim(seat, student, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(5)));
            })).get(10, TimeUnit.SECONDS);
        }
    }

    /** Commits a rival claim from outside the caller's transaction, and waits for it. */
    private void claimSeatOnAnotherThread(UUID student, TripSeat seat) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> transactions.executeWithoutResult(status -> claims.save(
                            new SeatClaim(seat, student, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(5)))))
                    .get(10, TimeUnit.SECONDS);
        }
    }

    /** Holds and immediately releases a seat of this thread's own, leaving no claim behind. */
    private void warmUp(UUID student, UUID ownSeat) {
        SeatHoldResponse hold = seatHoldService.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(ownSeat)));
        seatHoldService.release(student, hold.holdId());
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= CONTENDERS + 1; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-RACE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
