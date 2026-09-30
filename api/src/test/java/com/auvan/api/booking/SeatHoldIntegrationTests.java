package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private TransactionTemplate transactions;

    @PersistenceContext
    private EntityManager entityManager;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip trip;
    private List<TripSeat> seats;
    private String studentToken;

    @BeforeEach
    void setUp() throws Exception {
        claims.deleteAll();
        deleteBookings();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();

        trip = createTrip("VAN-01", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        seats = trip.getSeats();
        studentToken = tokenFor("student-token", "Ustudent");
    }

    @AfterEach
    void clearBookings() {
        claims.deleteAll();
        deleteBookings();
    }

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
        assertThat(claims.count()).isOne();
    }

    @Test
    void anExpiredHoldIsReclaimedByTheNextStudentToClaimTheSeat() throws Exception {
        expireAHoldOn(seats.get(0));

        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        assertThat(claims.count()).isOne();
    }

    @Test
    void aBookedSeatIsReportedAsBookedEvenToTheStudentWhoHeldIt() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        bookTheClaimOn(seats.get(0), OffsetDateTime.now().plusMinutes(5));

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("BOOKED"))
                .andExpect(jsonPath("$.seats[1].state").value("AVAILABLE"));
        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(jsonPath("$[0].availableSeats").value(3));
    }

    @Test
    void aBookedClaimStillBlocksItsSeatAfterTheHoldWindowHasPassed() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        bookTheClaimOn(seats.get(0), OffsetDateTime.now().minusHours(1));

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("BOOKED"));

        String otherToken = tokenFor("other-token", "Uother");
        hold(otherToken, seats.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("refresh")));
        hold(studentToken, seats.get(0)).andExpect(status().isConflict());
        assertThat(claims.count()).isOne();
    }

    @Test
    void aHoldThatHasBecomeABookingCanNoLongerBeReleased() throws Exception {
        String holdId = holdIdFrom(hold(studentToken, seats.get(0)).andExpect(status().isCreated()));
        bookTheClaimOn(seats.get(0), OffsetDateTime.now().plusMinutes(5));

        mockMvc.perform(authenticated(post("/api/v1/seat-holds/" + holdId + "/release")))
                .andExpect(status().isNotFound());

        assertThat(claims.count()).isOne();
        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("BOOKED"));
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

    private void bookTheClaimOn(TripSeat seat, OffsetDateTime expiresAt) {
        UUID bookingId = insertBooking();
        transactions.executeWithoutResult(status -> entityManager.createQuery("""
                        update SeatClaim claim
                        set claim.bookingId = :bookingId, claim.expiresAt = :expiresAt
                        where claim.tripSeat.id = :seatId
                        """)
                .setParameter("bookingId", bookingId)
                .setParameter("expiresAt", expiresAt)
                .setParameter("seatId", seat.getId())
                .executeUpdate());
    }

    private UUID insertBooking() {
        UUID userId = users.findByLineSubject("Ustudent").orElseThrow().getId();
        return bookings.saveAndFlush(new Booking(trip, userId, "AUV-000000-" + reference(), "Test Student",
                "0800000000", new BigDecimal("35.00"), OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now())).getId();
    }

    private static String reference() {
        return UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void deleteBookings() {
        bookings.deleteAll();
    }

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
