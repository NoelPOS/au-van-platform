package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.IdempotencyService;
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
import org.mockito.invocation.InvocationOnMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Concurrency cover for booking creation. This class must not be
 * {@code @Transactional}: a test-managed transaction would put every thread on
 * one connection and there would be no race left to observe.
 *
 * <p>Each test stubs exactly one repository call so that an interleaving a real
 * race only sometimes produces happens every time. The stub controls timing
 * only; the code under test is the production path in both threads. Statuses
 * are deliberately not asserted over HTTP — the existing seat-hold concurrency
 * class explains why — so these call the services directly and assert on
 * exceptions and rows.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour.
 * Concurrency coverage against real PostgreSQL belongs to issue #10.
 */
@SpringBootTest
class BookingConcurrencyIntegrationTests extends AuthenticationTestSupport {
    private static final String ENDPOINT = "POST /api/v1/bookings";

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHoldService;

    @Autowired
    private IdempotencyService idempotency;

    // Spies, not mocks: every call runs for real unless a test stubs the one
    // call whose timing it needs to control.
    @MockitoSpyBean
    private SeatClaimRepository claims;

    @MockitoSpyBean
    private BookingRepository bookings;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

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
    private UUID student;
    private UUID rival;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        seats = trip.getSeats();
        student = users.save(new AppUser("Ustudent", "Student")).getId();
        rival = users.save(new AppUser("Urival", "Rival")).getId();
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
     * The row lock's whole purpose. Two confirmations of one hold both only
     * {@code UPDATE} rows that already exist, so {@code seat_claims_trip_seat_unique}
     * is satisfied by each of them and nothing in the schema objects to two
     * bookings sharing a seat. The second confirmation has to read the claims
     * <em>after</em> the first has committed, which is what the lock forces.
     *
     * <p>The rival is released one statement short of its own locking read, so
     * it is at the lock while the winner is still inside its transaction.
     */
    @Test
    void theSecondConfirmationOfOneHoldIsRefusedAndOnlyOneBookingExists() throws Exception {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<IdempotencyService.StoredResponse>> loser = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    loser.set(pool.submit(() -> bookingService.create(student, "rival-key", request(holdId))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(claims).lockByHoldId(holdId);

            bookingService.create(student, "student-key", request(holdId));

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("hold_already_used");
                    });
        }

