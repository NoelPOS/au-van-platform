package com.auvan.api.booking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingCancellationIntegrationTests extends BookingTestSupport {
    @Test
    void cancellingReleasesTheSeatsAndRecordsTheTransition() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0), seats.get(1)), "key-1"));

        cancel(studentToken, bookingId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[0].type").value("CREATED"))
                .andExpect(jsonPath("$.events[1].type").value("CANCELLED"))
                .andExpect(jsonPath("$.events[1].detail").value("Cancelled and released seats A1, A2."))
                .andExpect(jsonPath("$.seats.length()").value(2));

        assertThat(claims.count()).isZero();
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.events.length()").value(2));
    }

    @Test
    void anotherStudentCanTakeTheSeatsOfACancelledBooking() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        cancel(studentToken, bookingId).andExpect(status().isOk());
        String otherToken = tokenFor("other-token", "Uother");

        hold(otherToken, seats.get(0)).andExpect(status().isCreated());
    }

    @Test
    void cancellingAnotherStudentsBookingIsNotFoundAndChangesNothing() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        String otherToken = tokenFor("other-token", "Uother");

        cancel(otherToken, bookingId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertThat(claims.count()).isOne();
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void cancellingTwiceIsRejectedTheSecondTime() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        cancel(studentToken, bookingId).andExpect(status().isOk());

        cancel(studentToken, bookingId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_already_cancelled"));
    }
}
