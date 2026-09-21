package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SeatHoldIntegrationTests extends AuthenticationTestSupport {
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

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private List<TripSeat> seats;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();

        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
    }

    // Success paths

    @Test
    void catalogueListsBookableTripsWithTheirRemainingSeats() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].origin").value("AU"))
                .andExpect(jsonPath("$[0].totalSeats").value(4))
                .andExpect(jsonPath("$[0].availableSeats").value(4));
    }

    @Test
    void catalogueReportsFewerSeatsOnceSomeAreHeld() throws Exception {
        hold(studentToken, seats.get(0), seats.get(1)).andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availableSeats").value(2));
    }

    @Test
    void seatMapReportsEverySeatAsAvailableBeforeAnyHold() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(4))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));
    }

    @Test
    void holdingSeatsReturnsTheHoldAndItsExpiry() throws Exception {
        hold(studentToken, seats.get(0), seats.get(1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.holdId").isNotEmpty())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].label").value("A1"));
    }

    @Test
    void theHolderSeesTheirOwnSeatsAsHeldByYou() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("HELD_BY_YOU"))
                .andExpect(jsonPath("$.seats[1].state").value("AVAILABLE"));
    }

    @Test
    void anotherStudentSeesTheSameSeatsAsHeld() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        String otherToken = tokenFor("other-token", "Uother");

        mockMvc.perform(get("/api/v1/trips/" + trip.getId() + "/seats")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.seats[0].state").value("HELD"));
    }

    @Test
    void reSelectingSeatsReplacesTheCallersExistingHoldRatherThanAccumulating() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        hold(studentToken, seats.get(1)).andExpect(status().isCreated());

        assertThat(claims.count()).isOne();
        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$.seats[1].state").value("HELD_BY_YOU"));
    }

    /**
     * Regression guard for Hibernate's flush ordering: without an explicit flush
     * between the release and the re-insert, this collides with the caller's own row.
     */
    @Test
    void reSelectingASeatTheCallerAlreadyHoldsSucceeds() throws Exception {
        hold(studentToken, seats.get(0), seats.get(1)).andExpect(status().isCreated());

        hold(studentToken, seats.get(1), seats.get(2))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(2));
        assertThat(claims.count()).isEqualTo(2);
    }

    @Test
    void anExpiredHoldIsReportedAvailableWithoutAnySchedulerRunning() throws Exception {
        expireAHoldOn(seats.get(0));

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));
        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(jsonPath("$[0].availableSeats").value(4));
        // The read derived the state and changed nothing; the row is still there.
        assertThat(claims.count()).isOne();
    }

    @Test
    void anExpiredHoldIsReclaimedByTheNextStudentToClaimTheSeat() throws Exception {
        expireAHoldOn(seats.get(0));

        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        assertThat(claims.count()).isOne();
    }

    @Test
    void theHolderCanReleaseTheirHoldAndTheSeatBecomesAvailable() throws Exception {
        String holdId = holdIdFrom(hold(studentToken, seats.get(0)).andExpect(status().isCreated()));

        mockMvc.perform(authenticated(post("/api/v1/seat-holds/" + holdId + "/release")))
                .andExpect(status().isNoContent());
        assertThat(claims.count()).isZero();
        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));
    }

    // Failure paths

    @Test
    void aSecondStudentHoldingTheSameSeatIsToldToRefresh() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        String otherToken = tokenFor("other-token", "Uother");

        hold(otherToken, seats.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("refresh")));
        assertThat(claims.count()).isOne();
    }

    @Test
    void seatsOnACancelledTripCannotBeHeld() throws Exception {
        Trip cancelled = createTrip("VAN-02", OffsetDateTime.now().plusDays(2), TripStatus.CANCELLED);

        holdOn(cancelled, studentToken, cancelled.getSeats().getFirst())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("no longer available")));
    }

    @Test
    void seatsOnADepartedTripCannotBeHeld() throws Exception {
        Trip departed = createTrip("VAN-03", OffsetDateTime.now().minusHours(1), TripStatus.ACTIVE);

        holdOn(departed, studentToken, departed.getSeats().getFirst())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already departed")));
    }

    @Test
    void seatsBelongingToAnotherTripCannotBeHeld() throws Exception {
        Trip other = createTrip("VAN-04", OffsetDateTime.now().plusDays(3), TripStatus.ACTIVE);

        holdOn(trip, studentToken, other.getSeats().getFirst())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("do not belong to this trip")));
    }

    @Test
    void moreSeatsThanTheConfiguredLimitCannotBeHeld() throws Exception {
        Trip roomy = createTrip("VAN-05", OffsetDateTime.now().plusDays(4), TripStatus.ACTIVE, 5);

        holdOn(roomy, studentToken, roomy.getSeats().toArray(TripSeat[]::new))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("at most 4 seats")));
    }

    @Test
    void theSameSeatCannotBeSelectedTwiceInOneHold() throws Exception {
        hold(studentToken, seats.get(0), seats.get(0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("only be selected once")));
    }

    @Test
    void anEmptySeatSelectionIsRejected() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/seat-holds"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void holdingSeatsOnAnUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/seat-holds"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + UUID.randomUUID() + "\",\"seatIds\":[\"" + seats.getFirst().getId() + "\"]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void theSeatMapOfAnUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/trips/" + UUID.randomUUID() + "/seats")))
                .andExpect(status().isNotFound());
    }

    /**
     * Someone else's hold answers 404, not 403, so that a stranger cannot tell a
     * live hold id from an imaginary one by the status they get back.
     */
    @Test
    void someoneElsesHoldReadsAsMissingAndSurvivesTheAttempt() throws Exception {
        String holdId = holdIdFrom(hold(studentToken, seats.get(0)).andExpect(status().isCreated()));
        String otherToken = tokenFor("other-token", "Uother");

        mockMvc.perform(post("/api/v1/seat-holds/" + holdId + "/release")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Hold not found."));

        assertThat(claims.count()).isOne();
        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("HELD_BY_YOU"));
    }

    @Test
    void releasingAHoldThatNeverExistedAnswersIdentically() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/seat-holds/" + UUID.randomUUID() + "/release")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Hold not found."));
    }

    @Test
    void theCatalogueOmitsCancelledAndDepartedTrips() throws Exception {
        createTrip("VAN-06", OffsetDateTime.now().plusDays(5), TripStatus.CANCELLED);
        createTrip("VAN-07", OffsetDateTime.now().minusHours(2), TripStatus.ACTIVE);

        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(trip.getId().toString()));
    }

    @Test
    void anonymousStudentsCannotSeeOrHoldSeats() throws Exception {
        mockMvc.perform(get("/api/v1/trips")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/trips/" + trip.getId() + "/seats")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/seat-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }

    // Fixtures

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        return createTrip(vehicleCode, departureAt, status, 4);
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status, int seatCount) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= seatCount; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        created.update(departureAt, status);
        return trips.save(created);
    }

    /** Writes a claim that expired a minute ago, which no API call can produce. */
    private void expireAHoldOn(TripSeat seat) {
        UUID owner = users.save(new AppUser("Uexpired", "Forgetful Student")).getId();
        claims.save(new SeatClaim(seat, owner, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1)));
    }

    private ResultActions hold(String token, TripSeat... requested) throws Exception {
        return holdOn(trip, token, requested);
    }

    private ResultActions holdOn(Trip target, String token, TripSeat... requested) throws Exception {
        String seatIds = Arrays.stream(requested)
                .map(seat -> "\"" + seat.getId() + "\"")
                .collect(Collectors.joining(","));
        return mockMvc.perform(post("/api/v1/seat-holds")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + target.getId() + "\",\"seatIds\":[" + seatIds + "]}"));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
    }

    private String holdIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.holdId");
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