        assertThat(bookings.count()).isOne();
        assertThat(claims.findByHoldId(holdId))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isEqualTo(bookings.findAll().getFirst().getId()));
    }

    /**
     * A reclaim must not free a seat that has just been sold.
     *
     * <p>Re-selecting seats deletes the caller's existing holds on the trip, and
     * it chooses them from a read taken earlier in the transaction. A
     * confirmation that commits in between turns one of those rows into a booked
     * seat, and the delete would still match it by id. The row lock does not
     * help: the reclaimer simply waits and then deletes the sold row.
     * {@code booking_id is null} in the delete is what refuses.
     */
    @Test
    void reSelectingSeatsCannotFreeAClaimThatHasJustBeenBooked() {
        TripSeat booked = seats.get(0);
        TripSeat wanted = seats.get(1);
        UUID holdId = holdOn(student, booked);
        AtomicReference<UUID> bookingId = new AtomicReference<>();
        // A true reading, taken while the claim really was an unbooked hold. It
        // is what the reclaim would have read, and it is stale by the time the
        // delete it feeds runs.
        List<SeatClaim> mine = claims.findHoldsOnTripBy(trip.getId(), student);

        doAnswer(invocation -> {
            bookingId.set(confirmOnAnotherThread(holdId));
            return mine;
        }).when(claims).findHoldsOnTripBy(trip.getId(), student);

        seatHoldService.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(wanted.getId())));

        assertThat(claims.findByBookingId(bookingId.get()))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getTripSeat().getId()).isEqualTo(booked.getId()));
        assertThat(claims.count()).isEqualTo(2);
    }

    /**
     * Two copies of one request in flight at once. The loser is refused by the
     * hold's row lock, not by the idempotency record, so what makes this a
     * replay rather than a 409 is the orchestrator asking the store again after
     * the write failed.
     */
    @Test
    void aDuplicateInFlightRequestIsAnsweredWithTheResponseTheWinnerStored() throws Exception {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<IdempotencyService.StoredResponse>> duplicate = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    duplicate.set(pool.submit(() -> bookingService.create(student, "one-key", request(holdId))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(claims).lockByHoldId(holdId);

            IdempotencyService.StoredResponse sent = bookingService.create(student, "one-key", request(holdId));

            assertThat(duplicate.get().get(30, TimeUnit.SECONDS)).isEqualTo(sent);
        }

        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isOne();
    }

    /**
     * A booking-side integrity violation has to be reported as its own conflict.
     * In practice that means a reference collision, which is what the rival
     * takes here between the reference being generated and the insert reaching
     * the database.
     *
     * <p>This is what the flush after {@code bookings.save} decides. Without it
     * the inserts defer until the idempotency record is flushed, and the
     * booking's own violation would be caught — and mislabelled — there.
     */
    @Test
    void losingTheRaceForAReferenceIsAConflictAndWritesNothing() {
        UUID holdId = holdOn(student, seats.getFirst());
        AtomicBoolean first = new AtomicBoolean(true);

        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                takeReferenceOnAnotherThread(invocation.<Booking>getArgument(0).getReference());
            }
            return real(invocation);
        }).when(bookings).save(any(Booking.class));

        assertThatThrownBy(() -> bookingService.create(student, "student-key", request(holdId)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(codeOf(conflict)).isEqualTo("booking_creation_conflict");
                });

        // Only the rival's booking exists, no response was stored, and the hold
        // is still a hold: the whole transaction went back.
        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isZero();
        assertThat(claims.findByHoldId(holdId))
                .singleElement()
                .satisfies(claim -> assertThat(claim.getBookingId()).isNull());
    }

    /**
     * A replay is answered from the record alone and never reads the hold.
     *
     * <p>The pre-check is what guarantees that. Without it a retry would go
     * through the write path, take a row lock on a hold the first request has
     * already consumed, and be answered only because that write failed — a
     * replay would then depend on an error rather than on the record.
     */
    @Test
    void aReplayIsAnsweredWithoutTouchingTheHold() {
        UUID holdId = holdOn(student, seats.getFirst());
        IdempotencyService.StoredResponse sent = bookingService.create(student, "one-key", request(holdId));
        clearInvocations(claims);

        assertThat(bookingService.create(student, "one-key", request(holdId))).isEqualTo(sent);

        verify(claims, never()).lockByHoldId(any());
    }

    /**
     * Two racing requests can both find no record for their key and both go on
     * to store one, so the unique constraint on
     * {@code (user_id, endpoint, idempotency_key)} is the last word. The loser
     * has to be told so as a conflict, which it can only be because the insert
     * is flushed inside the {@code try}: left to the commit, the violation
     * would be raised after every catch block had gone out of scope.
     */
    @Test
    void aSecondRecordUnderOneKeyIsAConflictRatherThanAFailureAtCommit() {
        OffsetDateTime now = OffsetDateTime.now();
        transactions.executeWithoutResult(status ->
                idempotency.record(student, ENDPOINT, "one-key", "hash-of-the-first", 201, "first", now));

        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                idempotency.record(student, ENDPOINT, "one-key", "hash-of-the-second", 201, "second", now)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(codeOf(conflict)).isEqualTo("idempotency_conflict");
                });

        assertThat(idempotencyKeys.count()).isOne();
    }

    // Fixtures

    /**
     * Runs the call the stub intercepted for real.
     *
     * <p>{@code invocation.callRealMethod()} cannot do this for a Spring Data
     * repository: the method is an interface method with no body, and the spy
     * keeps the actual repository in its default answer rather than as a spied
     * instance. Forwarding through that answer is how a stub that needs the
     * real result — a locking read returning managed rows, say — gets one.
     */
    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private static Object codeOf(ResponseStatusException problem) {
        return problem.getBody().getProperties().get("code");
    }

    private static CreateBookingRequest request(UUID holdId) {
        return new CreateBookingRequest(holdId, "Somchai P.", "0812345678");
    }

    private UUID holdOn(UUID owner, TripSeat seat) {
        return seatHoldService.hold(owner, new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
    }

    /** Confirms the hold from outside the caller's transaction, and waits for it. */
    private UUID confirmOnAnotherThread(UUID holdId) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> bookingService.create(student, "other-thread-key", request(holdId)))
                    .get(30, TimeUnit.SECONDS);
        }
        return bookings.findAll().getFirst().getId();
    }

    /** Commits a booking on the reference the caller is about to insert. */
    private void takeReferenceOnAnotherThread(String reference) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(() -> transactions.executeWithoutResult(status -> bookings.save(new Booking(
                            trip, rival, reference, "Rival Student", "0800000000",
                            new BigDecimal("35.00"), OffsetDateTime.now()))))
                    .get(30, TimeUnit.SECONDS);
        }
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-BOOK", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
