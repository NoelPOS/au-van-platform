package com.auvan.api.booking;

import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingCreationIntegrationTests extends BookingTestSupport {
    @Test
    void confirmingAHoldReturnsTheBookingItsSeatsAndItsFirstHistoryEntry() throws Exception {
        String holdId = holdOn(seats.get(0), seats.get(1));

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.reference").value(matchesPattern("AUV-\\d{6}-[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}")))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.trip.id").value(trip.getId().toString()))
                .andExpect(jsonPath("$.trip.origin").value("AU"))
                .andExpect(jsonPath("$.trip.destination").value("Asok"))
                .andExpect(jsonPath("$.trip.departureAt").isNotEmpty())
                .andExpect(jsonPath("$.passengerName").value("Somchai P."))
                .andExpect(jsonPath("$.passengerPhone").value("0812345678"))
                .andExpect(jsonPath("$.totalFare").value(70.00))
                .andExpect(jsonPath("$.seats.length()").value(2))
                .andExpect(jsonPath("$.seats[0].label").value("A1"))
                .andExpect(jsonPath("$.events.length()").value(1))
                .andExpect(jsonPath("$.events[0].type").value("CREATED"))
                .andExpect(jsonPath("$.events[0].detail").value("Booked seats A1, A2."))
                .andExpect(jsonPath("$.events[0].actorUserId").value(studentId.toString()));

        assertThat(bookings.count()).isOne();
    }

    @Test
    void theTotalFareIsDerivedFromTheTripAndTheSeatsHeld() throws Exception {
        String holdId = holdOn(seats.get(0));

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalFare").value(35.00));
    }

    @Test
    void theSeatsOfANewBookingReadAsBookedAndKeepTheirClaims() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1").andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/trips/" + trip.getId() + "/seats")))
                .andExpect(jsonPath("$.seats[0].state").value("BOOKED"))
                .andExpect(jsonPath("$.seats[1].state").value("AVAILABLE"));
        assertThat(claims.count()).isOne();
    }

    @Test
    void confirmingAnExpiredHoldIsRejected() throws Exception {
        UUID holdId = UUID.randomUUID();
        claims.saveAndFlush(new SeatClaim(seats.get(0), studentId, holdId, OffsetDateTime.now().minusMinutes(1)));

        confirm(studentToken, holdId.toString(), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hold_expired"))
                .andExpect(jsonPath("$.detail").value(containsString("expired")));

        assertThat(bookings.count()).isZero();
    }

    @Test
    void confirmingTheSameHoldASecondTimeUnderANewKeyIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        confirm(studentToken, holdId, "key-1").andExpect(status().isCreated());

        confirm(studentToken, holdId, "key-2")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("hold_already_used"));

        assertThat(bookings.count()).isOne();
    }

    @Test
    void confirmingSomeoneElsesHoldIsNotFoundAndLeavesItAlone() throws Exception {
        String holdId = holdOn(seats.get(0));
        String otherToken = tokenFor("other-token", "Uother");

        confirm(otherToken, holdId, "key-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"));

        assertThat(bookings.count()).isZero();
        assertThat(claims.count()).isOne();
    }

    @Test
    void confirmingAHoldThatNeverExistedAnswersIdentically() throws Exception {
        confirm(studentToken, UUID.randomUUID().toString(), "key-1")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"))
                .andExpect(jsonPath("$.detail").value("Hold not found."));
    }

    @Test
    void confirmingAHoldOnACancelledTripIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        retimeTrip(trip.getDepartureAt(), TripStatus.CANCELLED);

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_not_available"));
    }

    @Test
    void confirmingAHoldOnADepartedTripIsRejected() throws Exception {
        String holdId = holdOn(seats.get(0));
        retimeTrip(OffsetDateTime.now().minusMinutes(1), TripStatus.ACTIVE);

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_departed"));
    }

    @Test
    void aMissingPassengerNameIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1", " ", "0812345678")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anImplausiblePassengerPhoneIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1", "Somchai P.", "0812")
                .andExpect(status().isBadRequest());
    }

    @Test
    void anonymousStudentsCannotTouchAnyBookingEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/bookings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/bookings/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings/" + UUID.randomUUID() + "/cancel")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/bookings")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + UUID.randomUUID() + "\",\"passengerName\":\"Somchai P.\","
                                + "\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theSeatHoldErrorsCarryTheSameCodeMember() throws Exception {
        hold(studentToken, seats.get(0)).andExpect(status().isCreated());
        String otherToken = tokenFor("other-token", "Uother");

        hold(otherToken, seats.get(0))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("seat_taken"))
                .andExpect(jsonPath("$.detail").value(containsString("refresh")));
        mockMvc.perform(authenticated(post("/api/v1/seat-holds/" + UUID.randomUUID() + "/release")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("hold_not_found"));
        mockMvc.perform(authenticated(get("/api/v1/trips/" + UUID.randomUUID() + "/seats")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    private void retimeTrip(OffsetDateTime departureAt, TripStatus status) {
        Trip stored = trips.findById(trip.getId()).orElseThrow();
        stored.reschedule(departureAt);
        if (status == TripStatus.CANCELLED) {
            stored.cancel("The van has broken down.");
        }
        trips.saveAndFlush(stored);
    }
}
