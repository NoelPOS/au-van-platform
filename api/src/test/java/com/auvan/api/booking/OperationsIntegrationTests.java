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
import com.auvan.api.inventory.entity.TripStatus;
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

/**
 * The administrator's operational view end to end: a trip's bookings by
 * status, the queue standing behind it with whatever promotions it holds, and
 * the outbound work the dispatcher gave up on.
 *
 * <p>The promotion sweep that would produce a {@code PROMOTED} entry in a
 * running application is issue #69 and does not exist on this branch. The
 * fixture writes the status and the two promotion columns directly, which is
 * exactly what this view has to cope with: it reports whatever the columns say
 * rather than assuming any particular state is unreachable.
 */
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

    /**
     * Waitlist rows, claims and bookings all point at trips, so one left behind
     * blocks the {@code trips.deleteAll()} every other integration test starts
     * with — the same reason {@link WaitlistIntegrationTests} clears up after
     * itself.
     */
    @AfterEach
    void clearUp() {
        outbox.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
    }

    // Success paths

    /**
     * The whole of acceptance criterion 6 for a trip, against a fixture that
     * carries all three shapes at once: bookings spread across statuses, a
     * queue holding a waiting entry, a promoted one and a withdrawn one, and a
     * dead letter in the outbox.
     */
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
                // Every status is reported, the empty ones included, so an
                // operator never has to tell "none" from "not shown".
                .andExpect(jsonPath("$.bookingsByStatus.length()").value(5))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CANCELLED')].count").value(2))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_UNDER_REVIEW')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_REJECTED')].count").value(0))
                // Join order, ended entries included, positions counting only
                // the queued ones — the same rule the student's own read uses.
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
                // Withdrawn: still listed, because an operator asking "where did
                // they go" needs to see it, and holding no place at all.
                .andExpect(jsonPath("$.waitlist[2].entryId").value(withdrawn.toString()))
                .andExpect(jsonPath("$.waitlist[2].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.waitlist[2].position").doesNotExist());
    }

    /**
     * A claim that has simply lapsed does not block its seat, and this view
     * counts claimed seats by the same rule the seat map does — an operator
     * reading a trip as full while a student books the seat it was not counting
     * is the whole point of there being one predicate rather than two.
     */
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

    /**
     * The dead-letter view is the other half of criterion 6, and it reports
     * only the rows the dispatcher actually gave up on: a {@code PENDING} row
     * is waiting its turn and an {@code IN_FLIGHT} one is being sent right now,
     * and sending an operator after either is sending them after work that is
     * going to arrive on its own.
     */
    @Test
    void theDeadLetterViewReportsOnlyTheRowsTheDispatcherGaveUpOn() throws Exception {
        UUID aggregate = UUID.randomUUID();
        UUID dead = deadLetter(aggregate, firstStudent, "LINE refused the push: 400 invalid recipient.");
        outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), secondStudent,
                "{}", OffsetDateTime.now()));
        UUID inFlight = outbox.save(new OutboxEvent(OutboxEventType.PAYMENT_APPROVED, UUID.randomUUID(),
                thirdStudent, "{}", OffsetDateTime.now())).getId();
        outbox.claim(inFlight, OffsetDateTime.now(), OffsetDateTime.now().plusMinutes(5));

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

    /**
     * The trip scope is the whole of the booking and queue reads, and nothing
     * downstream would notice it missing: every count and every row would still
     * be a real count and a real row, just of the wrong trip.
     */
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

    // Failure paths

    /**
     * Both paths, not one. The {@code /api/v1/admin/**} prefix is the only
     * thing granting the rule — there is no method-level annotation behind it —
     * so a controller mapped one segment wrong silently publishes every
     * student's queue position and every delivery failure to any signed-in
     * student. This is the check {@code PaymentProofAdminController}'s javadoc
     * insists on, applied to this controller.
     */
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

    // Fixtures

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

    /**
     * A booking in whatever state the test needs, moved there by the entity's
     * own transitions rather than by driving hold, confirm, submit and review
     * for each one: this view reads statuses, and the fixture would otherwise
     * be several times longer than the assertions it feeds.
     */
    private void book(Trip subject, UUID userId, String reference, BookingStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = new Booking(subject, userId, reference, "Somchai P.", "0812345678",
                new BigDecimal("35.00"), now.plusMinutes(30), now);
        switch (status) {
            case PENDING_PAYMENT -> { }
            case PAYMENT_UNDER_REVIEW -> booking.markPaymentUnderReview(now.plusMinutes(30), now);
            case PAYMENT_REJECTED -> booking.markPaymentRejected(now.plusMinutes(30), now);
            case CONFIRMED -> booking.confirm(now);
            case CANCELLED -> booking.cancel(now);
        }
        bookings.save(booking);
    }

    private UUID queue(Trip subject, UUID userId, int seatsWanted, OffsetDateTime joinedAt) {
        return waitlist.saveAndFlush(new WaitlistEntry(subject, userId, seatsWanted, joinedAt)).getId();
    }

    /**
     * A promotion, written straight at the columns. {@link WaitlistEntry} has
     * no transition into {@code PROMOTED} on this branch — the sweep that adds
     * one is #69 — and this track must not reach into that work to test its
     * own read. What the view has to survive is a row in any valid state, which
     * is exactly what this produces.
     */
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

    /**
     * A dead letter produced the way the dispatcher produces one — claimed,
     * then given up on — rather than by writing {@code DEAD} at the column.
     * {@code markDead} only moves an {@code IN_FLIGHT} row, so a fixture that
     * skipped the claim would be testing a state the application cannot reach.
     */
    private UUID deadLetter(UUID aggregateId, UUID recipient, String error) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CANCELLED, aggregateId, recipient,
                "{\"reference\":\"AUV-OP-0001\"}", now)).getId();
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
        created.update(departureAt, TripStatus.ACTIVE);
        return trips.save(created);
    }

    /**
     * An administrator is a promoted student: the exchange endpoint only ever
     * mints a student, so the role has to be granted and a second token taken
     * afterwards, as {@code PaymentProofReviewIntegrationTests} already does.
     */
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
