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

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrency cover for seat holds. This class must not be {@code @Transactional}:
 * a test-managed transaction would put every thread on one connection and there
 * would be no race left to observe.
 *
 * <p>Read these two tests together. The racing test shows that eight students
 * claiming one seat at the same instant leave exactly one claim behind, but it
 * cannot say which guard stopped the losers — the optimistic read in
 * {@link SeatHoldService} catches whichever threads arrive after the winner has
 * committed, and only the rest reach the unique index. The second test pins that
 * backstop directly and deterministically.
 *
 * <p>Both run on H2, so neither proves PostgreSQL's behaviour, and the racing
 * test cannot pin the losers' HTTP status either: H2 surfaces unique-key
 * contention as a constraint violation or as a lock timeout depending on
 * timing, and only the first becomes a 409. The sequential test in
 * {@code SeatHoldIntegrationTests} is what pins the 409 and its wording.
 * Concurrency coverage against real PostgreSQL belongs to issue #10; putting
 * Testcontainers here would put Docker on the critical path of every build.
 */
@SpringBootTest
class SeatHoldConcurrencyIntegrationTests extends AuthenticationTestSupport {
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
                        // Losers fail either on the optimistic read or on the unique
                        // index, and the exception differs between the two, so the
                        // assertions below count winners and rows instead.
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
