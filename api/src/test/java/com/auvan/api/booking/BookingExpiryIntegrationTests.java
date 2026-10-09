package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.SeatState;
import com.auvan.api.booking.dto.TripSeatMapResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.IdempotencyKey;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryScheduler;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingExpiryWriter;
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
import org.springframework.data.domain.PageRequest;
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

@SpringBootTest
class BookingExpiryIntegrationTests extends AuthenticationTestSupport {
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
    private BookingExpiryWriter expiryWriter;

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

    @Test
    void anOverduePendingPaymentBookingIsCancelledAndItsSeatBecomesAvailableAgain() {
        UUID bookingId = createBooking("key-pending");
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(bookings.findById(bookingId).orElseThrow()).satisfies(booking -> {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getPaymentDeadlineAt()).isNull();
        });
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(seatState(trip.getSeats().getFirst())).isEqualTo(SeatState.AVAILABLE);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).singleElement().satisfies(event -> {
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

    @Test
    void anUnderReviewBookingIsNeitherAnExpiryCandidateNorExpirableEvenWithAStaleDeadline() {
        UUID bookingId = createBooking("key-under-review");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        overdue(bookingId);
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(bookings.findExpirable(now, PageRequest.of(0, 50))).doesNotContain(bookingId);
        assertThat(expiryWriter.expire(bookingId, now)).isFalse();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(review.list()).hasSize(1);
    }

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

    @Test
    void theSweepPrunesSpentIdempotencyKeysAndLeavesRecentOnesAlone() {
        UUID recent = storedKey("recent-key", OffsetDateTime.now().minusHours(1));
        UUID spent = storedKey("spent-key", OffsetDateTime.now().minusHours(25));

        expiry.sweep();

        assertThat(idempotencyKeys.findById(recent)).isPresent();
        assertThat(idempotencyKeys.findById(spent)).isEmpty();
    }

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

    @Test
    void aConfirmedBookingIsNeverExpiredWhateverItsDeadlineSays() {
        UUID bookingId = createBooking("key-confirmed");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        overdue(bookingId);

        assertThat(expiry.sweep()).isZero();

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(bookingId, BookingEventType.EXPIRED)).isEmpty();
    }

    @Test
    void anAlreadyCancelledBookingIsNotExpiredASecondTime() {
        UUID bookingId = createBooking("key-twice");
        overdue(bookingId);
        assertThat(expiry.sweep()).isOne();

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

    @Test
    void aNewBookingGetsTheShorterOfThePaymentWindowAndTheDepartureBound() {
        UUID bookingId = createBooking("key-window");

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void aBookingMadeCloseToDepartureIsBoundedByTheDepartureCutoffInstead() {
        Trip soon = trips.save(new Trip(trip.getRoute(), trip.getVehicle(), OffsetDateTime.now().plusMinutes(100)));
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(soon.getId(),
                List.of(soon.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, "key-soon", new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));

        UUID bookingId = bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(soon.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void submittingAProofStopsTheDeadlineSoASlowReviewNeverExpiresTheBooking() {
        UUID bookingId = createBooking("key-submit");

        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt()).isNull();
        assertThat(expiry.sweep()).isZero();
    }

    @Test
    void theV10MigrationClearsTheDeadlineOfEveryBookingAlreadyUnderReview() throws Exception {
        UUID waiting = createBooking("key-v10-waiting");
        UUID underReview = createBooking("key-v10-under-review", trip.getSeats().get(1));
        paymentProofs.submit(student, underReview, jpeg("the-slip"));
        overdue(underReview);

        jdbc.update(statementOf("db/migration/V10__stop_deadline_under_payment_review.sql", "UPDATE bookings"));

        assertThat(bookings.findById(underReview).orElseThrow().getPaymentDeadlineAt()).isNull();
        assertThat(bookings.findById(waiting).orElseThrow().getPaymentDeadlineAt()).isNotNull();
    }

    @Test
    void aQuickRejectionKeepsTheOriginalDeadlineRatherThanStartingAFreshWindow() {
        UUID bookingId = createBooking("key-reject-early");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));

        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        Booking rejected = bookings.findById(bookingId).orElseThrow();
        assertThat(rejected.getPaymentDeadlineAt())
                .isCloseTo(rejected.getCreatedAt().plusHours(2), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void aRejectionAfterTheOriginalDeadlineGivesThirtyMinutesToResubmit() {
        UUID bookingId = createBooking("key-reject-late");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        jdbc.update("update bookings set created_at = ? where id = ?", OffsetDateTime.now().minusHours(3), bookingId);

        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        assertThat(bookings.findById(bookingId).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusMinutes(30), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void theV8BackfillBoundsEachNonTerminalStatusByItsOwnFormulaAndLeavesTerminalOnesNull() throws Exception {
        UUID waiting = createBooking("key-backfill-waiting");
        UUID confirmed = createBooking("key-backfill-confirmed", trip.getSeats().get(1));
        paymentProofs.submit(student, confirmed, jpeg("the-slip"));
        review.approve(administrator, proofs.findAll().getFirst().getId(), null);
        UUID underReview = createBooking("key-backfill-under-review", trip.getSeats().get(2));
        paymentProofs.submit(student, underReview, jpeg("the-slip"));
        jdbc.update("update bookings set payment_deadline_at = null");

        jdbc.update(statementOf("db/migration/V8__add_booking_payment_deadline.sql", "UPDATE bookings"));

        assertThat(bookings.findById(waiting).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(OffsetDateTime.now().plusHours(2), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(underReview).orElseThrow().getPaymentDeadlineAt())
                .isCloseTo(trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.MINUTES));
        assertThat(bookings.findById(confirmed).orElseThrow().getPaymentDeadlineAt()).isNull();
    }

    private static String statementOf(String path, String opening) throws Exception {
        String migration = new String(new ClassPathResource(path).getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        int start = migration.indexOf(opening);
        assertThat(start).isNotNegative();
        return migration.substring(start, migration.indexOf(';', start) + 1);
    }

    @Test
    void noSweeperRunsUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(BookingExpiryScheduler.class)).isEmpty();
    }

    private UUID createBooking(String idempotencyKey) {
        return createBooking(idempotencyKey, trip.getSeats().getFirst());
    }

    private UUID createBooking(String idempotencyKey, TripSeat seat) {
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
        bookingService.create(student, idempotencyKey,
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
    }

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
