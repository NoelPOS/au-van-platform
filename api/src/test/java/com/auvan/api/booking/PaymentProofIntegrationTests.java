package com.auvan.api.booking;

import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentProofIntegrationTests extends PaymentProofTestSupport {
    @Test
    void submittingAProofStoresTheImageAndPutsTheBookingUnderReview() throws Exception {
        submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"))
                .andExpect(jsonPath("$.events.length()").value(2))
                .andExpect(jsonPath("$.events[1].type").value("PAYMENT_PROOF_SUBMITTED"))
                .andExpect(jsonPath("$.events[1].detail").value("Payment proof submitted for review."))
                .andExpect(jsonPath("$.events[1].actorUserId").value(studentId.toString()));

        List<PaymentProof> stored = proofs.findAll();
        assertThat(stored).singleElement().satisfies(proof -> {
            assertThat(proof.getSubmittedByUserId()).isEqualTo(studentId);
            assertThat(proof.getContentType()).isEqualTo("image/jpeg");
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
            assertThat(proof.getSizeBytes()).isEqualTo("the-slip".length());
            assertThat(proof.getObjectKey()).startsWith("payment-proofs/" + bookingId + "/").endsWith(".jpg");
        });
        assertThat(storage.objects()).containsOnlyKeys(stored.getFirst().getObjectKey());
        assertThat(storage.objects().get(stored.getFirst().getObjectKey()).content())
                .isEqualTo("the-slip".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"));
    }

    @Test
    void theResponseCarriesNoObjectKeyBucketOrUrl() throws Exception {
        String body = submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("payment-proofs/")
                .doesNotContain("test-payment-proofs")
                .doesNotContain("http://")
                .doesNotContain("https://");
    }

    @Test
    void submittingAgainstAnotherStudentsBookingIsNotFoundAndChangesNothing() throws Exception {
        String otherToken = tokenFor("other-token", "Uother");

        submit(otherToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void submittingAgainstABookingThatDoesNotExistAnswersIdentically() throws Exception {
        submit(studentToken, UUID.randomUUID().toString(), jpeg("the-slip"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("booking_not_found"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void aSecondProofWhileTheFirstIsUnderReviewIsRejected() throws Exception {
        submit(studentToken, bookingId, jpeg("the-slip")).andExpect(status().isOk());

        submit(studentToken, bookingId, jpeg("another-slip"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_awaiting_payment"));

        assertThat(proofs.count()).isOne();
        assertThat(storage.objects()).hasSize(1);
    }

    @Test
    void submittingAgainstACancelledBookingIsRejected() throws Exception {
        mockMvc.perform(authenticated(post("/api/v1/bookings/" + bookingId + "/cancel")))
                .andExpect(status().isOk());

        submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_awaiting_payment"));

        assertNothingSubmitted(BookingStatus.CANCELLED);
    }

    @Test
    void anAnonymousSubmissionIsRefused() throws Exception {
        mockMvc.perform(multipart("/api/v1/bookings/" + bookingId + "/payment-proof").file(jpeg("the-slip")))
                .andExpect(status().isUnauthorized());

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void aStorageFailureLeavesTheBookingExactlyAsItWas() throws Exception {
        storage.failEveryStore();

        String body = submit(studentToken, bookingId, jpeg("the-slip"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("payment_proof_storage_unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("IllegalStateException").doesNotContain("unreachable");
        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
        mockMvc.perform(authenticated(get("/api/v1/bookings/" + bookingId)))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.events.length()").value(1));
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private MockHttpServletRequestBuilder authenticated(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + studentToken);
    }
}
