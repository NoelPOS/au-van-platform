package com.auvan.api.booking;

import com.auvan.api.PostgresTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatHoldResponse;
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
import org.springframework.http.HttpStatus;
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

/**
 * The seat-hold race, on the engine that actually decides it.
 *
 * <p>{@link SeatHoldConcurrencyIntegrationTests} runs the same race on H2 and
 * keeps its fast feedback, but its own Javadoc records what it cannot do: H2
 * "surfaces unique-key contention as a constraint violation or as a lock timeout
 * depending on timing, and only the first becomes a 409", so it can only assert
 * the status of whichever losers happened to fail the first way. PostgreSQL has
 * no such ambiguity. A second inserter on {@code seat_claims_trip_seat_unique}
 * waits for the winner to commit and is then refused with a unique violation —
 * always that, never a lock timeout — so <em>every</em> loser here must arrive at
 * the same 409 and the same code, and this class asserts it for all seven.
 *
 * <p>That is the behavioural gain over the H2 class, not a re-run of it.
 *
 * <p>Must not be {@code @Transactional}, for the reason every concurrency class
 * here gives: a test-managed transaction would put all eight threads on one
 * connection and there would be no race left to observe.
 */
@SpringBootTest
class SeatHoldConcurrencyPostgresTests extends PostgresTestSupport {
    private static final int CONTENDERS = 8;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private SeatClaimRepository claims;

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
            students.add(users.save(new AppUser("Upg-hold-" + index, "Student " + index)).getId());
        }
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    /**
     * Eight students, one seat, one winner — and seven refusals that are all the
     * same refusal.
     *
     * <p>The losers arrive by two routes: the optimistic read in
     * {@link SeatHoldService} catches those that start after the winner has
     * committed, and {@code seat_claims_trip_seat_unique} catches the rest at the
     * flush. Both are translated to {@code seat_taken}, and on PostgreSQL both are
     * reached deterministically, so the assertion covers all seven rather than the
     * subset the H2 class has to settle for.
     */
    @Test
    void eightStudentsRacingForOneSeatLeaveOneHoldAndSevenIdenticalConflicts() throws Exception {
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
        // Every loser, not merely the ones that failed a particular way. This is
        // the line the H2 class cannot write.
        assertThat(losses).hasSize(CONTENDERS - 1);
        assertThat(losses).allSatisfy(loss -> assertThat(loss)
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getBody().getProperties().get("code")).isEqualTo("seat_taken");
                }));
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
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PGHOLD", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
