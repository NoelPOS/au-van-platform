package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryScheduler;
import com.auvan.api.booking.service.BookingService;
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
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both sides of the expiry scheduling gate.
 *
 * <p>With {@code booking.expiry.enabled} turned back on, an overdue booking
 * really is expired by the scheduler with nothing in this test calling
 * {@code sweep()}. Every other class calls the sweep directly, so without this
 * one the wiring — the {@code @Scheduled} method, the poll-interval binding, the
 * conditional bean — would be entirely unproven.
 *
 * <p>The property is set here and nowhere else, so this is the only context in
 * the suite with a live sweeper in it. That isolation is the point, and the
 * second test is the half that protects everyone else: under the ordinary test
 * configuration there is no scheduler bean at all, so no background sweep can
 * cancel a booking-concurrency fixture mid-assertion.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "booking.expiry.enabled=true",
        "booking.expiry.poll-interval=PT0.1S"
})
class BookingExpirySchedulingIntegrationTests extends AuthenticationTestSupport {
    private static final long TIMEOUT_MILLIS = 20_000;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

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

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-expiry-schedule", "Student")).getId();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        events.deleteAll();
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
    void theScheduledSweepExpiresAnOverdueBookingWithoutAnybodyCallingIt() throws Exception {
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(trip.getId(),
                List.of(trip.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, "key-scheduled",
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        UUID bookingId = bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), bookingId);

        assertThat(awaitExpiry(bookingId).getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(claims.findByBookingId(bookingId)).isEmpty();
    }

    /**
     * The gate from the side that matters to everyone else's tests. Remove
     * {@code @ConditionalOnProperty} from {@code BookingExpiryScheduler} and the
     * bean exists in every {@code @SpringBootTest} in the suite; the assertion
     * below is the only thing in the repository that would notice.
     */
    @Test
    void theSchedulerExistsOnlyBecauseThisClassTurnedItOn() {
        assertThat(context.getBeanNamesForType(BookingExpiryScheduler.class)).hasSize(1);
    }

    /** Polls rather than sleeping a fixed time, so a slow machine waits longer and a fast one does not. */
    private Booking awaitExpiry(UUID bookingId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            Booking booking = bookings.findById(bookingId).orElseThrow();
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                return booking;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The scheduled sweep never expired booking " + bookingId + ".");
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Expiry schedule layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-EXPSCHED", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
