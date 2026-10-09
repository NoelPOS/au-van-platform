package com.auvan.api.booking;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingReadIntegrationTests extends BookingTestSupport {
    @Test
    void theStudentSeesTheirOwnBookingsNewestFirst() throws Exception {
        String first = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        cancel(studentToken, first).andExpect(status().isOk());
        confirm(studentToken, holdOn(seats.get(1)), "key-2").andExpect(status().isCreated());

        mockMvc.perform(authenticated(get("/api/v1/bookings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seats[0].label").value("A2"))
                .andExpect(jsonPath("$[1].seats[0].label").value("A1"));
    }

    @Test
    void aStudentWithNoBookingsGetsAnEmptyList() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/bookings")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void theStudentCanReadOneOfTheirOwnBookings() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));

        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));
    }

    @Test
    void readingAnotherStudentsBookingIsNotFound() throws Exception {
        String bookingId = bookingIdFrom(confirm(studentToken, holdOn(seats.get(0)), "key-1"));
        String otherToken = tokenFor("other-token", "Uother");

        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
        mockMvc.perform(get("/api/v1/bookings").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void readingABookingThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));
    }
}
