package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofReviewService;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.WaitlistPromotionService;
import com.auvan.api.booking.service.WaitlistService;
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
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UnpaidBookingRuleIntegrationTests extends AuthenticationTestSupport {
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

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private WaitlistPromotionService promotion;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private WaitlistEntryRepository waitlist;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private OutboxEventRepository events;

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

    private Trip tripA;
    private Trip tripB;
    private UUID student;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        tripA = createTrip("VAN-UNPAID-A");
        tripB = createTrip("VAN-UNPAID-B");
        token = tokenFor("unpaid-token", "Uunpaid-student");
        student = users.findByLineSubject("Uunpaid-student").orElseThrow().getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        proofs.deleteAll();
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
    void anUnpaidBookingBlocksAHoldOnAnotherTripAndNamesTheBooking() throws Exception {
        Booking unpaid = book(tripA, 0);

        mockMvc.perform(post("/api/v1/seat-holds")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + tripB.getId() + "\",\"seatIds\":[\""
                                + seat(tripB, 0).getId() + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("unpaid_booking_exists"))
                .andExpect(jsonPath("$.detail").value(containsString(unpaid.getReference())))
                .andExpect(jsonPath("$.bookingId").value(unpaid.getId().toString()))
                .andExpect(jsonPath("$.bookingReference").value(unpaid.getReference()));
        assertThat(claims.findHoldsOnTripBy(tripB.getId(), student)).isEmpty();
    }

    @Test
    void aSlipThatWasSentBackStillCountsAsUnpaid() {
        Booking rejected = book(tripA, 0);
        paymentProofs.submit(student, rejected.getId(), jpeg());
        UUID administrator = users.save(new AppUser("Uunpaid-admin", "Administrator")).getId();
        review.reject(administrator, proofs.findAll().getFirst().getId(), "The slip is unreadable.");

        assertRefused(() -> hold(tripB, 0), "unpaid_booking_exists");
    }

    @Test
    void aBookingUnderReviewDoesNotStopTheNextOne() {
        Booking underReview = book(tripA, 0);
        paymentProofs.submit(student, underReview.getId(), jpeg());

        assertThat(book(tripB, 0).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void cancellingTheUnpaidBookingLetsTheStudentBookAgain() {
        bookingService.cancel(student, book(tripA, 0).getId());

        assertThat(book(tripB, 0).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void aHoldTakenBeforeAnotherBookingCannotBeBookedWhileThatBookingIsUnpaid() {
        UUID holdOnB = hold(tripB, 0);
        book(tripA, 0);

        assertRefused(() -> confirm(holdOnB, "key-second"), "unpaid_booking_exists");
        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(student)).hasSize(1);
    }

    @Test
    void aWaitlistPromotionCannotBeBookedWhileAnotherBookingIsUnpaid() {
        UUID holder = users.save(new AppUser("Uunpaid-holder", "Holder")).getId();
        tripA.getSeats().forEach(seat -> claims.save(
                new SeatClaim(seat, holder, UUID.randomUUID(), OffsetDateTime.now().plusHours(2))));
        UUID entryId = waitlistService.join(student, new JoinWaitlistRequest(tripA.getId(), 1)).id();
        book(tripB, 0);
        jdbc.update("update seat_claims set expires_at = ? where user_id = ?",
                OffsetDateTime.now().minusMinutes(1), holder);
        assertThat(promotion.sweep()).isOne();
        UUID promotionHold = waitlist.findById(entryId).orElseThrow().getPromotionHoldId();

        assertRefused(() -> confirm(promotionHold, "key-promoted"), "unpaid_booking_exists");
    }

    private Booking book(Trip trip, int seatIndex) {
        confirm(hold(trip, seatIndex), "key-" + trip.getId() + "-" + seatIndex);
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst();
    }

    private UUID hold(Trip trip, int seatIndex) {
        return holds.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(seat(trip, seatIndex).getId())))
                .holdId();
    }

    private void confirm(UUID holdId, String key) {
        bookingService.create(student, key, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
    }

    private static TripSeat seat(Trip trip, int index) {
        return trip.getSeats().get(index);
    }

    private static void assertRefused(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
            assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refusal.getBody().getProperties()).containsEntry("code", code);
        });
    }

    private Trip createTrip(String vehicleCode) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode,
                List.of(new SeatLayoutSeat("A1", 1, 1), new SeatLayoutSeat("A2", 1, 2))));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", "the-slip".getBytes(StandardCharsets.UTF_8));
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
