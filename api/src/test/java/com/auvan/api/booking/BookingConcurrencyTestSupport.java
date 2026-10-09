package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.IdempotencyService;
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
import org.junit.jupiter.api.AfterEach;
import org.mockito.invocation.InvocationOnMock;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
abstract class BookingConcurrencyTestSupport extends AuthenticationTestSupport {
    static final String ENDPOINT = "POST /api/v1/bookings";

    @Autowired
    BookingService bookingService;

    @Autowired
    SeatHoldService seatHoldService;

    @Autowired
    IdempotencyService idempotency;

    @MockitoSpyBean
    SeatClaimRepository claims;

    @MockitoSpyBean
    BookingRepository bookings;

    @Autowired
    IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    TransactionTemplate transactions;

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
    List<TripSeat> seats;
    UUID student;
    UUID rival;

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        seats = trip.getSeats();
        student = users.save(new AppUser("Ustudent", "Student")).getId();
        rival = users.save(new AppUser("Urival", "Rival")).getId();
    }

    @AfterEach
    void clearData() {
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    static Object codeOf(ResponseStatusException problem) {
        return problem.getBody().getProperties().get("code");
    }

    static CreateBookingRequest request(UUID holdId) {
        return new CreateBookingRequest(holdId, "Somchai P.", "0812345678");
    }

    UUID holdOn(UUID owner, TripSeat seat) {
        return seatHoldService.hold(owner, new CreateSeatHoldRequest(trip.getId(), List.of(seat.getId()))).holdId();
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-BOOK", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
