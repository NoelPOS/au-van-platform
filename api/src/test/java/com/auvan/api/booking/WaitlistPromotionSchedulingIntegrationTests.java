package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.WaitlistPromotionScheduler;
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
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both sides of the promotion scheduling gate.
 *
 * <p>With {@code booking.waitlist.enabled} turned back on, a seat whose hold
 * has lapsed really is promoted by the scheduler with nothing in this test
 * calling {@code sweep()}. Every other class calls the sweep directly, so
 * without this one the wiring — the {@code @Scheduled} method, the
 * poll-interval binding, the conditional bean — would be entirely unproven.
 *
 * <p>The property is set here and nowhere else, so this is the only context in
 * the suite with a live promoter in it. That isolation is the point, and its
 * other half is
 * {@code WaitlistPromotionIntegrationTests.theSchedulerIsNotWiredUnderTheTestConfiguration}:
 * under the ordinary test configuration there is no scheduler bean at all, so
 * no background sweep can hand a seat out under a booking-concurrency fixture
 * mid-assertion.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "booking.waitlist.enabled=true",
        "booking.waitlist.poll-interval=PT0.1S"
})
class WaitlistPromotionSchedulingIntegrationTests extends AuthenticationTestSupport {
    private static final long TIMEOUT_MILLIS = 20_000;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private WaitlistEntryRepository waitlist;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private OutboxEventRepository events;

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
        student = users.save(new AppUser("Ustudent-promo-schedule", "Waiting Student")).getId();
    }

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
    @AfterEach
    void clearData() {
        events.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void theScheduledSweepPromotesAWaitingStudentWithoutAnybodyCallingIt() throws Exception {
        UUID holder = users.save(new AppUser("Uholder-promo-schedule", "Holding Student")).getId();
        // A hold that lapsed with nothing running, which is how a seat most
        // often comes free (ADR-006).
        claims.save(new SeatClaim(trip.getSeats().getFirst(), holder, UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1)));
        UUID entryId = waitlist.save(new WaitlistEntry(trip, student, 1, OffsetDateTime.now())).getId();

        WaitlistEntry promoted = awaitPromotion(entryId);

        assertThat(promoted.getPromotionHoldId()).isNotNull();
        assertThat(claims.findByHoldId(promoted.getPromotionHoldId())).singleElement()
                .satisfies(claim -> assertThat(claim.getUserId()).isEqualTo(student));
    }

    /** The conditional bean really is conditional, and this is the context that turned it on. */
    @Test
    void theSchedulerExistsOnlyBecauseThisClassTurnedItOn() {
        assertThat(context.getBeanNamesForType(WaitlistPromotionScheduler.class)).hasSize(1);
    }

    /** Polls rather than sleeping a fixed time, so a slow machine waits longer and a fast one does not. */
    private WaitlistEntry awaitPromotion(UUID entryId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            WaitlistEntry entry = waitlist.findById(entryId).orElseThrow();
            if (entry.getStatus() == WaitlistStatus.PROMOTED) {
                return entry;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("The scheduled sweep never promoted waitlist entry " + entryId + ".");
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Promotion schedule layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PROMOSCHED", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
