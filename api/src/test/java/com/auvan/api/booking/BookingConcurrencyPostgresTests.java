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
import org.mockito.invocation.InvocationOnMock;
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
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
class BookingConcurrencyPostgresTests extends PostgresTestSupport {
    private static final int CONTENDERS = 8;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    @MockitoSpyBean
    private SeatClaimRepository claims;

    @MockitoSpyBean
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
        trip = createTrip("VAN-PGBOOK");
        seats = trip.getSeats();
        student = users.save(new AppUser("Upg-booking", "Student")).getId();
    }

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

    @Test
    void reSelectingSeatsCannotFreeAClaimSoldBetweenTheCandidateReadAndTheDelete() {
        TripSeat sold = seats.get(0);
        TripSeat wanted = seats.get(1);
        UUID holdId = seatHoldService
                .hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(sold.getId()))).holdId();
        AtomicReference<UUID> bookingId = new AtomicReference<>();
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

    @Test
    void twoHoldsOnDifferentTripsBookedAtOnceLeaveOnlyOneUnpaidBooking() throws Exception {
        UUID firstHold = holdOn(trip);
        UUID secondHold = holdOn(createTrip("VAN-PGBOOK-2"));
        CyclicBarrier bothChecking = new CyclicBarrier(2);
        doAnswer(invocation -> {
            try {
                bothChecking.await(3, TimeUnit.SECONDS);
            } catch (BrokenBarrierException | TimeoutException serialisedByTheLock) {
                // The lock keeps the second booking out of the check, so the barrier never fills.
            }
            return real(invocation);
        }).when(bookings).findFirstByUserIdAndStatusInOrderByCreatedAtAsc(eq(student), any());
        Queue<Exception> refusals = new ConcurrentLinkedQueue<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (UUID holdId : List.of(firstHold, secondHold)) {
                pool.execute(() -> {
                    try {
                        bookingService.create(student, "pg-unpaid-" + holdId, request(holdId));
                    } catch (Exception expectedForTheLoser) {
                        refusals.add(expectedForTheLoser);
                    }
                });
            }
        }

        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(student)).hasSize(1);
        assertThat(refusals).singleElement().isInstanceOfSatisfying(ResponseStatusException.class,
                refusal -> assertThat(refusal.getBody().getProperties().get("code"))
                        .isEqualTo("unpaid_booking_exists"));
    }

    private UUID holdOn(Trip target) {
        return seatHoldService.hold(student,
                new CreateSeatHoldRequest(target.getId(), List.of(target.getSeats().getFirst().getId()))).holdId();
    }

    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private static CreateBookingRequest request(UUID holdId) {
        return new CreateBookingRequest(holdId, "Somchai P.", "0812345678");
    }

    private UUID confirmOnAnotherThread(UUID holdId) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> bookingService.create(student, "other-thread-key", request(holdId)))
                    .get(30, TimeUnit.SECONDS);
        }
        return bookings.findAll().getFirst().getId();
    }

    private Trip createTrip(String vehicleCode) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
