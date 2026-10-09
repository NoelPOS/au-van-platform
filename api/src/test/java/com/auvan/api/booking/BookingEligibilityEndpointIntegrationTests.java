package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.repository.BookingRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingEligibilityEndpointIntegrationTests extends AuthenticationTestSupport {
    private static final String ELIGIBILITY = "/api/v1/me/booking-eligibility";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

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
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip();
        token = tokenFor("eligibility-token", "Ueligibility-student");
        student = users.findByLineSubject("Ueligibility-student").orElseThrow().getId();
    }

    @AfterEach
    void clearData() {
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void aStudentWithNothingOutstandingCanBook() throws Exception {
        eligibility()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBook").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.retryAt").doesNotExist())
                .andExpect(jsonPath("$.unpaidBookingId").doesNotExist());
    }

    @Test
    void anUnpaidBookingIsNamedSoTheStudentCanGoAndPayForIt() throws Exception {
        Booking unpaid = bookings.save(booking(OffsetDateTime.now()));

        eligibility()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBook").value(false))
                .andExpect(jsonPath("$.reason").value("unpaid_booking_exists"))
                .andExpect(jsonPath("$.message").value(containsString(unpaid.getReference())))
                .andExpect(jsonPath("$.unpaidBookingId").value(unpaid.getId().toString()))
                .andExpect(jsonPath("$.unpaidBookingReference").value(unpaid.getReference()));
    }

    @Test
    void aPauseSaysWhenTheStudentCanBookAgain() throws Exception {
        expiredAt(OffsetDateTime.now().minusDays(2));
        OffsetDateTime latest = expiredAt(OffsetDateTime.now().minusHours(3));

        String body = eligibility()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canBook").value(false))
                .andExpect(jsonPath("$.reason").value("booking_cooldown"))
                .andExpect(jsonPath("$.unpaidBookingId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.retryAt")))
                .isCloseTo(latest.plusHours(24), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void anAnonymousRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get(ELIGIBILITY)).andExpect(status().isUnauthorized());
    }

    private ResultActions eligibility() throws Exception {
        return mockMvc.perform(get(ELIGIBILITY).header("Authorization", "Bearer " + token));
    }

    private OffsetDateTime expiredAt(OffsetDateTime moment) {
        Booking booking = booking(moment.minusHours(2));
        booking.expire(moment);
        booking.recordEvent(BookingEventType.EXPIRED, "Expired unpaid.", null, moment);
        bookings.save(booking);
        return moment;
    }

    private Booking booking(OffsetDateTime createdAt) {
        String reference = "AUV-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return new Booking(trip, student, reference, "Somchai P.", "0812345678", new BigDecimal("35.00"),
                createdAt.plusHours(2), createdAt);
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-ELIGIBLE",
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-ELIGIBLE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
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
