package com.auvan.api.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatHoldIntegrationTests extends SeatHoldTestSupport {
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
    void anExpiredHoldIsReclaimedByTheNextStudentToClaimTheSeat() throws Exception {
        expireAHoldOn(seats.get(0));

        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        assertThat(claims.count()).isOne();
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

    private String holdIdFrom(ResultActions actions) throws Exception {
        return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.holdId");
    }
}
