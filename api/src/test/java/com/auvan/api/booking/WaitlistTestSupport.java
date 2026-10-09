package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.WaitlistService;
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
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class WaitlistTestSupport extends AuthenticationTestSupport {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private TripRepository trips;

    @Autowired
    SeatClaimRepository claims;

    @MockitoSpyBean
    WaitlistEntryRepository waitlist;

    @Autowired
    WaitlistService waitlistService;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    Trip fullTrip;
    String studentToken;
    String otherToken;

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

    private void clearEverything() {
        waitlist.deleteAll();
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    Trip createTrip(String vehicleCode, OffsetDateTime departureAt, TripStatus status) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 2; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        Trip created = new Trip(route, vehicle, departureAt);
        if (status == TripStatus.CANCELLED) {
            created.cancel("The van has broken down.");
        }
        return trips.save(created);
    }

    void fillEverySeatOf(Trip trip) {
        UUID holder = users.save(new AppUser("Uholder-" + trip.getId(), "Holding Student")).getId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);
        trip.getSeats().forEach(seat -> claims.save(new SeatClaim(seat, holder, UUID.randomUUID(), expiresAt)));
    }

    ResultActions join(String token, Trip trip, int seatsWanted) throws Exception {
        return mockMvc.perform(post("/api/v1/waitlist")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tripId\":\"" + trip.getId() + "\",\"seatsWanted\":" + seatsWanted + "}"));
    }

    ResultActions leave(String token, String entryId) throws Exception {
        return mockMvc.perform(post("/api/v1/waitlist/" + entryId + "/leave")
                .header("Authorization", "Bearer " + token));
    }

    String entryIdFrom(ResultActions actions) throws Exception {
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
