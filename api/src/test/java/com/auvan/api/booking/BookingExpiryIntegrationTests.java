package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatState;
import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.IdempotencyKey;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryScheduler;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofReviewService;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.booking.service.SeatAvailabilityService;
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
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The expiry sweep end to end: which bookings it takes, which it leaves, what a
 * student is left with afterwards, and what it prunes.
 *
 * <p>Not {@code @Transactional}. The sweep opens a transaction per booking and
 * decides behind a row lock; a test-managed transaction would put all of that on
 * one connection and the locking would prove nothing. Bookings are aged with
 * {@link JdbcTemplate} rather than by waiting, for the reason
 * {@code OutboxIntegrationTests} gives: nothing in this codebase injects a
 * {@code Clock}, and {@code Booking} exposes no setter for a deadline beyond the
 * transitions that own it.
 *
 * <p>All of this runs on H2, so none of it proves PostgreSQL's behaviour.
 * Coverage against real PostgreSQL belongs to issue #10.
 */
@SpringBootTest
class BookingExpiryIntegrationTests extends AuthenticationTestSupport {
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
    private ApplicationContext context;

    @Autowired
    private BookingExpiryService expiry;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private SeatAvailabilityService availability;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    @Autowired
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
    private UUID otherStudent;
    private UUID administrator;

