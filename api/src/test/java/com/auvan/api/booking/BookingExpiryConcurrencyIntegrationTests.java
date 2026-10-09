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
import org.springframework.data.domain.Pageable;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
class BookingExpiryConcurrencyIntegrationTests extends AuthenticationTestSupport {
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
    void aSweepRacingAnApprovalLeavesTheBookingConfirmedWithItsSeats() throws Exception {
        UUID proofId = submittedProofId();
        overdue(bookingId);
        doReturn(List.of(bookingId)).when(bookings).findExpirable(any(OffsetDateTime.class), any(Pageable.class));
        AtomicReference<Future<Integer>> sweep = new AtomicReference<>();
        CountDownLatch sweepAtTheLock = new CountDownLatch(1);
        AtomicBoolean firstLock = new AtomicBoolean(true);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
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
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(BookingEventType.EXPIRED)).isEmpty();
        assertThat(outboxOfType(OutboxEventType.BOOKING_EXPIRED)).isEmpty();
    }

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
            assertThat(booking.getPaymentDeadlineAt()).isNull();
        });
        assertThat(claims.findByBookingId(bookingId)).hasSize(1);
        assertThat(bookingEventsOfType(BookingEventType.EXPIRED)).isEmpty();
        assertThat(proofs.count()).isOne();
    }

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

    private UUID submittedProofId() {
        assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        return proofs.findAll().getFirst().getId();
    }

    private void overdue(UUID id) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), id);
    }

    private List<BookingResponse.BookingEventResponse> bookingEventsOfType(BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .toList();
    }

    private List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

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
