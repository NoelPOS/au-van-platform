package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofReviewService;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.booking.service.SeatHoldService;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

/**
 * The highest-risk part of #62: what happens when the sweep and a human reach
 * the same booking at the same moment. "Safely" is the load-bearing word in
 * acceptance criterion 3 — the sweep must not cancel a booking an administrator
 * is approving, and must not delete the claims of a seat somebody has paid for.
 *
 * <p>Like the three concurrency classes before it, this one must <strong>not</strong>
 * be {@code @Transactional}: a test-managed transaction would put both threads
 * on one connection and there would be no race left to observe.
 *
 * <p>Each test starts the sweep while its rival still holds the booking's row,
 * so the sweep reads its candidates from before the rival's write and is granted
 * the lock only after that write commits. That is the interleaving a real race
 * produces only sometimes, made to happen every run. The stubs control timing
 * only; the production path runs in both threads.
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
class BookingExpiryConcurrencyIntegrationTests extends AuthenticationTestSupport {
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
    private BookingExpiryService expiry;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    // A spy, not a mock: every call runs for real except the one a test stubs.
    @MockitoSpyBean
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private JdbcTemplate jdbc;

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
    private UUID administrator;
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        clearData();
        storage.reset();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-expiryrace", "Student")).getId();
        administrator = users.save(new AppUser("Uadmin-expiryrace", "Administrator")).getId();
        bookingId = createBooking();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        events.deleteAll();
        proofs.deleteAll();
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
     * The race the whole design of this sweep exists for. The administrator
     * holds the booking's row while the sweep reads its candidates, so by the
     * time the sweep is granted the lock its candidate list is stale — and the
     * booking must end {@code CONFIRMED} <em>with its {@code seat_claims}
     * intact</em>, because a confirmed booking whose seats were freed is a
     * student who paid for nothing.
     *
     * <p>Two guards are on trial here. Replace {@code lockById} with
     * {@code findById} in {@code BookingExpiryWriter} and the sweep reads
     * {@code PAYMENT_UNDER_REVIEW} from under the uncommitted approval, cancels
     * a booking that is being paid for, and deletes its claims. Keep the lock
     * but drop the {@code isExpirable} re-check and the same thing happens one
     * statement later. Either deletion reddens this test.
     */
    @Test
    void aSweepRacingAnApprovalLeavesTheBookingConfirmedWithItsSeats() throws Exception {
        UUID proofId = submittedProofId();
        // Overdue, so the sweep really does name it as a candidate.
        overdue(bookingId);
        AtomicReference<Future<Integer>> sweep = new AtomicReference<>();
        CountDownLatch sweepAtTheLock = new CountDownLatch(1);
        AtomicBoolean firstLock = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            // Both the approval and the sweep lock through lockById. The first
            // call is the approval's; the second is the sweep's, and it blocks
            // on the row until the approval commits.
            doAnswer(invocation -> {
                if (firstLock.compareAndSet(true, false)) {
                    Object locked = real(invocation);
                    sweep.set(pool.submit(expiry::sweep));
                    assertThat(sweepAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                    return locked;
                }
                sweepAtTheLock.countDown();
                return real(invocation);
            }).when(bookings).lockById(bookingId);

            assertThat(review.approve(administrator, proofId, "Received in full.").status())
                    .isEqualTo(BookingStatus.CONFIRMED);
            assertThat(sweep.get().get(30, TimeUnit.SECONDS)).isZero();
        }

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        // The point of the whole test: a paid seat still belongs to the student.
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(BookingEventType.EXPIRED)).isEmpty();
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).isEmpty();
    }

    /**
     * The student cancels the booking the sweep was about to expire. One
     * cancellation, one history entry, one release of the claims — never a
     * double-cancel that appends a second entry and tells the student twice.
     */
    @Test
    void aSweepRacingTheStudentsOwnCancellationCancelsItOnceOnly() throws Exception {
        overdue(bookingId);
        AtomicReference<Future<Integer>> sweep = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheSweepAtTheStudentsLock(pool, sweep);

            assertThat(bookingService.cancel(student, bookingId).status()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(sweep.get().get(30, TimeUnit.SECONDS)).isZero();
        }

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(bookingEventsOfType(BookingEventType.CANCELLED)).hasSize(1);
        assertThat(bookingEventsOfType(BookingEventType.EXPIRED)).isEmpty();
        assertThat(outboxOfType(OutboxEventType.BOOKING_CANCELLED)).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).isEmpty();
    }

    /**
     * The student sends their slip in the same instant the sweep names their
     * booking. The submission commits first and pushes the deadline out to the
     * departure bound, so the sweep — re-reading behind its lock — must find a
     * booking that is no longer overdue and skip it.
     *
     * <p>This is the case a status check alone would get wrong: after the
     * submission the booking is {@code PAYMENT_UNDER_REVIEW}, which is still an
     * expirable status. Only the deadline half of the re-check saves it.
     */
    @Test
    void aSweepRacingAProofSubmissionSkipsTheBookingTheSubmissionMovedOn() throws Exception {
        overdue(bookingId);
        AtomicReference<Future<Integer>> sweep = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            releaseTheSweepAtTheStudentsLock(pool, sweep);

            assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                    .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
            assertThat(sweep.get().get(30, TimeUnit.SECONDS)).isZero();
        }

        assertThat(bookings.findById(bookingId).orElseThrow()).satisfies(booking -> {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
            assertThat(booking.getPaymentDeadlineAt()).isAfter(OffsetDateTime.now());
        });
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(BookingEventType.EXPIRED)).isEmpty();
        assertThat(proofs.count()).isOne();
    }

    // Fixtures

    /**
     * Starts the sweep while the student's own transaction holds the booking's
     * row, and does not let that transaction go on until the sweep is at its
     * lock. The sweep therefore reads its candidates from before the student's
     * write and is granted the lock only after that write commits, which is the
     * interleaving a real race produces only sometimes.
     *
     * <p>The student's paths lock through {@code lockByIdAndUserId} and the
     * sweep through {@code lockById}, so the two stubs need not tell each other
     * apart the way the approval race's single stub has to.
     */
    private void releaseTheSweepAtTheStudentsLock(ExecutorService pool, AtomicReference<Future<Integer>> sweep) {
        AtomicBoolean firstStudentLock = new AtomicBoolean(true);
        CountDownLatch sweepAtTheLock = new CountDownLatch(1);
        doAnswer(invocation -> {
            sweepAtTheLock.countDown();
            return real(invocation);
        }).when(bookings).lockById(bookingId);
        doAnswer(invocation -> {
            if (firstStudentLock.compareAndSet(true, false)) {
                Object locked = real(invocation);
                sweep.set(pool.submit(expiry::sweep));
                assertThat(sweepAtTheLock.await(10, TimeUnit.SECONDS)).isTrue();
                return locked;
            }
            return real(invocation);
        }).when(bookings).lockByIdAndUserId(bookingId, student);
    }

    private UUID createBooking() {
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(trip.getId(),
                List.of(trip.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, "key-expiry-race",
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
    }

    /** Submits a proof the way a student does, so the booking really is under review. */
    private UUID submittedProofId() {
        assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        return proofs.findAll().getFirst().getId();
    }

    /**
     * Ages the booking by writing {@code payment_deadline_at} directly. Nothing
     * injects a {@code Clock} and {@code Booking} has no setter for this column
     * beyond the transitions that own it.
     */
    private void overdue(UUID id) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), id);
    }

    /** Counted through the student's own read, which is the only thing that exposes the history. */
    private List<BookingResponse.BookingEventResponse> bookingEventsOfType(BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
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

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Expiry race layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-EXPRACE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
