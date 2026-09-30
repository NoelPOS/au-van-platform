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

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
class PaymentProofConcurrencyIntegrationTests extends AuthenticationTestSupport {
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
        SeatClaim claim = new SeatClaim(seat, student, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(10));
        claim.attachTo(bookingId);
        claims.save(claim);
    }

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

        assertThat(proofs.count()).isOne();
        assertThat(storage.objects()).hasSize(1);
        assertThat(storage.objects().values()).singleElement()
                .satisfies(object -> assertThat(object.content())
                        .isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8)));
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus())
                        .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW));
    }

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
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isOne();
    }

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

    @Test
    void anApprovalRacingAStudentCancellationIsRefusedRatherThanConfirmingAReleasedBooking() throws Exception {
        UUID proofId = submittedProofId();
        AtomicBoolean firstCancellation = new AtomicBoolean(true);
        AtomicReference<Future<BookingResponse>> approval = new AtomicReference<>();
        CountDownLatch atTheLock = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
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

        assertThat(bookings.findById(bookingId).orElseThrow().getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
        assertThat(eventsOfType(BookingEventType.PAYMENT_APPROVED)).isZero();
    }

    private UUID submittedProofId() {
        assertThat(paymentProofs.submit(student, bookingId, jpeg("the-slip")).status())
                .isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        return proofs.findAll().getFirst().getId();
    }

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

    private long eventsOfType(BookingEventType type) {
        return bookingService.get(student, bookingId).events().stream()
                .filter(event -> event.type() == type)
                .count();
    }

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
