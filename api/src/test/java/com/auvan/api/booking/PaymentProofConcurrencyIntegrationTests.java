package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
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
 * Concurrency cover for payment-proof submission. Like the two concurrency
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
 * Concurrency coverage against real PostgreSQL belongs to issue #10.
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
    private InMemoryPaymentProofStorage storage;

    // A spy, not a mock: every call runs for real except the one the test stubs.
    @MockitoSpyBean
    private BookingRepository bookings;

    @Autowired
    private PaymentProofRepository proofs;

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
    private UUID bookingId;

    @BeforeEach
    void setUp() {
        clearData();
        storage.reset();
        Trip trip = createTrip();
        student = users.save(new AppUser("Ustudent", "Student")).getId();
        bookingId = bookings.save(new Booking(trip, student, "AUV-250101-PROOFRAC", "Somchai P.",
                "0812345678", new BigDecimal("35.00"), OffsetDateTime.now())).getId();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        proofs.deleteAll();
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

    // Fixtures

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