    @BeforeEach
    void setUp() {
        clearData();
        storage.reset();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-expiry", "Student")).getId();
        otherStudent = users.save(new AppUser("Uother-expiry", "Other Student")).getId();
        administrator = users.save(new AppUser("Uadmin-expiry", "Administrator")).getId();
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

    // Success path

    /**
     * The whole of acceptance criterion 3 in one test: the booking ends
     * {@code CANCELLED}, its claims are gone, the seat reads {@code AVAILABLE}
     * again, and the history says why with nobody's name on it.
     */
    @Test
    void anOverduePendingPaymentBookingIsCancelledAndItsSeatBecomesAvailableAgain() {
        UUID bookingId = createBooking("key-pending");
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow()).satisfies(booking -> {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            // Terminal rows carry no deadline: NULL means "never expires".
            assertThat(booking.getPaymentDeadlineAt()).isNull();
        });
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(seatState(trip.getSeats().getFirst())).isEqualTo(SeatState.AVAILABLE);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).singleElement().satisfies(event -> {
            // No actor: nothing and nobody asked for this.
            assertThat(event.actorUserId()).isNull();
            assertThat(event.detail()).contains("Expired unpaid").contains("A1");
        });
    }

    @Test
    void anOverdueRejectedBookingIsExpiredToo() {
        UUID bookingId = createBooking("key-rejected");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");
        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PAYMENT_REJECTED);
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
    }

    /**
     * A booking under review expires too — bounded by departure rather than by a
     * payment timer — and the proof it leaves behind must not sit in the review
     * queue forever. The sweep cannot decide the proof itself: {@code V6}'s
     * {@code payment_proofs_review_recorded} CHECK demands a reviewer and there
     * is none, so the queue query excludes it instead.
     */
    @Test
    void anOverdueUnderReviewBookingIsExpiredAndItsProofLeavesTheReviewQueue() {
        UUID bookingId = createBooking("key-under-review");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        assertThat(review.list()).hasSize(1);
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        // Still SUBMITTED in the table, because the CHECK forbids anything else,
        // and out of the queue, because the administrator could never clear it.
        assertThat(proofs.findAll()).singleElement()
                .satisfies(proof -> assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED));
        assertThat(review.list()).isEmpty();
    }

    /** One row per expired booking, so the student is told once and only once. */
    @Test
    void eachExpiredBookingLeavesExactlyOnePendingOutboxRowAddressedToItsOwner() {
        UUID first = createBooking("key-outbox-1");
        UUID second = createBooking("key-outbox-2", trip.getSeats().get(1));
        overdue(first);
        overdue(second);

        assertThat(expiry.sweep()).isEqualTo(2);

        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).hasSize(2)
                .allSatisfy(event -> {
                    assertThat(event.getRecipientUserId()).isEqualTo(student);
                    assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
                })
                .extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(first, second);
    }

    /**
     * ADR-008 accepted that {@code idempotency_keys} grows without bound and
     * named this sweep as the home for the prune. The window is the whole guard:
     * a key dropped while its client could still retry stops replaying and the
     * retry writes a second booking.
     */
    @Test
    void theSweepPrunesSpentIdempotencyKeysAndLeavesRecentOnesAlone() {
        UUID recent = storedKey("recent-key", OffsetDateTime.now().minusHours(1));
        UUID spent = storedKey("spent-key", OffsetDateTime.now().minusHours(25));

        expiry.sweep();

        assertThat(idempotencyKeys.findById(recent)).isPresent();
        assertThat(idempotencyKeys.findById(spent)).isEmpty();
    }

    /**
     * The criterion that actually matters to a student, and the easiest one to
     * leave out: the freed seat is not merely reported available, it can be held
     * and booked by somebody else, end to end.
     */
    @Test
    void aSeatFreedByExpiryCanBeHeldAndBookedByAnotherStudent() {
        TripSeat seat = trip.getSeats().getFirst();
        UUID abandoned = createBooking("key-abandoned");
        overdue(abandoned);

        assertThat(expiry.sweep()).isOne();

        UUID holdId = holds.hold(otherStudent,
                new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
        bookingService.create(otherStudent, "key-rebooked",
                new CreateBookingRequest(holdId, "Nok S.", "0899999999"));

        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(otherStudent)).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT));
        assertThat(claims.findBySeatIdIn(List.of(seat.getId()))).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(otherStudent));
    }

    // Failure path

    /**
     * The guard that matters most, because a confirmed booking is one somebody
     * has paid for. Remove the status filter from
     * {@code BookingRepository.findExpirable} <em>and</em> from
     * {@code Booking.isExpirable} and this reddens.
     */
    @Test
    void aConfirmedBookingIsNeverExpiredWhateverItsDeadlineSays() {
        UUID bookingId = createBooking("key-confirmed");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        // A deadline in the past on a CONFIRMED row: the state the sweep must
        // refuse whatever the column says.
        overdue(bookingId);

        assertThat(expiry.sweep()).isZero();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).isEmpty();
    }

    /** No second event, no second outbox row, and no second release. */
    @Test
    void anAlreadyCancelledBookingIsNotExpiredASecondTime() {
        UUID bookingId = createBooking("key-twice");
        overdue(bookingId);
        assertThat(expiry.sweep()).isOne();

        // Age it again: a row that is already CANCELLED but still looks due.
        overdue(bookingId);
        assertThat(expiry.sweep()).isZero();

        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).hasSize(1);
    }

    @Test
    void aBookingWhoseDeadlineHasNotPassedIsLeftAlone() {
        UUID bookingId = createBooking("key-in-time");

        assertThat(expiry.sweep()).isZero();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).isEmpty();
    }

    // The deadline's three write points

    @Test
    void aNewBookingGetsTheShorterOfThePaymentWindowAndTheDepartureBound() {
        UUID bookingId = createBooking("key-window");

        // booking.payment-window is PT2H and the trip departs in a day, so the
        // window is the binding half.
        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
    }

    /**
     * A booking made close to departure is bounded by the departure cutoff
     * instead, which is the case the payment window alone would get wrong.
     */
    @Test
    void aBookingMadeCloseToDepartureIsBoundedByTheDepartureCutoffInstead() {
        Trip soon = trips.save(new Trip(trip.getRoute(), trip.getVehicle(), OffsetDateTime.now().plusMinutes(90)));
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(soon.getId(),
                List.of(soon.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, "key-soon", new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));

        UUID bookingId = bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(soon.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
    }

    /** A student waiting on a reviewer is bounded by departure, not by a timer. */
    @Test
    void submittingAProofMovesTheDeadlineToTheDepartureBound() {
        UUID bookingId = createBooking("key-submit");

        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
    }

    /**
     * A rejected student gets a real window to resubmit in. Without the reset
     * they would inherit whatever was left of the deadline the review ran down.
     */
    @Test
    void rejectingAProofGivesTheStudentAFreshWindowToResubmitIn() {
        UUID bookingId = createBooking("key-reject-window");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
    }

    // The V8 backfill

    /**
     * The backfill, run against rows that look like the ones {@code V8} found.
     * Flyway applies it to an empty database in this suite, which proves only
     * that it parses, and a backfill that matched nothing would leave every
     * booking that existed before this feature with a {@code NULL} deadline —
     * never expiring, which is the gap the whole issue exists to close.
     *
     * <p>The statement is read out of the migration rather than restated here,
     * so the two cannot drift apart.
     *
     * <p>The {@code PAYMENT_UNDER_REVIEW} row is the one that distinguishes the
     * two formulas. It must come out on the departure bound alone, the way
     * {@link PaymentProofService#submit} writes it for every proof submitted
     * after the migration; the creation formula would instead hand a booking
     * already in front of an administrator a fixed two-hour timer measured from
     * the migration, which is what acceptance criterion 3 on #62 forbids. The
     * trip departs tomorrow, so the two bounds are twenty-one hours apart and
     * the assertions cannot be satisfied by both.
     */
    @Test
    void theV8BackfillBoundsEachNonTerminalStatusByItsOwnFormulaAndLeavesTerminalOnesNull() throws Exception {
        UUID waiting = createBooking("key-backfill-waiting");
        UUID confirmed = createBooking("key-backfill-confirmed", trip.getSeats().get(1));
        paymentProofs.submit(student, confirmed, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        UUID underReview = createBooking("key-backfill-under-review", trip.getSeats().get(2));
        paymentProofs.submit(student, underReview, jpeg("the-slip"));
        // All three as they would have been before V8 ran.
        jdbc.update("update bookings set payment_deadline_at = null");

        jdbc.update(backfillStatementOfV8());

        assertThat(bookings.findById(waiting).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(underReview).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(confirmed).orElseThrow().getPaymentDeadlineAt()).isNull();
    }

    /** The one {@code UPDATE} in {@code V8}, taken from the migration itself. */
    private static String backfillStatementOfV8() throws Exception {
        String migration = new String(new ClassPathResource(
                "db/migration/V8__add_booking_payment_deadline.sql").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int start = migration.indexOf("UPDATE bookings");
        assertThat(start).isNotNegative();
        return migration.substring(start, migration.indexOf(';', start) + 1);
    }

    // The scheduling gate

    /**
     * The gate, from the side that matters to everyone else's tests: under the
     * ordinary test configuration there is no scheduler bean at all, so no
     * background sweep can cancel a concurrency test's fixture mid-assertion.
     * Remove {@code @ConditionalOnProperty} from
     * {@code BookingExpiryScheduler} and this reddens.
     * {@code BookingExpirySchedulingIntegrationTests} proves the other side.
     */
    @Test
    void noSweeperRunsUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(BookingExpiryScheduler.class)).isEmpty();
    }

    // Fixtures

    private UUID createBooking(String idempotencyKey) {
        return createBooking(idempotencyKey, trip.getSeats().getFirst());
    }

    /** A booking made the way a student makes one, so the create path really runs. */
    private UUID createBooking(String idempotencyKey, TripSeat seat) {
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
        bookingService.create(student, idempotencyKey,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
    }

    /**
     * Ages a booking by writing {@code payment_deadline_at} directly. Nothing
     * injects a {@code Clock} and {@code Booking} has no setter for this column
     * beyond the transitions that own it, so the alternative is waiting out a
     * real two-hour window.
     */
    private void overdue(UUID bookingId) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), bookingId);
    }

    private UUID storedKey(String key, OffsetDateTime createdAt) {
        return idempotencyKeys.save(new IdempotencyKey(student, "POST /api/v1/bookings", key,
                "0".repeat(64), 201, "{}", createdAt)).getId();
    }

    private SeatState seatState(TripSeat seat) {
        return availability.seatMap(trip.getId(), otherStudent).seats().stream()
                .filter(mapped -> mapped.id().equals(seat.getId()))
                .map(TripSeatMapResponse.SeatResponse::state)
                .findFirst()
                .orElseThrow();
    }

    /** Read through the student's own booking response, which is what exposes the history. */
    private List<BookingResponse.BookingEventResponse> bookingEventsOfType(UUID bookingId, BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
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
        SeatLayout layout = seatLayouts.save(new SeatLayout("Expiry layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-EXPIRY", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
