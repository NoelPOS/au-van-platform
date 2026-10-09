package com.auvan.api.booking;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BookingIdempotencyIntegrationTests extends BookingTestSupport {
    @Test
    void repeatingTheRequestWithTheSameKeyReplaysTheStoredResponseAndWritesNothing() throws Exception {
        String holdId = holdOn(seats.get(0), seats.get(1));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replayed = confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();

        assertThat(replayed).isEqualTo(first);
        assertThat(bookings.count()).isOne();
        assertThat(idempotencyKeys.count()).isOne();
        assertThat(claims.count()).isEqualTo(2);
    }

    @Test
    void aReplayAfterACancellationStillReturnsTheResponseThatWasSent() throws Exception {
        String holdId = holdOn(seats.get(0));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        cancel(studentToken, JsonPath.read(first, "$.id")).andExpect(status().isOk());

        confirm(studentToken, holdId, "key-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    @Test
    void aConfirmationWithoutAnIdempotencyKeyIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));

        assertThat(bookings.count()).isZero();
    }

    @Test
    void aBlankIdempotencyKeyIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() throws Exception {
        confirm(studentToken, holdOn(seats.get(0)), "key-1").andExpect(status().isCreated());

        confirm(studentToken, UUID.randomUUID().toString(), "key-1")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("idempotency_key_reused"));

        assertThat(bookings.count()).isOne();
    }

    @Test
    void aRetryWhosePayloadOnlyDiffersInShapeIsStillAReplay() throws Exception {
        String holdId = holdOn(seats.get(0));
        String first = confirm(studentToken, holdId, "key-1").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String reordered = "{ \"passengerPhone\": \"0812345678\",\n  \"passengerName\": \"Somchai P.\",\n"
                + "  \"holdId\": \"" + holdId + "\" }";
        String replayed = mockMvc.perform(authenticated(post("/api/v1/bookings"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reordered))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(replayed).isEqualTo(first);
        assertThat(bookings.count()).isOne();
    }
}
