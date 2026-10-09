package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
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
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class SeatHoldTestSupport extends AuthenticationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private TripRepository trips;

    @Autowired
    SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private TransactionTemplate transactions;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    Trip trip;
    List<TripSeat> seats;
    String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        claims.deleteAll();
        deleteBookings();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();

        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
    }

    @AfterEach
    void clearBookings() {
        claims.deleteAll();
        deleteBookings();
    }

    Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        return createTrip(vehicleCode, departureAt, status, 4);
    }

    Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status, int seatCount) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= seatCount; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        if (status == TripStatus.CANCELLED) {
            created.cancel("The van has broken down.");
        }
        return trips.save(created);
    }

    void bookTheClaimOn(TripSeat seat, OffsetDateTime expiresAt) {
        UUID bookingId = insertBooking();
        transactions.executeWithoutResult(status -> entityManager.createQuery("""
                        update SeatClaim claim
                        set claim.bookingId = :bookingId, claim.expiresAt = :expiresAt
                        where claim.tripSeat.id = :seatId
                        """)
                .setParameter("bookingId", bookingId)
                .setParameter("expiresAt", expiresAt)
                .setParameter("seatId", seat.getId())
                .executeUpdate());
    }

    private UUID insertBooking() {
        UUID userId = users.findByLineSubject("Ustudent").orElseThrow().getId();
        return bookings.saveAndFlush(new Booking(trip, userId, "AUV-000000-" + reference(), "Test Student",
                "0800000000", new BigDecimal("35.00"), OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now())).getId();
    }

    private static String reference() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void deleteBookings() {
        bookings.deleteAll();
    }

    void expireAHoldOn(TripSeat seat) {
        UUID owner = users.save(new AppUser("Uexpired", "Forgetful Student")).getId();
        claims.save(new SeatClaim(seat, owner, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1)));
    }

    ResultActions hold(String token, TripSeat... requested) throws Exception {
        return holdOn(trip, token, requested);
    }

    ResultActions holdOn(Trip target, String token, TripSeat... requested) throws Exception {
        String seatIds = Arrays.stream(requested)
                .map(seat -> "\"" + seat.getId() + "\"")
                .collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + target.getId() + "\",\"seatIds\":[" + seatIds + "]}"));
    }

    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
    }

    String tokenFor(String idToken, String lineSubject) throws Exception {
        when(lineTokenVerifier.verify(idToken)).thenReturn(new VerifiedLineIdentity(lineSubject, "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
