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
import com.auvan.api.booking.service.PaymentProofFile;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ReusedPaymentSlipIntegrationTests extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InMemoryPaymentProofStorage storage;

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
    private SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private String adminToken;
    private String firstStudent;
    private String secondStudent;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        storage.reset();
        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1));
        adminToken = tokenFor("admin-token", "Uadmin", true);
        firstStudent = tokenFor("first-token", "Ufirst", false);
        secondStudent = tokenFor("second-token", "Usecond", false);
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

    @Test
    void eachSubmissionStoresTheSha256OfTheBytesTheStudentSent() throws Exception {
        String booking = book(firstStudent, trip, 0, "key-1");

        submit(firstStudent, booking, "the-slip");

        assertThat(proofs.findAll()).singleElement().satisfies(proof -> assertThat(proof.getContentSha256())
                .isEqualTo(PaymentProofFile.sha256("the-slip".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void aSlipAnotherStudentAlreadySentIsFlaggedOnBothBookings() throws Exception {
        String first = book(firstStudent, trip, 0, "key-1");
        String second = book(secondStudent, trip, 1, "key-2");
        submit(firstStudent, first, "the-slip");
        submit(secondStudent, second, "the-slip");

        queue().andExpect(jsonPath("$[0].sameSlipBookings.length()").value(1))
                .andExpect(jsonPath("$[0].sameSlipBookings[0].bookingId").value(second))
                .andExpect(jsonPath("$[0].sameSlipBookings[0].bookingReference").value(referenceOf(second)))
                .andExpect(jsonPath("$[1].sameSlipBookings.length()").value(1))
                .andExpect(jsonPath("$[1].sameSlipBookings[0].bookingId").value(first))
                .andExpect(jsonPath("$[1].sameSlipBookings[0].bookingReference").value(referenceOf(first)));
    }

    @Test
    void oneStudentSendingTheSameSlipForTwoBookingsIsFlagged() throws Exception {
        String first = book(firstStudent, trip, 0, "key-1");
        submit(firstStudent, first, "the-slip");
        String second = book(firstStudent, createTrip("VAN-02", OffsetDateTime.now().plusDays(2)), 0, "key-2");
        submit(firstStudent, second, "the-slip");

        queue().andExpect(jsonPath("$[1].bookingId").value(second))
                .andExpect(jsonPath("$[1].sameSlipBookings.length()").value(1))
                .andExpect(jsonPath("$[1].sameSlipBookings[0].bookingId").value(first));
    }

    @Test
    void resendingTheSameSlipOnOneBookingAfterASendBackIsNotFlagged() throws Exception {
        String booking = book(firstStudent, trip, 0, "key-1");
        submit(firstStudent, booking, "the-slip");
        reject(waitingProofId());
        submit(firstStudent, booking, "the-slip");

        queue().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sameSlipBookings").isEmpty());
    }

    @Test
    void aBookingThatSentTheSlipTwiceIsNamedOnce() throws Exception {
        String first = book(firstStudent, trip, 0, "key-1");
        submit(firstStudent, first, "the-slip");
        reject(waitingProofId());
        submit(firstStudent, first, "the-slip");
        String second = book(secondStudent, trip, 1, "key-2");
        submit(secondStudent, second, "the-slip");

        queue().andExpect(jsonPath("$[1].bookingId").value(second))
                .andExpect(jsonPath("$[1].sameSlipBookings.length()").value(1))
                .andExpect(jsonPath("$[1].sameSlipBookings[0].bookingId").value(first));
    }

    @Test
    void differentSlipsAreNotFlagged() throws Exception {
        submit(firstStudent, book(firstStudent, trip, 0, "key-1"), "one-slip");
        submit(secondStudent, book(secondStudent, trip, 1, "key-2"), "another-slip");

        queue().andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sameSlipBookings").isEmpty())
                .andExpect(jsonPath("$[1].sameSlipBookings").isEmpty());
    }

    @Test
    void proofsSentBeforeSlipsWereHashedAreNeverMatchedWithEachOther() throws Exception {
        String first = book(firstStudent, trip, 0, "key-1");
        String second = book(secondStudent, trip, 1, "key-2");
        submit(firstStudent, first, "the-slip");
        submit(secondStudent, second, "the-slip");
        jdbc.update("update payment_proofs set content_sha256 = null");

        queue().andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].sameSlipBookings").isEmpty())
                .andExpect(jsonPath("$[1].sameSlipBookings").isEmpty());
    }

    private ResultActions queue() throws Exception {
        return mockMvc.perform(get("/api/v1/admin/payment-proofs").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk());
    }

    private UUID waitingProofId() throws Exception {
        String body = queue().andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$[0].id"));
    }

    private void reject(UUID proofId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/payment-proofs/" + proofId + "/reject")
                        .header("Authorization", bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"The slip is too blurred to read.\"}"))
                .andExpect(status().isOk());
    }

    private String referenceOf(String bookingId) {
        return bookings.findById(UUID.fromString(bookingId)).orElseThrow().getReference();
    }

    private void submit(String token, String booking, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "slip.jpg", "image/jpeg",
                content.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/bookings/" + booking + "/payment-proof")
                        .file(file)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    private String book(String token, Trip on, int seat, String key) throws Exception {
        String hold = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + on.getId() + "\",\"seatIds\":[\""
                                + on.getSeats().get(seat).getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String booking = mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", bearer(token))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + JsonPath.read(hold, "$.holdId") + "\","
                                + "\"passengerName\":\"Somchai P.\",\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(booking, "$.id");
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, departureAt));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
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
