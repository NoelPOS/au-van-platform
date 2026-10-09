package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatAvailabilityService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.WaitlistPromotionService;
import com.auvan.api.booking.service.WaitlistPromotionWriter;
import com.auvan.api.booking.service.WaitlistService;
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
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
abstract class WaitlistPromotionConcurrencyTestSupport extends AuthenticationTestSupport {
    @Autowired
    WaitlistPromotionService promotion;

    @Autowired
    WaitlistPromotionWriter writer;

    @Autowired
    WaitlistService waitlistService;

    @Autowired
    SeatHoldService holds;

    @Autowired
    BookingService bookingService;

    @MockitoSpyBean
    SeatAvailabilityService availability;

    @MockitoSpyBean
    SeatClaimRepository claims;

    @MockitoSpyBean
    WaitlistEntryRepository waitlist;

    @Autowired
    BookingRepository bookings;

    @Autowired
    OutboxEventRepository events;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

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

    Trip trip;
    UUID student;
    UUID rival;
    UUID entryId;
    UUID contestedSeat;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-promorace", "Waiting Student")).getId();
        rival = users.save(new AppUser("Urival-promorace", "Rival Student")).getId();
        UUID holder = users.save(new AppUser("Uholder-promorace", "Holding Student")).getId();
        contestedSeat = trip.getSeats().getFirst().getId();
        claims.save(new SeatClaim(trip.getSeats().get(1), holder, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(2)));
        entryId = waitlist.save(new WaitlistEntry(trip, student, 1, OffsetDateTime.now())).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    WaitlistEntry entry() {
        return waitlist.findById(entryId).orElseThrow();
    }

    List<SeatClaim> claimsOnTheContestedSeat() {
        return claims.findAll().stream()
                .filter(claim -> claim.getTripSeat().getId().equals(contestedSeat))
                .toList();
    }

    List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Promotion race layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PROMORACE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
