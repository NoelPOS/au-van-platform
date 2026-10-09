package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
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
class StudentCancellationIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    private JdbcTemplate jdbc;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        when(lineTokenVerifier.verify("cutoff-token"))
                .thenReturn(new VerifiedLineIdentity("Ucutoff-student", "Test User"));
        token = JsonPath.read(mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"cutoff-token\"}"))
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
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

    @Test
    void aBookingSaysUntilWhenItCanBeCancelled() throws Exception {
        Trip trip = createTrip(OffsetDateTime.parse("2030-01-15T08:00:00Z"));
        String bookingId = book(trip);

        String body = mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.cancellableUntil")))
                .isEqualTo("2030-01-15T06:00:00Z");
    }

    @Test
    void aCancelledBookingIsNoLongerCancellable() throws Exception {
        String bookingId = book(createTrip(OffsetDateTime.now().plusDays(1)));

        cancel(bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cancellableUntil").doesNotExist());
    }

    @Test
    void aStudentCanCancelJustBeforeTheCutoff() throws Exception {
        Trip trip = createTrip(OffsetDateTime.now().plusHours(3));
        String bookingId = book(trip);
        departIn(trip, 125);

        cancel(bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aStudentCannotCancelInsideTwoHoursOfDeparture() throws Exception {
        Trip trip = createTrip(OffsetDateTime.now().plusHours(3));
        String bookingId = book(trip);
        departIn(trip, 115);

        cancel(bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("cancellation_closed"))
                .andExpect(jsonPath("$.detail").value(containsString("120 minutes before departure")));

        assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(claims.findByBookingId(UUID.fromString(bookingId))).hasSize(1);
    }

    private void departIn(Trip trip, int minutes) {
        jdbc.update("update trips set departure_at = ? where id = ?",
                OffsetDateTime.now().plusMinutes(minutes), trip.getId());
    }

    private ResultActions cancel(String bookingId) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                .header("Authorization", "Bearer " + token));
    }

    private String book(Trip trip) throws Exception {
        String holdId = JsonPath.read(mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[\""
                                + trip.getSeats().getFirst().getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.holdId");
        return JsonPath.read(mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "cutoff-" + holdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                                + "\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private Trip createTrip(OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-CUTOFF",
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-CUTOFF", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, departureAt));
    }
}
