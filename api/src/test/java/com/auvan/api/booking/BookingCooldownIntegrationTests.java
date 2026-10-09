package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.repository.BookingCooldownClearRepository;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.outbox.repository.OutboxEventRepository;
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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingCooldownIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpiryService expiry;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private BookingCooldownClearRepository clears;

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

    private Trip trip;
    private Trip otherTrip;
    private UUID student;
    private String studentToken;
    private UUID admin;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip("VAN-COOL-A");
        otherTrip = createTrip("VAN-COOL-B");
        studentToken = tokenFor("cooldown-student", "Ucooldown-student", false);
        student = users.findByLineSubject("Ucooldown-student").orElseThrow().getId();
        adminToken = tokenFor("cooldown-admin", "Ucooldown-admin", true);
        admin = users.findByLineSubject("Ucooldown-admin").orElseThrow().getId();
    }

    @AfterEach
    void clearData() {
        clears.deleteAll();
        events.deleteAll();
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
    void twoExpiriesInAWeekPauseHoldsUntilADayAfterTheLatest() throws Exception {
        expireABookingAgo(Duration.ofDays(3));
        OffsetDateTime latest = expireABookingAgo(Duration.ofHours(1));

        String body = hold(otherTrip)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_cooldown"))
                .andExpect(jsonPath("$.detail").value(containsString("you can book again from")))
                .andReturn().getResponse().getContentAsString();
        assertThat(OffsetDateTime.parse(JsonPath.read(body, "$.retryAt")))
                .isCloseTo(latest.plusHours(24), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void oneExpiryAloneDoesNotPauseBooking() throws Exception {
        expireABookingAgo(Duration.ofHours(1));

        hold(otherTrip).andExpect(status().isCreated());
    }

    @Test
    void twoExpiriesMoreThanAWeekApartDoNotPauseBooking() throws Exception {
        expireABookingAgo(Duration.ofDays(7).plusHours(12));
        expireABookingAgo(Duration.ofHours(1));

        hold(otherTrip).andExpect(status().isCreated());
    }

    @Test
    void thePauseEndsADayAfterTheLatestExpiry() throws Exception {
        expireABookingAgo(Duration.ofDays(3));
        expireABookingAgo(Duration.ofHours(25));

        hold(otherTrip).andExpect(status().isCreated());
    }

    @Test
    void cancellingYourOwnBookingsNeverCountsTowardsThePause() throws Exception {
        bookingService.cancel(student, book(trip, "key-cancel-1"));
        bookingService.cancel(student, book(trip, "key-cancel-2"));

        hold(otherTrip).andExpect(status().isCreated());
    }

    @Test
    void aHoldTakenBeforeThePauseCannotBeBookedDuringIt() throws Exception {
        expireABookingAgo(Duration.ofDays(1));
        UUID earlierHold = holds.hold(student, new CreateSeatHoldRequest(otherTrip.getId(),
                List.of(otherTrip.getSeats().getFirst().getId()))).holdId();
        expireABookingAgo(Duration.ofMinutes(1));

        confirm(earlierHold)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_cooldown"))
                .andExpect(jsonPath("$.retryAt").isNotEmpty());
    }

    @Test
    void anAdministratorCanLiftThePauseAndIsRecordedAsHavingDoneSo() throws Exception {
        expireABookingAgo(Duration.ofDays(3));
        expireABookingAgo(Duration.ofHours(1));

        clearCooldown(student, adminToken).andExpect(status().isNoContent());

        hold(otherTrip).andExpect(status().isCreated());
        assertThat(clears.findAll()).singleElement().satisfies(clear -> {
            assertThat(clear.getUserId()).isEqualTo(student);
            assertThat(clear.getClearedByUserId()).isEqualTo(admin);
            assertThat(clear.getClearedAt()).isCloseTo(OffsetDateTime.now(), within(1, ChronoUnit.MINUTES));
        });
    }

    @Test
    void onlyExpiriesAfterTheLatestClearCount() throws Exception {
        expireABookingAgo(Duration.ofDays(3));
        expireABookingAgo(Duration.ofDays(2));
        clearCooldown(student, adminToken).andExpect(status().isNoContent());
        expireABookingAgo(Duration.ZERO);

        hold(otherTrip).andExpect(status().isCreated());
    }

    @Test
    void aStudentCannotLiftTheirOwnPause() throws Exception {
        clearCooldown(student, studentToken).andExpect(status().isForbidden());

        assertThat(clears.count()).isZero();
    }

    @Test
    void liftingThePauseOfAnUnknownStudentIsNotFound() throws Exception {
        clearCooldown(UUID.randomUUID(), adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("student_not_found"));
    }

    private OffsetDateTime expireABookingAgo(Duration ago) {
        UUID bookingId = book(trip, "key-expire-" + UUID.randomUUID());
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), bookingId);
        assertThat(expiry.sweep()).isOne();
        OffsetDateTime expiredAt = OffsetDateTime.now().minus(ago);
        jdbc.update("update booking_events set created_at = ? where booking_id = ? and event_type = 'EXPIRED'",
                expiredAt, bookingId);
        return expiredAt;
    }

    private UUID book(Trip on, String key) {
        UUID holdId = holds.hold(student, new CreateSeatHoldRequest(on.getId(),
                List.of(on.getSeats().getFirst().getId()))).holdId();
        bookingService.create(student, key, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst().getId();
    }

    private ResultActions hold(Trip on) throws Exception {
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + on.getId() + "\",\"seatIds\":[\""
                        + on.getSeats().getFirst().getId() + "\"]}"));
    }

    private ResultActions confirm(UUID holdId) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings")
                .header("Authorization", "Bearer " + studentToken)
                .header("Idempotency-Key", "key-confirm-" + holdId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                        + "\"passengerPhone\":\"0812345678\"}"));
    }

    private ResultActions clearCooldown(UUID userId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/students/" + userId + "/cooldown/clear")
                .header("Authorization", "Bearer " + token));
    }

    private Trip createTrip(String vehicleCode) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode,
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
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
