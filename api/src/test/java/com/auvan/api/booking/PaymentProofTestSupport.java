package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.BookingStatus;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PaymentProofTestSupport.FakeStorageConfiguration.class)
abstract class PaymentProofTestSupport extends AuthenticationTestSupport {
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
    private SeatClaimRepository claims;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    PaymentProofRepository proofs;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private List<TripSeat> seats;
    String studentToken;
    UUID studentId;
    String bookingId;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        storage.reset();
        trip = createTrip(OffsetDateTime.now().plusDays(1));
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
        studentId = users.findByLineSubject("Ustudent").orElseThrow().getId();
        bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
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

    void assertNothingSubmitted(BookingStatus expected) {
        assertThat(proofs.count()).isZero();
        assertThat(storage.objects()).isEmpty();
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(expected));
    }

    ResultActions submit(String token, String bookingId, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/bookings/" + bookingId + "/payment-proof")
                .file(file)
                .header("Authorization", "Bearer " + token));
    }

    private Trip createTrip(OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-01", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-01", "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        return trips.save(created);
    }

    private String holdOn(TripSeat seat) throws Exception {
        String response = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[\"" + seat.getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.holdId");
    }

    private ResultActions confirm(String token, String holdId, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                        + "\"passengerPhone\":\"0812345678\"}"));
    }

    private String bookingIdFrom(ResultActions actions) throws Exception {
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
