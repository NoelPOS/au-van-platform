package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.booking.repository.BookingRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefundIntegrationTests extends AuthenticationTestSupport {
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
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    private Trip trip;
    private String studentToken;
    private UUID studentId;
    private String adminToken;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip();
        studentToken = tokenFor("refund-student", "Urefund-student", false);
        studentId = users.findByLineSubject("Urefund-student").orElseThrow().getId();
        adminToken = tokenFor("refund-admin", "Urefund-admin", true);
        adminId = users.findByLineSubject("Urefund-admin").orElseThrow().getId();
    }

    @AfterEach
    void clearData() {
        claims.deleteAll();
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void aConfirmedBookingCancelledByItsStudentOwesARefundAndIsListedForAdministrators() throws Exception {
        Booking booking = book(true);

        cancel(booking)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("DUE"))
                .andExpect(jsonPath("$.events[1].detail")
                        .value("Cancelled and released seats A1. A refund of 35.00 THB is due."));

        mockMvc.perform(get("/api/v1/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(booking.getId().toString()))
                .andExpect(jsonPath("$[0].totalFare").value(35.00))
                .andExpect(jsonPath("$[0].refundStatus").value("DUE"));
    }

    @Test
    void anUnpaidBookingCancelledByItsStudentOwesNothing() throws Exception {
        Booking booking = book(false);

        cancel(booking)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("NONE"));

        mockMvc.perform(get("/api/v1/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anAdministratorRecordsWhoRefundedWhenAndWhy() throws Exception {
        Booking booking = book(true);
        cancel(booking).andExpect(status().isOk());

        markRefunded(booking, "{\"note\":\"  PromptPay transfer 4411  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.refundedByUserId").value(adminId.toString()))
                .andExpect(jsonPath("$.refundedAt").isNotEmpty())
                .andExpect(jsonPath("$.refundNote").value("PromptPay transfer 4411"))
                .andExpect(jsonPath("$.events[2].type").value("REFUNDED"))
                .andExpect(jsonPath("$.events[2].detail").value("Refunded 35.00 THB. PromptPay transfer 4411"))
                .andExpect(jsonPath("$.events[2].actorUserId").value(adminId.toString()));

        mockMvc.perform(get("/api/v1/admin/refunds").header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/v1/bookings/" + booking.getId()).header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$.refundStatus").value("REFUNDED"));
    }

    @Test
    void aRefundCanBeRecordedWithoutANote() throws Exception {
        Booking booking = book(true);
        cancel(booking).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/refunds/" + booking.getId() + "/mark-refunded")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundStatus").value("REFUNDED"))
                .andExpect(jsonPath("$.refundNote").doesNotExist())
                .andExpect(jsonPath("$.events[2].detail").value("Refunded 35.00 THB."));
    }

    @Test
    void aBookingThatOwesNothingCannotBeMarkedRefunded() throws Exception {
        Booking booking = book(false);
        cancel(booking).andExpect(status().isOk());

        markRefunded(booking, "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("refund_not_due"));
        assertThat(refundOf(booking)).isEqualTo(RefundStatus.NONE);
    }

    @Test
    void aRefundCannotBeRecordedTwice() throws Exception {
        Booking booking = book(true);
        cancel(booking).andExpect(status().isOk());
        markRefunded(booking, "{}").andExpect(status().isOk());

        markRefunded(booking, "{\"note\":\"again\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("refund_already_recorded"));
        assertThat(bookings.findById(booking.getId()).orElseThrow().getRefundNote()).isNull();
    }

    @Test
    void aNoteLongerThanFiveHundredCharactersIsRefused() throws Exception {
        Booking booking = book(true);
        cancel(booking).andExpect(status().isOk());

        markRefunded(booking, "{\"note\":\"" + "x".repeat(501) + "\"}").andExpect(status().isBadRequest());
        assertThat(refundOf(booking)).isEqualTo(RefundStatus.DUE);
    }

    @Test
    void anUnknownBookingIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/admin/refunds/" + UUID.randomUUID() + "/mark-refunded")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
    }

    @Test
    void studentsCanNeitherListNorRecordRefunds() throws Exception {
        Booking booking = book(true);
        cancel(booking).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/refunds").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/refunds/" + booking.getId() + "/mark-refunded")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        assertThat(refundOf(booking)).isEqualTo(RefundStatus.DUE);
    }

    private RefundStatus refundOf(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow().getRefundStatus();
    }

    private ResultActions cancel(Booking booking) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + booking.getId() + "/cancel")
                .header("Authorization", "Bearer " + studentToken));
    }

    private ResultActions markRefunded(Booking booking, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/refunds/" + booking.getId() + "/mark-refunded")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Booking book(boolean confirmed) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = new Booking(trip, studentId, "AUV-261009-" + UUID.randomUUID().toString().substring(0, 8),
                "Somchai P.", "0812345678", new BigDecimal("35.00"), now.plusHours(2), now);
        booking.addSeat(trip.getSeats().getFirst());
        booking.recordEvent(BookingEventType.CREATED, "Booked seats A1.", studentId, now);
        if (confirmed) {
            booking.confirm(now);
        }
        return bookings.save(booking);
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-REFUND",
                List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-REFUND", "Toyota Commuter", layout));
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
            var user = users.findByLineSubject(lineSubject).orElseThrow();
            user.promoteToAdmin();
            users.save(user);
            return tokenFor(idToken + "-admin", lineSubject, false);
        }
        return JsonPath.read(response, "$.accessToken");
    }
}
