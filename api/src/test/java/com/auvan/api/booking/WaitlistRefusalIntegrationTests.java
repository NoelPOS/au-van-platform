package com.auvan.api.booking;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WaitlistRefusalIntegrationTests extends WaitlistTestSupport {
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
    void anonymousStudentsCannotJoinReadOrLeaveTheWaitlist() throws Exception {
        mockMvc.perform(get("/api/v1/waitlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + fullTrip.getId() + "\",\"seatsWanted\":1}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/waitlist/" + UUID.randomUUID() + "/leave"))
                .andExpect(status().isUnauthorized());
    }
}
