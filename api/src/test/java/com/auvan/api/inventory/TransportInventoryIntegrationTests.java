package com.auvan.api.inventory;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
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

import java.time.OffsetDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransportInventoryIntegrationTests extends AuthenticationTestSupport {
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

    @BeforeEach
    void clearData() {
        claims.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void administratorCanCreateBookingReadyTransportInventory() throws Exception {
        String adminToken = tokenFor("admin-token", "Uadmin", true);
        String routeId = idFrom(authenticatedPost("/api/v1/admin/routes", """
                {"origin":"AU","destination":"อโศก","fare":35.00,"durationMinutes":45}
                """, adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString());

        String seatLayoutId = idFrom(authenticatedPost("/api/v1/admin/seat-layouts", """
                {"name":"Toyota 10-seat","seats":[
                  {"label":"A1","rowNumber":1,"columnNumber":1},
                  {"label":"A2","rowNumber":1,"columnNumber":2}
                ]}
                """, adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        String vehicleId = idFrom(authenticatedPost("/api/v1/admin/vehicles", """
                {"code":"VAN-01","name":"Toyota Commuter","seatLayoutId":"%s"}
                """.formatted(seatLayoutId), adminToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());

        String departureAt = OffsetDateTime.now().plusDays(1).withNano(0).toString();
        String tripId = idFrom(authenticatedPost("/api/v1/admin/trips", """
                {"routeId":"%s","vehicleId":"%s","departureAt":"%s"}
                """.formatted(routeId, vehicleId, departureAt), adminToken)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fare").value(35.00))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andReturn().getResponse().getContentAsString());

        authenticatedPut("/api/v1/admin/routes/" + routeId, "{\"fare\":40.00}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fare").value(40.00));
        authenticatedPut("/api/v1/admin/seat-layouts/" + seatLayoutId, """
                {"name":"Toyota 10-seat updated","seats":[
                  {"label":"A1","rowNumber":1,"columnNumber":1},
                  {"label":"A2","rowNumber":1,"columnNumber":2}
                ]}
                """, adminToken).andExpect(status().isOk());
        authenticatedPut("/api/v1/admin/vehicles/" + vehicleId, """
                {"code":"VAN-01","name":"Toyota Commuter","seatLayoutId":"%s","status":"ACTIVE"}
                """.formatted(seatLayoutId), adminToken).andExpect(status().isOk());
        authenticatedPut("/api/v1/admin/trips/" + tripId, "{\"status\":\"CANCELLED\"}", adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void aSeatLayoutCanBeUpdatedWithTheSeatLabelsItAlreadyHas() throws Exception {
        String adminToken = tokenFor("admin-token", "Uadmin", true);
        String seatLayoutId = idFrom(authenticatedPost("/api/v1/admin/seat-layouts", """
                {"name":"Toyota 10-seat","seats":[
                  {"label":"A1","rowNumber":1,"columnNumber":1},
                  {"label":"A2","rowNumber":1,"columnNumber":2}
                ]}
                """, adminToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        authenticatedPut("/api/v1/admin/seat-layouts/" + seatLayoutId, """
                {"name":"Toyota 10-seat","seats":[
                  {"label":"A1","rowNumber":1,"columnNumber":1},
                  {"label":"A2","rowNumber":1,"columnNumber":2},
                  {"label":"A3","rowNumber":1,"columnNumber":3}
                ]}
                """, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(3))
                .andExpect(jsonPath("$.seats[2].label").value("A3"));
    }

    @Test
    void studentCannotAccessInventoryAdministration() throws Exception {
        String studentToken = tokenFor("student-token", "Ustudent", false);

        mockMvc.perform(get("/api/v1/admin/routes").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidSeatLayoutsAndTripsWithInactiveInventory() throws Exception {
        String adminToken = tokenFor("admin-token", "Uadmin", true);

        authenticatedPost("/api/v1/admin/seat-layouts", """
                {"name":"Broken","seats":[
                  {"label":"A1","rowNumber":1,"columnNumber":1},
                  {"label":"A1","rowNumber":1,"columnNumber":2}
                ]}
                """, adminToken).andExpect(status().isBadRequest());

        String routeId = idFrom(authenticatedPost("/api/v1/admin/routes", """
                {"origin":"AU","destination":"อโศก","fare":35.00,"durationMinutes":45}
                """, adminToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String seatLayoutId = idFrom(authenticatedPost("/api/v1/admin/seat-layouts", """
                {"name":"Valid","seats":[{"label":"A1","rowNumber":1,"columnNumber":1}]}
                """, adminToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String vehicleId = idFrom(authenticatedPost("/api/v1/admin/vehicles", """
                {"code":"VAN-01","name":"Toyota Commuter","seatLayoutId":"%s"}
                """.formatted(seatLayoutId), adminToken).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        authenticatedPut("/api/v1/admin/routes/" + routeId, "{" +
                "\"status\":\"INACTIVE\"}", adminToken).andExpect(status().isOk());

        String departureAt = OffsetDateTime.now().plusDays(1).withNano(0).toString();
        authenticatedPost("/api/v1/admin/trips", """
                {"routeId":"%s","vehicleId":"%s","departureAt":"%s"}
                """.formatted(routeId, vehicleId, departureAt), adminToken)
                .andExpect(status().isBadRequest());

        authenticatedPut("/api/v1/admin/routes/" + routeId, "{\"status\":\"ACTIVE\"}", adminToken)
                .andExpect(status().isOk());
        authenticatedPost("/api/v1/admin/trips", """
                {"routeId":"%s","vehicleId":"%s","departureAt":"%s"}
                """.formatted(routeId, vehicleId, departureAt), adminToken).andExpect(status().isCreated());
        authenticatedPost("/api/v1/admin/trips", """
                {"routeId":"%s","vehicleId":"%s","departureAt":"%s"}
                """.formatted(routeId, vehicleId, departureAt), adminToken).andExpect(status().isConflict());
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

    private org.springframework.test.web.servlet.ResultActions authenticatedPost(String path, String body, String token) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions authenticatedPut(String path, String body, String token) throws Exception {
        return mockMvc.perform(put(path)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String idFrom(String response) {
        return JsonPath.read(response, "$.id");
    }
}
