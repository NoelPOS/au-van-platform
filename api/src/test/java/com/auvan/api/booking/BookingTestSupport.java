package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
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
abstract class BookingTestSupport extends AuthenticationTestSupport {
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
    TripRepository trips;

    @Autowired
    SeatClaimRepository claims;

    @Autowired
    BookingRepository bookings;

    @Autowired
    IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    Trip trip;
    List<TripSeat> seats;
    String studentToken;
    UUID studentId;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
        studentId = users.findByLineSubject("Ustudent").orElseThrow().getId();
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

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
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

    String holdOn(TripSeat... requested) throws Exception {
        return JsonPath.read(hold(studentToken, requested).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.holdId");
    }

    ResultActions hold(String token, TripSeat... requested) throws Exception {
        String seatIds = Arrays.stream(requested)
                .map(seat -> "\"" + seat.getId() + "\"")
                .collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[" + seatIds + "]}"));
    }

    ResultActions confirm(String token, String holdId, String key) throws Exception {
        return confirm(token, holdId, key, "Somchai P.", "0812345678");
    }

    ResultActions confirm(String token, String holdId, String key, String name, String phone)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"" + name + "\","
                        + "\"passengerPhone\":\"" + phone + "\"}");
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    ResultActions cancel(String token, String bookingId) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                .header("Authorization", "Bearer " + token));
    }

    MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
    }

    String bookingIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
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
