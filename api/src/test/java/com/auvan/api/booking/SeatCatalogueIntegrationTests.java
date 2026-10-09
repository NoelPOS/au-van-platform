package com.auvan.api.booking;

import com.auvan.api.inventory.entity.TripStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatCatalogueIntegrationTests extends SeatHoldTestSupport {
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
    void anExpiredHoldIsReportedAvailableWithoutAnySchedulerRunning() throws Exception {
        expireAHoldOn(seats.get(0));

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("AVAILABLE"));
        mockMvc.perform(authenticated(get("/api/v1/trips")))
                .andExpect(jsonPath("$[0].availableSeats").value(4));
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
    void theSeatMapOfAnUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/trips/" + UUID.randomUUID() + "/seats")))
                .andExpect(status().isNotFound());
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
}
