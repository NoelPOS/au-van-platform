package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingCloseIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

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

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private WaitlistEntryRepository waitlist;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        token = tokenFor("close-token", "Uclose-student");
    }

    @AfterEach
    void clearData() {
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

    @Test
    void seatsCanStillBeHeldJustBeforeBookingCloses() throws Exception {
        Trip trip = createTrip("VAN-OPEN", OffsetDateTime.now().plusMinutes(100));

        hold(trip).andExpect(status().isCreated());
    }

    @Test
    void seatsCannotBeHeldOnceBookingHasClosed() throws Exception {
        Trip trip = createTrip("VAN-CLOSED", OffsetDateTime.now().plusMinutes(80));

        hold(trip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_closed"))
                .andExpect(jsonPath("$.detail").value(containsString("90 minutes before departure")));
        assertThat(claims.count()).isZero();
    }

    @Test
    void aHoldTakenBeforeBookingClosedCanStillBeBookedAfterwards() throws Exception {
        Trip trip = createTrip("VAN-LATE", OffsetDateTime.now().plusMinutes(100));
        String holdId = JsonPath.read(hold(trip).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.holdId");
        departIn(trip, 80);

        mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "close-late-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                                + "\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void aFullTripsWaitlistCanBeJoinedBeforeBookingCloses() throws Exception {
        Trip trip = createTrip("VAN-WAITOPEN", OffsetDateTime.now().plusMinutes(100));
        fill(trip);

        join(trip).andExpect(status().isCreated());
    }

    @Test
    void aWaitlistCannotBeJoinedOnceBookingHasClosed() throws Exception {
        Trip trip = createTrip("VAN-WAITCLOSED", OffsetDateTime.now().plusMinutes(80));
        fill(trip);

        join(trip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_closed"));
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void theCatalogueAndSeatMapSayWhenBookingCloses() throws Exception {
        Trip trip = createTrip("VAN-CATALOGUE", OffsetDateTime.parse("2030-01-15T08:00:00Z"));

        String expected = "2030-01-15T06:30:00Z";
        assertThat(OffsetDateTime.parse(JsonPath.read(content(get("/api/v1/trips")), "$[0].bookingClosesAt")))
                .isEqualTo(expected);
        assertThat(OffsetDateTime.parse(JsonPath.read(
                content(get("/api/v1/trips/" + trip.getId() + "/seats")), "$.bookingClosesAt")))
                .isEqualTo(expected);
    }

    private String content(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private ResultActions hold(Trip trip) throws Exception {
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[\""
                        + trip.getSeats().getFirst().getId() + "\"]}"));
    }

    private ResultActions join(Trip trip) throws Exception {
        return mockMvc.perform(post("/api/v1/waitlist")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatsWanted\":1}"));
    }

    private void fill(Trip trip) {
        UUID filler = users.save(new AppUser("Ufiller-" + trip.getId(), "Holding Student")).getId();
        trip.getSeats().forEach(seat -> claims.save(
                new SeatClaim(seat, filler, UUID.randomUUID(), OffsetDateTime.now().plusHours(2))));
    }

    private void departIn(Trip trip, int minutes) {
        jdbc.update("update trips set departure_at = ? where id = ?",
                OffsetDateTime.now().plusMinutes(minutes), trip.getId());
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode,
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, departureAt));
    }

    private String tokenFor(String idToken, String lineSubject) throws Exception {
        when(lineTokenVerifier.verify(idToken)).thenReturn(new VerifiedLineIdentity(lineSubject, "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
