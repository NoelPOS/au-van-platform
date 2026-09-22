package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofReviewService;
import com.auvan.api.booking.service.PaymentProofService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * Concurrency cover for a payment proof's whole life: the student's
 * submission, the administrator's decision, and a decision racing the
 * student's own cancellation. Like the two concurrency
 * classes before it, this one must not be {@code @Transactional}: a
 * test-managed transaction would put both threads on one connection and there
 * would be no race left to observe. Statuses are asserted from the thrown
 * {@link ResponseStatusException} rather than over HTTP, for the reason
 * {@link SeatHoldConcurrencyIntegrationTests} gives.
 *
 * <p>The test stubs the one repository call whose timing it needs to control,
 * so the interleaving a real race only sometimes produces happens every time.
 * The stub controls timing only; the production path runs in both threads.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour.
 * The {@code SELECT … FOR UPDATE} these paths decide behind is the same lock
 * {@link BookingConcurrencyPostgresTests} proves against a real PostgreSQL 17, in a
 * suite tagged {@code postgres} and excluded from {@code ./gradlew test}. These
 * particular races are deliberately not re-run there: the tagged suite asserts one
 * property per engine-specific guarantee rather than every class twice, which is
 * what keeps it short enough to be worth running on every pull request.
 */
@SpringBootTest
class PaymentProofConcurrencyIntegrationTests extends AuthenticationTestSupport {
    /** The port's in-memory side, exactly as {@link PaymentProofIntegrationTests} registers it. */
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    // A spy, not a mock: every call runs for real except the one the test stubs.
    @MockitoSpyBean
    private BookingRepository bookings;

    @Autowired
    private PaymentProofRepository proofs;

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

    private UUID student;
    private UUID administrator;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        clearData();
        storage.reset();
        Trip trip = createTrip();
        student = users.save(new AppUser("Ustudent", "Student")).getId();
        administrator = users.save(new AppUser("Uadmin", "Administrator")).getId();
        Booking booking = new Booking(trip, student, "AUV-250101-PROOFRAC", "Somchai P.",
                "0812345678", new BigDecimal("35.00"), OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now());
        TripSeat seat = trip.getSeats().getFirst();
        booking.addSeat(seat);
        bookingId = bookings.save(booking).getId();
        // The claim the cancellation frees, so the approve-versus-cancel race
        // can assert what happened to the seats and not only to the status.
        SeatClaim claim = new SeatClaim(seat, student, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(10));
        claim.attachTo(bookingId);
        claims.save(claim);
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        proofs.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    /**
     * The row lock's whole purpose here. The eligibility check reads
     * {@code status} and the submission writes it, and {@code payment_proofs}
     * constrains only {@code object_key} — a fresh UUID per submission, so it
     * never collides. Two submissions in flight at once would both read
     * {@code PENDING_PAYMENT}, both pass the check, and both insert; the second
     * has to read the booking <em>after</em> the first has committed, which is
     * what the lock forces.
     *
     * <p>The rival is released one statement short of its own locking read, so
     * it is at the lock while the winner is still inside its transaction. This
     * is the retry the client fires before the first response arrives — the
     * case a {@code 409} on an already-committed submission cannot cover.
     */
    @Test
    void aSecondSubmissionInFlightIsRefusedAndOnlyOneProofExists() throws Exception {
        AtomicBoolean winner = new AtomicBoolean(true);
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                if (winner.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    loser.set(pool.submit(() -> paymentProofs.submit(student, bookingId, jpeg("the-retry"))));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                atTheLock.countDown();
                return real(invocation);
            }).when(bookings).lockByIdAndUserId(bookingId, student);

            assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                    .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("booking_not_awaiting_payment");
                    });
        }

        // One row, one object, and one transition: the loser wrote nothing.
        assertThat(proofs.count()).isOne();
        assertThat(storage.objects()).hasSize(1);
        assertThat(storage.objects().values()).singleElement()
                .satisfies(object -> assertThat(object.content())
                        .isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8)));
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus())
                        .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW));
    }

    /**
     * The same race on the review side, and the reason
     * {@link com.auvan.api.booking.repository.PaymentProofRepository#findBookingIdById}
     * exists. The decision reads the proof's status and writes it, and nothing
     * in the schema refuses a second write; only the booking's row lock makes
     * the second administrator read the proof <em>after</em> the first has
     * committed.
     *
     * <p>This is also what proves the persistence-context trap stays closed.
     * If the service read the booking id by loading the {@code PaymentProof}
     * entity instead of projecting it, the loser would hold that entity from
     * before the lock, the post-lock read would hand the same instance back
     * still saying {@code SUBMITTED}, and both approvals would go through —
     * with the lock fully in place and looking like it was working.
     */
    @Test
    void aSecondApprovalInFlightIsRefusedAndTheBookingIsConfirmedOnce() throws Exception {
        UUID proofId = submittedProofId();
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseRivalAtTheLock(pool, () -> review.approve(administrator, proofId, null), loser);

            assertThat(review.approve(administrator, proofId, null).status())
                    .isEqualTo(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("payment_proof_already_decided");
                    });
        }

        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        // One decision, so one entry in the history: a second approval that
        // slipped through would show up here even if the status did not.
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isOne();
    }

    /** Two administrators disagreeing at once: one decision lands, not both. */
    @Test
    void anApprovalAndARejectionInFlightResolveToOneOutcome() throws Exception {
        UUID proofId = submittedProofId();
        AtomicReference<Future<BookingResponse>> loser = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseRivalAtTheLock(pool, () -> review.reject(administrator, proofId, "The slip is unreadable."),
                    loser);

            assertThat(review.approve(administrator, proofId, null).status())
                    .isEqualTo(BookingStatus.CONFIRMED);

            assertThatThrownBy(() -> loser.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("payment_proof_already_decided");
                    });
        }

        assertThat(proofs.findById(proofId).orElseThrow()).satisfies(proof -> {
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
            assertThat(proof.getReviewedByUserId()).isEqualTo(administrator);
            assertThat(proof.getReviewNote()).isNull();
        });
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isOne();
        assertThat(eventsOfType(BookingEventType.PAYMENT_REJECTED)).isZero();
    }

    /**
     * The student cancels while an administrator is approving. The cancellation
     * holds the booking's row first, so the approval has to read what the
     * cancellation committed and refuse — the booking never ends up approved
     * with its seats already released.
     *
     * <p>This is the race {@code BookingService.cancel} took no lock for until
     * this change. Without it the approval reads {@code PAYMENT_UNDER_REVIEW}
     * from under the uncommitted cancellation, confirms a booking that is being
     * cancelled, and leaves an {@code APPROVED} proof on it.
     */
    @Test
    void anApprovalRacingAStudentCancellationIsRefusedRatherThanConfirmingAReleasedBooking() throws Exception {
        UUID proofId = submittedProofId();
        AtomicBoolean firstCancellation = new AtomicBoolean(true);
        AtomicReference<Future<BookingResponse>> approval = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            // The approving administrator, released one statement short of its
            // own locking read while the cancellation still holds the row.
            doAnswer(invocation -> {
                atTheLock.countDown();
                return real(invocation);
            }).when(bookings).lockById(bookingId);
            doAnswer(invocation -> {
                if (firstCancellation.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    approval.set(pool.submit(() -> review.approve(administrator, proofId, null)));
                    assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                return real(invocation);
            }).when(bookings).lockByIdAndUserId(bookingId, student);

            assertThat(bookingService.cancel(student, bookingId).status()).isEqualTo(BookingStatus.CANCELLED);

            assertThatThrownBy(() -> approval.get().get(30, TimeUnit.SECONDS))
                    .cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(codeOf(refusal)).isEqualTo("booking_not_under_review");
                    });
        }

        // One consistent final state: cancelled, seats released, and no
        // approved payment sitting on a booking nobody is travelling on.
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isZero();
    }

    // Fixtures

    /**
     * Submits a proof the way a student does, so the booking really is under
     * review and the row the races decide on really exists.
     */
    private UUID submittedProofId() {
        assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        return proofs.findAll().getFirst().getId();
    }

    /**
     * Stubs the review path's lock so the rival is released one statement short
     * of its own locking read: it is <em>at</em> the lock while the winner is
     * still inside its transaction. This is the retry an administrator fires by
     * double-clicking, which a {@code 409} on an already-committed decision
     * cannot cover.
     */
    private void releaseRivalAtTheLock(ExecutorService pool, Callable<BookingResponse> rival,
                                       AtomicReference<Future<BookingResponse>> loser) {
        AtomicBoolean winner = new AtomicBoolean(true);
        CountDownLatch atTheLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            if (winner.compareAndSet(true, false)) {
                Object locked = real(invocation);
                loser.set(pool.submit(rival));
                assertThat(atTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                return locked;
            }
            atTheLock.countDown();
            return real(invocation);
        }).when(bookings).lockById(bookingId);
    }

    /** Counted through the student's own read, which is the only thing that exposes the history. */
    private long eventsOfType(BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .count();
    }

    /**
     * Runs the call the stub intercepted for real.
     *
     * <p>{@code invocation.callRealMethod()} cannot do this for a Spring Data
     * repository: the method is an interface method with no body, and the spy
     * keeps the actual repository in its default answer rather than as a spied
     * instance. Forwarding through that answer is how a stub that needs the
     * real result — a locking read returning a managed booking — gets one.
     */
    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private static Object codeOf(ResponseStatusException problem) {
        return problem.getBody().getProperties().get("code");
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PROOF", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
