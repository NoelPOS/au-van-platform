package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The booking endpoints end to end. Every failure asserts the machine-readable
 * {@code code} as well as the status, because this endpoint answers 409 for
 * seven different conditions and the status alone tells a client nothing about
 * what to do next.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

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
    private IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private List<TripSeat> seats;
    private String studentToken;
    private UUID studentId;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
        studentId = users.findByLineSubject("Ustudent").orElseThrow().getId();
    }

    /**
     * In foreign-key order, and in both hooks. Bookings outlive this class, and
     * one leftover row blocks the {@code users.deleteAll()} that every other
     * integration test starts with.
     */
    @AfterEach
    void clearData() {
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    // Success paths

    /**
     * Confirming a hold secures the seats; it no longer pays for them. ADR-009
     * made the payment review the only path to {@code CONFIRMED}, so the
     * booking this returns is {@code PENDING_PAYMENT}.
     */
    @Test
    void confirmingAHoldReturnsTheBookingItsSeatsAndItsFirstHistoryEntry() throws Exception {
        String holdId = holdOn(seats.get(0), seats.get(1));

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reference").value(matchesPattern("AUV-\\d{6}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.trip.id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.trip.origin").value("AU"))
                .andExpect(jsonPath("$.trip.destination").value("Asok"))
                .andExpect(jsonPath("$.trip.departureAt").isNotEmpty())
                .andExpect(jsonPath("$.passengerName").value("Somchai P."))
                .andExpect(jsonPath("$.passengerPhone").value("0812345678"))
                .andExpect(jsonPath("$.totalFare").value(70.00))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].type").value("CREATED"))
                .andExpect(jsonPath("$.events[0].detail").value("Booked seats A1, A2."))
                .andExpect(jsonPath("$.events[0].actorUserId").value(studentId.toString()));

        assertThat(bookings.count()).isOne();
    }

    /** The price comes from the trip and the seats held, never from the client. */
    @Test
    void theTotalFareIsDerivedFromTheTripAndTheSeatsHeld() throws Exception {
        String holdId = holdOn(seats.get(0));

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalFare").value(35.00));
    }

    @Test
    void theSeatsOfANewBookingReadAsBookedAndKeepTheirClaims() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1").andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("BOOKED"))
                .andExpect(jsonPath("$.seats[1].state").value("AVAILABLE"));
        assertThat(claims.count()).isOne();
    }

    @Test
    void repeatingTheRequestWithTheSameKeyReplaysTheStoredResponseAndWritesNothing() throws Exception {
        String holdId = holdOn(seats.get(0), seats.get(1));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replayed = confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        // Byte for byte, with nothing in it for the client to branch on.
        assertThat(replayed).isEqualTo(first);
        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isOne();
        assertThat(claims.count()).isEqualTo(2);
    }

    /**
     * The stored response is replayed, not rebuilt. Rebuilding would answer a
     * retry that arrives after a cancellation with a cancelled booking under
     * {@code 201 Created}.
     */
    @Test
    void aReplayAfterACancellationStillReturnsTheResponseThatWasSent() throws Exception {
        String holdId = holdOn(seats.get(0));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        cancel(studentToken, JsonPath.read(first, "$.id")).andExpect(status().isOk());

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void theStudentSeesTheirOwnBookingsNewestFirst() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1").andExpect(status().isCreated());
        confirm(studentToken, holdOn(seats.get(1)), "key-2").andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/bookings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seats[0].label").value("A2"))
                .andExpect(jsonPath("$[1].seats[0].label").value("A1"));
    }

    @Test
    void aStudentWithNoBookingsGetsAnEmptyList() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/bookings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theStudentCanReadOneOfTheirOwnBookings() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));

        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void cancellingReleasesTheSeatsAndRecordsTheTransition() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0), seats.get(1)), "key-1"));

        cancel(studentToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].type").value("CREATED"))
                .andExpect(jsonPath("$.events[1].type").value("CANCELLED"))
                .andExpect(jsonPath("$.events[1].detail").value("Cancelled and released seats A1, A2."))
                // booking_seats is not seat_claims, so the booking still says
                // what was booked even though the claims are gone.
                .andExpect(jsonPath("$.seats.length()").value(2));

        assertThat(claims.count()).isZero();
        // The cancellation and its event really reached the database, rather
        // than only the response body.
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void anotherStudentCanTakeTheSeatsOfACancelledBooking() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        cancel(studentToken, bookingId).andExpect(status().isOk());
        String otherToken = tokenFor("other-token", "Uother");

        hold(otherToken, seats.get(0)).andExpect(status().isCreated());
    }

    // Failure paths

    @Test
    void aConfirmationWithoutAnIdempotencyKeyIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));

        assertThat(bookings.count()).isZero();
    }

    @Test
    void aBlankIdempotencyKeyIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1").andExpect(status().isCreated());

        confirm(studentToken, holdOn(seats.get(1)), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));

        assertThat(bookings.count()).isOne();
    }

    /** Two requests that differ only in field order are the same request. */
    @Test
    void aRetryWhosePayloadOnlyDiffersInShapeIsStillAReplay() throws Exception {
        String holdId = holdOn(seats.get(0));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reordered = "{ \"passengerPhone\": \"0812345678\",\n  \"passengerName\": \"Somchai P.\",\n"
                + "  \"holdId\": \"" + holdId + "\" }";
        String replayed = mockMvc.perform(authenticated(post("/api/v1/bookings"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reordered))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(replayed).isEqualTo(first);
        assertThat(bookings.count()).isOne();
    }

    @Test
    void confirmingAnExpiredHoldIsRejected() throws Exception {
        UUID holdId = UUID.randomUUID();
        claims.saveAndFlush(new SeatClaim(seats.get(0), studentId, holdId, OffsetDateTime.now().minusMinutes(1)));

        confirm(studentToken, holdId.toString(), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hold_expired"))
                .andExpect(jsonPath("$.detail").value(containsString("expired")));

        assertThat(bookings.count()).isZero();
    }

    @Test
    void confirmingTheSameHoldASecondTimeUnderANewKeyIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        confirm(studentToken, holdId, "key-1").andExpect(status().isCreated());

        confirm(studentToken, holdId, "key-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hold_already_used"));

        assertThat(bookings.count()).isOne();
    }

    /**
     * Someone else's hold answers exactly as a hold that never existed, so a
     * stranger cannot tell a live hold id from an imaginary one.
     */
    @Test
    void confirmingSomeoneElsesHoldIsNotFoundAndLeavesItAlone() throws Exception {
        String holdId = holdOn(seats.get(0));
        String otherToken = tokenFor("other-token", "Uother");

        confirm(otherToken, holdId, "key-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"));

        assertThat(bookings.count()).isZero();
        assertThat(claims.count()).isOne();
    }

    @Test
    void confirmingAHoldThatNeverExistedAnswersIdentically() throws Exception {
        confirm(studentToken, UUID.randomUUID().toString(), "key-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"))
                .andExpect(jsonPath("$.detail").value("Hold not found."));
    }

    @Test
    void confirmingAHoldOnACancelledTripIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        retimeTrip(trip.getDepartureAt(), TripStatus.CANCELLED);

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_not_available"));
    }

    @Test
    void confirmingAHoldOnADepartedTripIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        retimeTrip(OffsetDateTime.now().minusMinutes(1), TripStatus.ACTIVE);

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_departed"));
    }

    @Test
    void aMissingPassengerNameIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1", " ", "0812345678")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anImplausiblePassengerPhoneIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1", "Somchai P.", "0812")
                .andExpect(status().isBadRequest());
    }

    @Test
    void readingAnotherStudentsBookingIsNotFound() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        String otherToken = tokenFor("other-token", "Uother");

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void cancellingAnotherStudentsBookingIsNotFoundAndChangesNothing() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        String otherToken = tokenFor("other-token", "Uother");

        cancel(otherToken, bookingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertThat(claims.count()).isOne();
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void cancellingTwiceIsRejectedTheSecondTime() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        cancel(studentToken, bookingId).andExpect(status().isOk());

        cancel(studentToken, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_already_cancelled"));
    }

    @Test
    void readingABookingThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
    }

    @Test
    void anonymousStudentsCannotTouchAnyBookingEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bookings/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings/" + UUID.randomUUID() + "/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + UUID.randomUUID() + "\",\"passengerName\":\"Somchai P.\","
                                + "\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * The seat-hold errors gained the same {@code code} member in this change,
     * so the web client has one error idiom across the whole booking flow.
     */
    @Test
    void theSeatHoldErrorsCarryTheSameCodeMember() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        String otherToken = tokenFor("other-token", "Uother");

        hold(otherToken, seats.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("seat_taken"))
                .andExpect(jsonPath("$.detail").value(containsString("refresh")));
        mockMvc.perform(authenticated(post("/api/v1/seat-holds/" + UUID.randomUUID() + "/release")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"));
        mockMvc.perform(authenticated(get("/api/v1/trips/" + UUID.randomUUID() + "/seats")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    // Fixtures

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        created.update(departureAt, status);
        return trips.save(created);
    }

    /** Cancels or reschedules the trip after a hold on it already exists. */
    private void retimeTrip(OffsetDateTime departureAt, TripStatus status) {
        Trip stored = trips.findById(trip.getId()).orElseThrow();
        stored.update(departureAt, status);
        trips.saveAndFlush(stored);
    }

    private String holdOn(TripSeat... requested) throws Exception {
        return JsonPath.read(hold(studentToken, requested).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.holdId");
    }

    private ResultActions hold(String token, TripSeat... requested) throws Exception {
        String seatIds = Arrays.stream(requested)
                .map(seat -> "\"" + seat.getId() + "\"")
                .collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[" + seatIds + "]}"));
    }

    private ResultActions confirm(String token, String holdId, String key) throws Exception {
        return confirm(token, holdId, key, "Somchai P.", "0812345678");
    }

    private ResultActions confirm(String token, String holdId, String key, String name, String phone)
            throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/bookings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"" + name + "\","
                        + "\"passengerPhone\":\"" + phone + "\"}");
        if (key != null) {
            request = request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions cancel(String token, String bookingId) throws Exception {
        return mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                .header("Authorization", "Bearer " + token));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
    }

    private String bookingIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
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
