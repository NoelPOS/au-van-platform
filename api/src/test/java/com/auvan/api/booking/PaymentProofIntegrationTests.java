package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
class PaymentProofIntegrationTests extends AuthenticationTestSupport {
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
    private List<TripSeat> seats;
    private String studentToken;
    private UUID studentId;
    private String bookingId;

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

    @Test
    void submittingAProofStoresTheImageAndPutsTheBookingUnderReview() throws Exception {
        submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[1].type").value("PAYMENT_PROOF_SUBMITTED"))
                .andExpect(jsonPath("$.events[1].detail").value("Payment proof submitted for review."))
                .andExpect(jsonPath("$.events[1].actorUserId").value(studentId.toString()));

        List<PaymentProof> stored = proofs.findAll();
        assertThat(stored).singleElement().satisfies(proof -> {
            assertThat(proof.getSubmittedByUserId()).isEqualTo(studentId);
            assertThat(proof.getContentType()).isEqualTo("image/jpeg");
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
            assertThat(proof.getSizeBytes()).isEqualTo("the-slip".length());
            assertThat(proof.getObjectKey()).startsWith("payment-proofs/" + bookingId + "/").endsWith(".jpg");
        });
        assertThat(storage.objects()).containsOnlyKeys(stored.getFirst().getObjectKey());
        assertThat(storage.objects().get(stored.getFirst().getObjectKey()).content())
                .isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"));
    }

    @Test
    void theResponseCarriesNoObjectKeyBucketOrUrl() throws Exception {
        String body = submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("payment-proofs/")
                .doesNotContain("test-payment-proofs")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    @Test
    void submittingAgainstAnotherStudentsBookingIsNotFoundAndChangesNothing() throws Exception {
        String otherToken = tokenFor("other-token", "Uother");

        submit(otherToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void submittingAgainstABookingThatDoesNotExistAnswersIdentically() throws Exception {
        submit(studentToken, UUID.randomUUID().toString(), jpeg("the-slip"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void aSecondProofWhileTheFirstIsUnderReviewIsRejected() throws Exception {
        submit(studentToken, bookingId, jpeg("the-slip")).andExpect(status().isOk());

        submit(studentToken, bookingId, jpeg("another-slip"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_awaiting_payment"));

        assertThat(proofs.count()).isOne();
        assertThat(storage.objects()).hasSize(1);
    }

    @Test
    void submittingAgainstACancelledBookingIsRejected() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/bookings/" + bookingId + "/cancel")))
                .andExpect(status().isOk());

        submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_awaiting_payment"));

        assertNothingSubmitted(BookingStatus.CANCELLED);
    }

    @Test
    void aFileThatIsNotAnAllowedImageIsRejected() throws Exception {
        submit(studentToken, bookingId,
                new MockMultipartFile("file", "slip.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_type_not_supported"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void anImageOverTheCeilingIsRejected() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];

        submit(studentToken, bookingId, new MockMultipartFile("file", "slip.jpg", "image/jpeg", oversized))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_too_large"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void anEmptyFileIsRejected() throws Exception {
        submit(studentToken, bookingId, new MockMultipartFile("file", "slip.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_empty"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void anAnonymousSubmissionIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/v1/bookings/" + bookingId + "/payment-proof").file(jpeg("the-slip")))
                .andExpect(status().isUnauthorized());

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void aStorageFailureLeavesTheBookingExactlyAsItWas() throws Exception {
        storage.failEveryStore();

        String body = submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("payment_proof_storage_unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException").doesNotContain("unreachable");
        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    private void assertNothingSubmitted(BookingStatus expected) {
        assertThat(proofs.count()).isZero();
        assertThat(storage.objects()).isEmpty();
        assertThat(bookings.findAll()).singleElement()
                .satisfies(booking -> assertThat(booking.getStatus()).isEqualTo(expected));
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions submit(String token, String bookingId, MockMultipartFile file) throws Exception {
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
        created.update(departureAt, TripStatus.ACTIVE);
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

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
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
