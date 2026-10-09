package com.auvan.api.booking;

import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.PaymentProofStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentProofReviewRefusalIntegrationTests extends PaymentProofReviewTestSupport {
    @Test
    void aStudentTokenIsForbiddenOnEveryReviewEndpoint() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(get("/api/v1/admin/payment-proofs").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(image(proofId)).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(decision(proofId, "approve", "{}").header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(decision(proofId, "reject", "{\"note\":\"no\"}")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());

        assertNothingWasDecided();
    }

    @Test
    void anAnonymousRequestIsUnauthorizedOnEveryReviewEndpoint() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(get("/api/v1/admin/payment-proofs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(image(proofId))).andExpect(status().isUnauthorized());
        mockMvc.perform(decision(proofId, "approve", "{}")).andExpect(status().isUnauthorized());
        mockMvc.perform(decision(proofId, "reject", "{\"note\":\"no\"}")).andExpect(status().isUnauthorized());

        assertNothingWasDecided();
    }

    @Test
    void aProofThatDoesNotExistIsNotFoundOnEveryDecisionEndpoint() throws Exception {
        UUID unknown = UUID.randomUUID();

        mockMvc.perform(get(image(unknown)).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
        approve(unknown, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
        reject(unknown, "No.").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_proof_not_found"));
    }

    @Test
    void aProofThatWasAlreadyDecidedCannotBeDecidedAgain() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        approve(proofId, null).andExpect(status().isOk());

        approve(proofId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));
        reject(proofId, "Changed my mind.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));

        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aRejectedProofCannotBeRejectedAgain() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        reject(proofId, "The slip is too blurred to read.").andExpect(status().isOk());

        reject(proofId, "Still blurred.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_proof_already_decided"));

        assertThat(proofs.findById(proofId).orElseThrow().getReviewNote())
                .isEqualTo("The slip is too blurred to read.");
    }

    @Test
    void aProofWhoseBookingTheStudentCancelledCannotBeApproved() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/cancel")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isOk());

        approve(proofId, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("booking_not_under_review"));

        assertThat(bookings.findById(UUID.fromString(bookingId)).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
        assertThat(proofs.findById(proofId).orElseThrow().getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
    }

    @Test
    void rejectingWithoutSayingWhyIsRefused() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        mockMvc.perform(decision(proofId, "reject", "{}").header("Authorization", bearer(adminToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_required"));
        reject(proofId, "   ").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_required"));

        assertNothingWasDecided();
    }

    @Test
    void aNoteLongerThanTheColumnIsRefusedRatherThanTruncated() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        reject(proofId, "n".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_too_long"));
        approve(proofId, "n".repeat(501)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("review_note_too_long"));

        assertNothingWasDecided();
        reject(proofId, "n".repeat(500)).andExpect(status().isOk());
    }

    private void assertNothingWasDecided() {
        assertThat(proofs.findAll()).allSatisfy(proof -> {
            assertThat(proof.getStatus()).isEqualTo(PaymentProofStatus.SUBMITTED);
            assertThat(proof.getReviewedByUserId()).isNull();
            assertThat(proof.getReviewedAt()).isNull();
        });
        assertThat(bookings.findAll()).allSatisfy(booking ->
                assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW));
    }
}
