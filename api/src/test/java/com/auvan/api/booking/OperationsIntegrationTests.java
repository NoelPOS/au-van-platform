package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
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
import java.time.OffsetDateTime;
import java.util.ArrayList;
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
class OperationsIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

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
    private WaitlistEntryRepository waitlist;

    @Autowired
    private OutboxEventRepository outbox;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private Trip otherTrip;
    private String studentToken;
    private String adminToken;
    private UUID firstStudent;
    private UUID secondStudent;
    private UUID thirdStudent;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip("VAN-OP1", OffsetDateTime.now().plusDays(1));
        otherTrip = createTrip("VAN-OP2", OffsetDateTime.now().plusDays(2));
        studentToken = tokenFor("student-token", "Ustudent", false);
        adminToken = tokenFor("admin-token", "Uadmin", true);
        firstStudent = users.findByLineSubject("Ustudent").orElseThrow().getId();
        secondStudent = users.save(new AppUser("Usecond", "Malee K.")).getId();
        thirdStudent = users.save(new AppUser("Uthird", "Nattapong S.")).getId();
    }

    @AfterEach
    void clearUp() {
        outbox.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
    }

    @Test
    void theTripViewReportsBookingsByStatusAndTheQueueWithItsPromotions() throws Exception {
        book(trip, firstStudent, "AUV-OP-0001", BookingStatus.CONFIRMED);
        book(trip, secondStudent, "AUV-OP-0002", BookingStatus.PENDING_PAYMENT);
        book(trip, thirdStudent, "AUV-OP-0003", BookingStatus.CANCELLED);
        book(trip, firstStudent, "AUV-OP-0004", BookingStatus.CANCELLED);
        UUID waiting = queue(trip, firstStudent, 1, OffsetDateTime.now().minusMinutes(30));
        UUID promoted = queue(trip, secondStudent, 2, OffsetDateTime.now().minusMinutes(20));
        UUID withdrawn = queue(trip, thirdStudent, 1, OffsetDateTime.now().minusMinutes(10));
        UUID holdId = UUID.randomUUID();
        OffsetDateTime promotionExpiresAt = OffsetDateTime.now().plusMinutes(25);
        promote(promoted, holdId, promotionExpiresAt);
        withdraw(withdrawn);

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.origin").value("AU"))
                .andExpect(jsonPath("$.destination").value("Asok"))
                .andExpect(jsonPath("$.tripStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.totalSeats").value(2))
                .andExpect(jsonPath("$.bookingsByStatus.length()").value(5))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CANCELLED')].count").value(2))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_UNDER_REVIEW')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_REJECTED')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(3))
                .andExpect(jsonPath("$.waitlist[0].entryId").value(waiting.toString()))
                .andExpect(jsonPath("$.waitlist[0].status").value("WAITING"))
                .andExpect(jsonPath("$.waitlist[0].position").value(1))
                .andExpect(jsonPath("$.waitlist[0].seatsWanted").value(1))
                .andExpect(jsonPath("$.waitlist[0].promotionHoldId").doesNotExist())
                .andExpect(jsonPath("$.waitlist[1].entryId").value(promoted.toString()))
                .andExpect(jsonPath("$.waitlist[1].status").value("PROMOTED"))
                .andExpect(jsonPath("$.waitlist[1].position").value(2))
                .andExpect(jsonPath("$.waitlist[1].displayName").value("Malee K."))
                .andExpect(jsonPath("$.waitlist[1].promotionHoldId").value(holdId.toString()))
                .andExpect(jsonPath("$.waitlist[1].promotionExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.waitlist[2].entryId").value(withdrawn.toString()))
                .andExpect(jsonPath("$.waitlist[2].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.waitlist[2].position").doesNotExist());
    }

    @Test
    void claimedSeatsCountsOnlyTheClaimsThatStillBlockTheirSeat() throws Exception {
        UUID holder = users.save(new AppUser("Uholder", "Holding Student")).getId();
        claims.save(new SeatClaim(trip.getSeats().get(0), holder, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(2)));
        claims.save(new SeatClaim(trip.getSeats().get(1), holder, UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1)));

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(2))
                .andExpect(jsonPath("$.claimedSeats").value(1));
    }

    @Test
    void theDeadLetterViewReportsOnlyTheRowsTheDispatcherGaveUpOn() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID aggregate = UUID.randomUUID();
        UUID dead = deadLetter(aggregate, firstStudent, "LINE refused the push: 400 invalid recipient.");
        outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), secondStudent,
                "{}", now));
        UUID inFlight = outbox.save(new OutboxEvent(OutboxEventType.PAYMENT_APPROVED, UUID.randomUUID(),
                thirdStudent, "{}", now.minusMinutes(1))).getId();
        assertThat(outbox.claim(inFlight, now, now.plusMinutes(5))).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(dead.toString()))
                .andExpect(jsonPath("$[0].eventType").value("BOOKING_CANCELLED"))
                .andExpect(jsonPath("$[0].aggregateId").value(aggregate.toString()))
                .andExpect(jsonPath("$[0].recipientUserId").value(firstStudent.toString()))
                .andExpect(jsonPath("$[0].attempts").value(1))
                .andExpect(jsonPath("$[0].lastError").value("LINE refused the push: 400 invalid recipient."))
                .andExpect(jsonPath("$[0].processedAt").isNotEmpty());
    }

    @Test
    void theTripViewReportsThisTripsBookingsAndQueueAndNotAnotherTrips() throws Exception {
        book(trip, firstStudent, "AUV-OP-0005", BookingStatus.CONFIRMED);
        book(otherTrip, secondStudent, "AUV-OP-0006", BookingStatus.CONFIRMED);
        book(otherTrip, thirdStudent, "AUV-OP-0007", BookingStatus.PENDING_PAYMENT);
        UUID mine = queue(trip, firstStudent, 1, OffsetDateTime.now().minusMinutes(5));
        queue(otherTrip, secondStudent, 1, OffsetDateTime.now().minusMinutes(4));
        queue(otherTrip, thirdStudent, 1, OffsetDateTime.now().minusMinutes(3));

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(1))
                .andExpect(jsonPath("$.waitlist[0].entryId").value(mine.toString()));

        tripView(otherTrip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(1))
                .andExpect(jsonPath("$.waitlist.length()").value(2));
    }

    @Test
    void aTripWithNothingHappeningReportsEmptyRatherThanFailing() throws Exception {
        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedSeats").value(0))
                .andExpect(jsonPath("$.bookingsByStatus.length()").value(5))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CANCELLED')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aStudentTokenIsForbiddenOnEveryOperationsEndpoint() throws Exception {
        mockMvc.perform(get(tripPath(trip)).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousRequestIsUnauthorizedOnEveryOperationsEndpoint() throws Exception {
        mockMvc.perform(get(tripPath(trip))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/trips/" + UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    private void clearData() {
        outbox.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    private ResultActions tripView(Trip subject) throws Exception {
        return mockMvc.perform(get(tripPath(subject)).header("Authorization", bearer(adminToken)));
    }

    private static String tripPath(Trip subject) {
        return "/api/v1/admin/operations/trips/" + subject.getId();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private void book(Trip subject, UUID userId, String reference, BookingStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = new Booking(subject, userId, reference, "Somchai P.", "0812345678",
                new BigDecimal("35.00"), now.plusMinutes(30), now);
        switch (status) {
            case PENDING_PAYMENT -> { }
            case PAYMENT_UNDER_REVIEW -> booking.markPaymentUnderReview(now);
            case PAYMENT_REJECTED -> booking.markPaymentRejected(now.plusMinutes(30), now);
            case CONFIRMED -> booking.confirm(now);
            case CANCELLED -> booking.cancel(now);
        }
        bookings.save(booking);
    }

    private UUID queue(Trip subject, UUID userId, int seatsWanted, OffsetDateTime joinedAt) {
        return waitlist.saveAndFlush(new WaitlistEntry(subject, userId, seatsWanted, joinedAt)).getId();
    }

    private void promote(UUID entryId, UUID holdId, OffsetDateTime expiresAt) {
        jdbc.update("update waitlist_entries set status = ?, promotion_hold_id = ?, promotion_expires_at = ?, "
                        + "updated_at = ? where id = ?",
                WaitlistStatus.PROMOTED.name(), holdId, expiresAt, OffsetDateTime.now(), entryId);
    }

    private void withdraw(UUID entryId) {
        WaitlistEntry entry = waitlist.findById(entryId).orElseThrow();
        entry.withdraw(OffsetDateTime.now());
        waitlist.saveAndFlush(entry);
    }

    // Recorded a minute back: the column keeps less precision than the clock, so a row
    // written and claimed at the same now can read as not yet due.
    private UUID deadLetter(UUID aggregateId, UUID recipient, String error) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CANCELLED, aggregateId, recipient,
                "{\"reference\":\"AUV-OP-0001\"}", now.minusMinutes(1))).getId();
        assertThat(outbox.claim(id, now, now.plusMinutes(5))).isEqualTo(1);
        assertThat(outbox.markDead(id, now, error)).isEqualTo(1);
        return id;
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        return trips.save(created);
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
