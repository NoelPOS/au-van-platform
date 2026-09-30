package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.WaitlistService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WaitlistIntegrationTests extends AuthenticationTestSupport {
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

    @MockitoSpyBean
    private WaitlistEntryRepository waitlist;

    @Autowired
    private WaitlistService waitlistService;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    private Trip fullTrip;
    private String studentToken;
    private String otherToken;

    @BeforeEach
    void setUp() throws Exception {
        clearEverything();
        fullTrip = createTrip("VAN-W1", OffsetDateTime.now().plusDays(1), TripStatus.ACTIVE);
        fillEverySeatOf(fullTrip);
        studentToken = tokenFor("student-token", "Ustudent");
        otherToken = tokenFor("other-token", "Uother");
    }

    @AfterEach
    void clearWaitlist() {
        waitlist.deleteAll();
        claims.deleteAll();
    }

    @Test
    void joiningAFullTripReturnsTheFirstPlaceAndTheNextStudentTheSecond() throws Exception {
        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tripId").value(fullTrip.getId().toString()))
                .andExpect(jsonPath("$.seatsWanted").value(1))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));

        join(otherToken, fullTrip, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsWanted").value(2))
                .andExpect(jsonPath("$.position").value(2));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void joiningTwiceReturnsTheSameEntryRatherThanASecondOne() throws Exception {
        String first = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        join(otherToken, fullTrip, 1).andExpect(status().isCreated());

        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.position").value(1));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void leavingWithdrawsTheEntryAndTheStudentBehindMovesUp() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        join(otherToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));

        mockMvc.perform(post("/api/v1/waitlist/" + entryId + "/leave")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        assertThat(waitlist.findById(UUID.fromString(entryId)).orElseThrow().getStatus())
                .isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(waitlist.count()).isEqualTo(2);
        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    void aStudentsOwnWaitlistReadReturnsTheirEntryAndItsPosition() throws Exception {
        join(otherToken, fullTrip, 1).andExpect(status().isCreated());
        String entryId = entryIdFrom(join(studentToken, fullTrip, 3).andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(entryId))
                .andExpect(jsonPath("$[0].tripId").value(fullTrip.getId().toString()))
                .andExpect(jsonPath("$[0].seatsWanted").value(3))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].position").value(2));
    }

    @Test
    void aWithdrawnEntryDropsOutOfTheStudentsOwnRead() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));

        leave(studentToken, entryId).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejoiningAfterLeavingReusesTheRowAndGoesToTheBack() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        leave(studentToken, entryId).andExpect(status().isNoContent());
        join(otherToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));

        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(2));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void joiningATripThatStillHasFreeSeatsIsRefused() throws Exception {
        Trip roomy = createTrip("VAN-W2", OffsetDateTime.now().plusDays(2), TripStatus.ACTIVE);

        join(studentToken, roomy, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("waitlist_not_needed"))
                .andExpect(jsonPath("$.detail").value(containsString("still has seats")));
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void joiningATripWhoseOnlyFreeSeatIsALapsedHoldIsRefused() throws Exception {
        Trip lapsing = createTrip("VAN-W3", OffsetDateTime.now().plusDays(3), TripStatus.ACTIVE);
        UUID holder = users.save(new AppUser("Uforgetful", "Forgetful Student")).getId();
        List<TripSeat> seats = lapsing.getSeats();
        claims.save(new SeatClaim(seats.get(0), holder, UUID.randomUUID(), OffsetDateTime.now().plusHours(2)));
        claims.save(new SeatClaim(seats.get(1), holder, UUID.randomUUID(), OffsetDateTime.now().minusMinutes(1)));

        join(studentToken, lapsing, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("waitlist_not_needed"));
    }

    @Test
    void joiningACancelledTripIsRefused() throws Exception {
        Trip cancelled = createTrip("VAN-W4", OffsetDateTime.now().plusDays(4), TripStatus.CANCELLED);
        fillEverySeatOf(cancelled);

        join(studentToken, cancelled, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_not_available"))
                .andExpect(jsonPath("$.detail").value(containsString("no longer available")));
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void joiningADepartedTripIsRefused() throws Exception {
        Trip departed = createTrip("VAN-W5", OffsetDateTime.now().minusHours(1), TripStatus.ACTIVE);
        fillEverySeatOf(departed);

        join(studentToken, departed, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_departed"))
                .andExpect(jsonPath("$.detail").value(containsString("already departed")));
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void joiningForMoreSeatsThanTheConfiguredLimitIsRefused() throws Exception {
        join(studentToken, fullTrip, 5)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("too_many_seats"))
                .andExpect(jsonPath("$.detail").value(containsString("at most 4 seats")));
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void joiningForNoSeatsIsRejected() throws Exception {
        join(studentToken, fullTrip, 0).andExpect(status().isBadRequest());
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void joiningAnUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/waitlist")
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + UUID.randomUUID() + "\",\"seatsWanted\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    @Test
    void leavingSomeoneElsesEntryAnswersAsOneThatDoesNotExistAndLeavesItStanding() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));

        leave(otherToken, entryId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("waitlist_entry_not_found"))
                .andExpect(jsonPath("$.detail").value("Waitlist entry not found."));

        assertThat(waitlist.findById(UUID.fromString(entryId)).orElseThrow().getStatus())
                .isEqualTo(WaitlistStatus.WAITING);
        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + studentToken))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    void leavingAnEntryThatNeverExistedAnswersIdentically() throws Exception {
        leave(studentToken, UUID.randomUUID().toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("waitlist_entry_not_found"))
                .andExpect(jsonPath("$.detail").value("Waitlist entry not found."));
    }

    @Test
    void theUniqueConstraintRefusesASecondRowForTheSameStudentAndTrip() {
        UUID student = users.findByLineSubject("Ustudent").orElseThrow().getId();
        OffsetDateTime now = OffsetDateTime.now();
        waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 1, now));

        assertThatThrownBy(() -> waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 1, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aJoinThatLosesTheRaceToInsertIsAConflictRatherThanACrash() {
        UUID student = users.findByLineSubject("Ustudent").orElseThrow().getId();
        WaitlistEntry rival = waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 2, OffsetDateTime.now()));
        doReturn(Optional.empty()).when(waitlist).findByTripIdAndUserId(fullTrip.getId(), student);

        assertThatThrownBy(() -> waitlistService.join(student, new JoinWaitlistRequest(fullTrip.getId(), 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getReason()).contains("refresh");
                });

        assertThat(waitlist.findAll()).singleElement()
                .satisfies(entry -> assertThat(entry.getId()).isEqualTo(rival.getId()));
    }

    @Test
    void anonymousStudentsCannotJoinReadOrLeaveTheWaitlist() throws Exception {
        mockMvc.perform(get("/api/v1/waitlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + fullTrip.getId() + "\",\"seatsWanted\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/waitlist/" + UUID.randomUUID() + "/leave"))
                .andExpect(status().isUnauthorized());
    }

    private void clearEverything() {
        waitlist.deleteAll();
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    private Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        created.update(departureAt, status);
        return trips.save(created);
    }

    private void fillEverySeatOf(Trip trip) {
        UUID holder = users.save(new AppUser("Uholder-" + trip.getId(), "Holding Student")).getId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);
        trip.getSeats().forEach(seat -> claims.save(new SeatClaim(seat, holder, UUID.randomUUID(), expiresAt)));
    }

    private ResultActions join(String token, Trip trip, int seatsWanted) throws Exception {
        return mockMvc.perform(post("/api/v1/waitlist")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatsWanted\":" + seatsWanted + "}"));
    }

    private ResultActions leave(String token, String entryId) throws Exception {
        return mockMvc.perform(post("/api/v1/waitlist/" + entryId + "/leave")
                .header("Authorization", "Bearer " + token));
    }

    private String entryIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id");
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
