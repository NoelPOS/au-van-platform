package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
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
import org.springframework.dao.DataIntegrityViolationException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentProofReviewIntegrationTests extends AuthenticationTestSupport {
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
    private List<TripSeat> seats;
    private String studentToken;
    private String adminToken;
    private UUID studentId;
    private UUID adminId;
    private String bookingId;

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

    @Test
    void theQueueListsSubmittedProofsOldestFirstWithTheBookingContextToDecideOn() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        Trip laterTrip = createTrip("VAN-02", OffsetDateTime.now().plusDays(2));
        String secondBooking = bookingIdFrom(confirm(holdOn(laterTrip.getSeats().getFirst()), "key-2"));
        submit(secondBooking, jpeg("another-slip"));

        queue().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[1].bookingId").value(secondBooking))
                .andExpect(jsonPath("$[0].bookingReference").isNotEmpty())
                .andExpect(jsonPath("$[0].passengerName").value("Somchai P."))
                .andExpect(jsonPath("$[0].passengerPhone").value("0812345678"))
                .andExpect(jsonPath("$[0].totalFare").value(35.00))
                .andExpect(jsonPath("$[0].trip.origin").value("AU"))
                .andExpect(jsonPath("$[0].trip.destination").value("Asok"))
                .andExpect(jsonPath("$[0].trip.departureAt").isNotEmpty())
                .andExpect(jsonPath("$[0].submittedByUserId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].sizeBytes").value("the-slip".length()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].submittedAt").isNotEmpty());

        approve(proofIdOf(bookingId), null).andExpect(status().isOk());

        queue().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(secondBooking));
    }

    @Test
    void theQueueCarriesNoObjectKeyBucketOrUrl() throws Exception {
        submit(bookingId, jpeg("the-slip"));

        String body = queue().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("payment-proofs/")
                .doesNotContain("test-payment-proofs")
                .doesNotContain("objectKey")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    @Test
    void theImageEndpointReturnsTheExactBytesTheStudentUploaded() throws Exception {
        submit(bookingId, jpeg("the-slip"));

        byte[] returned = mockMvc.perform(get(image(proofIdOf(bookingId))).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(returned).isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void approvingConfirmsTheBookingAndRecordsWhoDecidedItAndWhen() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        approve(proofId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[2].type").value("PAYMENT_APPROVED"))
                .andExpect(jsonPath("$.events[2].actorUserId").value(adminId.toString()));

        PaymentProof decided = proofs.findById(proofId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(decided.getReviewedByUserId()).isEqualTo(adminId);
        assertThat(decided.getReviewedAt()).isNotNull();
        assertThat(decided.getReviewNote()).isNull();
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectingRecordsTheReasonAndLeavesTheSeatsHeld() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        reject(proofId, "The slip is too blurred to read.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[2].type").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.events[2].detail").value("The slip is too blurred to read."))
                .andExpect(jsonPath("$.events[2].actorUserId").value(adminId.toString()));

        PaymentProof decided = proofs.findById(proofId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(PaymentProofStatus.REJECTED);
        assertThat(decided.getReviewNote()).isEqualTo("The slip is too blurred to read.");
        assertThat(claims.findByBookingId(UUID.fromString(bookingId))).hasSize(1);
    }

    @Test
    void aStudentCanSubmitAnotherProofAfterARejectionAndTheOldOneStaysAsHistory() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID rejectedId = proofIdOf(bookingId);
        reject(rejectedId, "The slip is too blurred to read.").andExpect(status().isOk());

        submit(bookingId, jpeg("a-clearer-slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"));

        assertThat(proofs.count()).isEqualTo(2);
        assertThat(storage.objects()).hasSize(2);
        PaymentProof rejected = proofs.findById(rejectedId).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(PaymentProofStatus.REJECTED);
        assertThat(rejected.getReviewNote()).isEqualTo("The slip is too blurred to read.");
        String body = queue().andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID waiting = UUID.fromString(JsonPath.read(body, "$[0].id"));
        assertThat(waiting).isNotEqualTo(rejectedId);

        approve(waiting, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void aStudentTokenIsForbiddenOnEveryReviewEndpoint() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(get("/api/v1/admin/payment-proofs").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(image(proofId)).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(decision(proofId, "approve", "{}").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(decision(proofId, "reject", "{\"note\":\"no\"}")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        assertNothingWasDecided();
    }

    @Test
    void anAnonymousRequestIsUnauthorizedOnEveryReviewEndpoint() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(get("/api/v1/admin/payment-proofs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(image(proofId))).andExpect(status().isUnauthorized());
        mockMvc.perform(decision(proofId, "approve", "{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(decision(proofId, "reject", "{\"note\":\"no\"}")).andExpect(status().isUnauthorized());

        assertNothingWasDecided();
    }

    @Test
    void aProofThatDoesNotExistIsNotFoundOnEveryDecisionEndpoint() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(image(unknown)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
        approve(unknown, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
        reject(unknown, "No.").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
    }

    @Test
    void aProofThatWasAlreadyDecidedCannotBeDecidedAgain() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        approve(proofId, null).andExpect(status().isOk());

        approve(proofId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));
        reject(proofId, "Changed my mind.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));

        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aRejectedProofCannotBeRejectedAgain() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        reject(proofId, "The slip is too blurred to read.").andExpect(status().isOk());

        reject(proofId, "Still blurred.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));

        assertThat(proofs.findById(proofId).orElseThrow().getReviewNote())
                .isEqualTo("The slip is too blurred to read.");
    }

    @Test
    void aProofWhoseBookingTheStudentCancelledCannotBeApproved() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        approve(proofId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_under_review"));

        assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
    }

    @Test
    void rejectingWithoutSayingWhyIsRefused() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(decision(proofId, "reject", "{}").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_required"));
        reject(proofId, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_required"));

        assertNothingWasDecided();
    }

    @Test
    void aNoteLongerThanTheColumnIsRefusedRatherThanTruncated() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        reject(proofId, "n".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_too_long"));
        approve(proofId, "n".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_too_long"));

        assertNothingWasDecided();
        reject(proofId, "n".repeat(500)).andExpect(status().isOk());
    }

    @Test
    void anImageThatCannotBeReadAnswersWithoutNamingTheBucket() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        storage.failEveryLoad();

        String body = mockMvc.perform(get(image(proofIdOf(bookingId)))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("payment_proof_unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException").doesNotContain("unreachable")
                .doesNotContain("bucket");
    }

    @Test
    void theSchemaRefusesADecidedProofThatDoesNotSayWhoDecidedIt() {
        insertProof("SUBMITTED");

        assertThatThrownBy(() -> insertProof("APPROVED")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProof("REJECTED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertProof(String status) {
        jdbc.update("""
                insert into payment_proofs (id, booking_id, submitted_by_user_id, object_key, content_type,
                                            size_bytes, status, created_at)
                values (?, ?, ?, ?, 'image/jpeg', 12, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(bookingId), studentId,
                "payment-proofs/raw/" + UUID.randomUUID() + ".jpg", status, OffsetDateTime.now());
    }

    private void assertNothingWasDecided() {
        assertThat(proofs.findAll()).allSatisfy(proof -> {
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
            assertThat(proof.getReviewedByUserId()).isNull();
            assertThat(proof.getReviewedAt()).isNull();
        });
        assertThat(bookings.findAll()).allSatisfy(booking ->
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW));
    }

    private ResultActions queue() throws Exception {
        return mockMvc.perform(get("/api/v1/admin/payment-proofs").header("Authorization", bearer(adminToken)));
    }

    private ResultActions approve(UUID proofId, String note) throws Exception {
        return mockMvc.perform(decision(proofId, "approve", body(note)).header("Authorization", bearer(adminToken)));
    }

    private ResultActions reject(UUID proofId, String note) throws Exception {
        return mockMvc.perform(decision(proofId, "reject", body(note)).header("Authorization", bearer(adminToken)));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder decision(
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

    private static String image(UUID proofId) {
        return "/api/v1/admin/payment-proofs/" + proofId + "/image";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private UUID proofIdOf(String booking) throws Exception {
        String body = queue().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(body, "$[?(@.bookingId == '" + booking + "')].id");
        return UUID.fromString(ids.getLast());
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private ResultActions submit(String booking, MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/v1/bookings/" + booking + "/payment-proof")
                        .file(file)
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt) {
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

    private String holdOn(TripSeat seat) throws Exception {
        String response = mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + seat.getTrip().getId() + "\",\"seatIds\":[\"" + seat.getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.holdId");
    }

    private ResultActions confirm(String holdId, String key) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("Authorization", bearer(studentToken))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                        + "\"passengerPhone\":\"0812345678\"}"));
    }

    private String bookingIdFrom(ResultActions actions) throws Exception {
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
