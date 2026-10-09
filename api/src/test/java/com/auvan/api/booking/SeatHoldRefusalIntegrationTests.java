package com.auvan.api.booking;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatHoldRefusalIntegrationTests extends SeatHoldTestSupport {
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
    void anonymousStudentsCannotSeeOrHoldSeats() throws Exception {
        mockMvc.perform(get("/api/v1/trips")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/trips/" + trip.getId() + "/seats")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/seat-holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[]}"))
                .andExpect(status().isUnauthorized());
    }
}
