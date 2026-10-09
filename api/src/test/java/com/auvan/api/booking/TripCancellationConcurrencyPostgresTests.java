package com.auvan.api.booking;

import com.auvan.api.PostgresTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.TripChangeService;
import com.auvan.api.booking.service.WaitlistService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxRecorder;
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
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

@SpringBootTest
class TripCancellationConcurrencyPostgresTests extends PostgresTestSupport {
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    private TripChangeService changes;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService seatHolds;

    @Autowired
    private PaymentProofService paymentProofs;

    @MockitoSpyBean
    private AppUserRepository users;

    @MockitoSpyBean
    private OutboxRecorder recorder;

    @MockitoSpyBean
    private BookingRepository bookings;

    @MockitoSpyBean
    private WaitlistEntryRepository waitlist;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private TripRepository trips;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    private Trip trip;
    private UUID student;
    private UUID admin;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        student = users.save(new AppUser("Upg-cancel-student", "Student")).getId();
        admin = users.save(new AppUser("Upg-cancel-admin", "Administrator")).getId();
    }

    @AfterEach
    void clearData() {
        outbox.deleteAll();
        waitlist.deleteAll();
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
    void aBookingConfirmedWhileItsTripIsCancelledIsCancelledWithIt() throws Exception {
        UUID holdId = seatHolds.hold(student, new CreateSeatHoldRequest(trip.getId(),
                List.of(trip.getSeats().getFirst().getId()))).holdId();
        AtomicReference<Future<?>> cancellation = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                cancellation.set(startAndWait(pool, () -> changes.cancel(admin, trip.getId(), "Van broke down.")));
                return real(invocation);
            }).when(users).lockById(student);

            bookingService.create(student, "pg-cancel-race", new CreateBookingRequest(holdId, "Somchai P.",
                    "0812345678"));
            cancellation.get().get(30, TimeUnit.SECONDS);
        }

        assertThat(trips.findById(trip.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.CANCELLED);
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED));
        assertThat(claims.count()).isZero();
    }

    @Test
    void aSlipUploadedWhileItsTripIsCancelledIsRefusedRatherThanLost() throws Exception {
        UUID bookingId = unpaidBooking();
        AtomicReference<Future<BookingResponse>> upload = new AtomicReference<>();

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            doAnswer(invocation -> {
                upload.set(startAndWait(pool, () -> paymentProofs.submit(student, bookingId, new MockMultipartFile(
                        "file", "slip.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, 1}))));
                return invocation.callRealMethod();
            }).when(recorder).record(eq(OutboxEventType.TRIP_CANCELLED), any(), any(), any(), any());

            changes.cancel(admin, trip.getId(), "Van broke down.");

            assertThatThrownBy(() -> upload.get().get(30, TimeUnit.SECONDS)).cause()
                    .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> assertThat(
                            refusal.getBody().getProperties().get("code")).isEqualTo("booking_not_awaiting_payment"));
        }

        Booking cancelled = bookings.findById(bookingId).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(cancelled.getRefundStatus()).isEqualTo(RefundStatus.NONE);
        assertThat(proofs.count()).isZero();
    }

    @Test
    void aBookingItsStudentCancelsMidCascadeKeepsItsOwnCancellation() throws Exception {
        UUID bookingId = unpaidBooking();
        doAnswer(invocation -> {
            Object ids = real(invocation);
            onAnotherThread(() -> bookingService.cancel(student, bookingId));
            return ids;
        }).when(bookings).findActiveIdsByTripId(trip.getId());

        changes.cancel(admin, trip.getId(), "Van broke down.");

        assertThat(outbox.findAll()).noneMatch(event -> event.getEventType() == OutboxEventType.TRIP_CANCELLED);
        assertThat(jdbc.queryForList("select event_type from booking_events where booking_id = ?", String.class,
                bookingId)).containsExactly("CANCELLED");
    }

    @Test
    void aStudentWhoLeavesTheWaitlistMidCascadeIsNotToldTheTripIsCancelled() throws Exception {
        UUID entryId = waitlist.save(new WaitlistEntry(trip, student, 1, OffsetDateTime.now())).getId();
        doAnswer(invocation -> {
            Object ids = real(invocation);
            onAnotherThread(() -> {
                waitlistService.leave(student, entryId);
                return null;
            });
            return ids;
        }).when(waitlist).findQueuedIdsByTripId(trip.getId());

        changes.cancel(admin, trip.getId(), "Van broke down.");

        assertThat(waitlist.findById(entryId).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(outbox.findAll()).noneMatch(event -> event.getEventType() == OutboxEventType.TRIP_CANCELLED);
    }

    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private static void onAnotherThread(Callable<?> work) throws Exception {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            pool.submit(work).get(30, TimeUnit.SECONDS);
        }
    }

    // Waits briefly: a rival that settles inside the wait was not held back by a lock.
    private static <T> Future<T> startAndWait(ExecutorService pool, Callable<T> rival) throws InterruptedException {
        Future<T> started = pool.submit(rival);
        try {
            started.get(3, TimeUnit.SECONDS);
        } catch (TimeoutException | ExecutionException settledLaterOrRefused) {
            // The caller reads the outcome from the future.
        }
        return started;
    }

    private UUID unpaidBooking() {
        OffsetDateTime now = OffsetDateTime.now();
        TripSeat seat = trip.getSeats().getFirst();
        Booking booking = new Booking(trip, student, "AUV-261009-PGCANCEL", "Somchai P.", "0812345678",
                new BigDecimal("35.00"), now.plusHours(2), now);
        booking.addSeat(seat);
        UUID bookingId = bookings.save(booking).getId();
        SeatClaim claim = new SeatClaim(seat, student, UUID.randomUUID(), now.plusMinutes(5));
        claim.attachTo(bookingId);
        claims.save(claim);
        return bookingId;
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-PGCANCEL",
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PGCANCEL", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
