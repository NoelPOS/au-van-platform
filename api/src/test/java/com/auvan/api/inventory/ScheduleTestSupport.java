package com.auvan.api.inventory;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.DayTemplateRepository;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

abstract class ScheduleTestSupport extends AuthenticationTestSupport {
    static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    AppUserRepository users;

    @Autowired
    VanRouteRepository routes;

    @Autowired
    SeatLayoutRepository seatLayouts;

    @Autowired
    VehicleRepository vehicles;

    @Autowired
    TripRepository trips;

    @Autowired
    DayTemplateRepository templates;

    @Autowired
    BookingRepository bookings;

    @Autowired
    SeatClaimRepository claims;

    @Autowired
    WaitlistEntryRepository waitlist;

    @Autowired
    IdempotencyKeyRepository idempotencyKeys;

    @MockitoBean
    LineTokenVerifier lineTokenVerifier;

    VanRoute route;
    Vehicle van;
    String adminToken;

    @BeforeEach
    void createInventory() throws Exception {
        clearEverything();
        route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Commuter", List.of(new SeatLayoutSeat("A1", 1, 1))));
        van = vehicles.save(new Vehicle("VAN-01", "Hiace", layout));
        adminToken = tokenFor("admin-token", "Uadmin", true);
    }

    @AfterEach
    void clearEverything() {
        idempotencyKeys.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        templates.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    static LocalDate today() {
        return LocalDate.now(BANGKOK);
    }

    static OffsetDateTime at(LocalDate date, String clock) {
        return date.atTime(LocalTime.parse(clock)).atZone(BANGKOK).toOffsetDateTime();
    }

    Trip tripAt(LocalDate date, String clock) {
        return trips.save(new Trip(route, van, at(date, clock)));
    }

    ResultActions postJson(String path, String body) throws Exception {
        return postJson(path, body, adminToken);
    }

    ResultActions postJson(String path, String body, String token) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    String tokenFor(String idToken, String lineSubject, boolean administrator) throws Exception {
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
