package com.auvan.api.booking;

import com.auvan.api.inventory.entity.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentProofQueueIntegrationTests extends PaymentProofReviewTestSupport {
    @Test
    void theQueueListsSubmittedProofsOldestFirstWithTheBookingContextToDecideOn() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        Trip laterTrip = createTrip("VAN-02", OffsetDateTime.now().plusDays(2));
        String secondBooking = bookingIdFrom(confirm(holdOn(laterTrip.getSeats().getFirst()), "key-2"));
        submit(secondBooking, jpeg("another-slip"));

        queue().andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].bookingId").value(bookingId))
                .andExpect(jsonPath("$[1].bookingId").value(secondBooking))
                .andExpect(jsonPath("$[0].bookingReference").isNotEmpty())
                .andExpect(jsonPath("$[0].passengerName").value("Somchai P."))
                .andExpect(jsonPath("$[0].passengerPhone").value("0812345678"))
                .andExpect(jsonPath("$[0].totalFare").value(35.00))
                .andExpect(jsonPath("$[0].trip.origin").value("AU"))
                .andExpect(jsonPath("$[0].trip.destination").value("Asok"))
                .andExpect(jsonPath("$[0].trip.departureAt").isNotEmpty())
                .andExpect(jsonPath("$[0].submittedByUserId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$[0].sizeBytes").value("the-slip".length()))
                .andExpect(jsonPath("$[0].status").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].submittedAt").isNotEmpty());

        approve(proofIdOf(bookingId), null).andExpect(status().isOk());

        queue().andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].bookingId").value(secondBooking));
    }

    @Test
    void theQueueCarriesNoObjectKeyBucketOrUrl() throws Exception {
        submit(bookingId, jpeg("the-slip"));

        String body = queue().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("payment-proofs/")
                .doesNotContain("test-payment-proofs")
                .doesNotContain("objectKey")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    @Test
    void theImageEndpointReturnsTheExactBytesTheStudentUploaded() throws Exception {
        submit(bookingId, jpeg("the-slip"));

        byte[] returned = mockMvc.perform(get(image(proofIdOf(bookingId))).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Content-Disposition", "inline"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(returned).isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void anImageThatCannotBeReadAnswersWithoutNamingTheBucket() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        storage.failEveryLoad();

        String body = mockMvc.perform(get(image(proofIdOf(bookingId)))
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("payment_proof_unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException").doesNotContain("unreachable")
                .doesNotContain("bucket");
    }
}
