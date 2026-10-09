package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentProofReviewTestSupport.FakeStorageConfiguration.class)
abstract class PaymentProofReviewTestSupport extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InMemoryPaymentProofStorage storage;

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
    BookingRepository bookings;

    @Autowired
    PaymentProofRepository proofs;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private List<TripSeat> seats;
    String studentToken;
    String adminToken;
    UUID studentId;
    UUID adminId;
    String bookingId;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        storage.reset();
        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1));
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent", false);
        studentId = users.findByLineSubject("Ustudent").orElseThrow().getId();
        adminToken = tokenFor("admin-token", "Uadmin", true);
        adminId = users.findByLineSubject("Uadmin").orElseThrow().getId();
        bookingId = bookingIdFrom(confirm(holdOn(seats.get(0)), "key-1"));
    }

    @AfterEach
    void clearData() {
        proofs.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    ResultActions queue() throws Exception {
        return mockMvc.perform(get("/api/v1/admin/payment-proofs").header("Authorization", bearer(adminToken)));
    }

    ResultActions approve(UUID proofId, String note) throws Exception {
        return mockMvc.perform(decision(proofId, "approve", body(note)).header("Authorization", bearer(adminToken)));
    }

    ResultActions reject(UUID proofId, String note) throws Exception {
        return mockMvc.perform(decision(proofId, "reject", body(note)).header("Authorization", bearer(adminToken)));
    }

    static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decision(
            UUID proofId, String action, String body) {
        return post("/api/v1/admin/payment-proofs/" + proofId + "/" + action)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String body(String note) {
        return note == null ? "{}" : "{\"note\":" + quoted(note) + "}";
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String image(UUID proofId) {
        return "/api/v1/admin/payment-proofs/" + proofId + "/image";
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    UUID proofIdOf(String booking) throws Exception {
        String body = queue().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.bookingId == '" + booking + "')].id");
        return UUID.fromString(ids.getLast());
    }

    static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    ResultActions submit(String booking, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/bookings/" + booking + "/payment-proof")
                        .file(file)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
    }

    Trip createTrip(String vehicleCode, OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        return trips.save(created);
    }

    String holdOn(TripSeat seat) throws Exception {
        String response = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + seat.getTrip().getId() + "\",\"seatIds\":[\"" + seat.getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.holdId");
    }

    ResultActions confirm(String holdId, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("Authorization", bearer(studentToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                        + "\"passengerPhone\":\"0812345678\"}"));
    }

    String bookingIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String tokenFor(String idToken, String lineSubject, boolean administrator) throws Exception {
        when(lineTokenVerifier.verify(idToken)).thenReturn(new VerifiedLineIdentity(lineSubject, "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (administrator) {
            AppUser user = users.findByLineSubject(lineSubject).orElseThrow();
            user.promoteToAdmin();
            users.save(user);
            return tokenFor(idToken + "-admin", lineSubject, false);
        }
        return JsonPath.read(response, "$.accessToken");
    }
}
