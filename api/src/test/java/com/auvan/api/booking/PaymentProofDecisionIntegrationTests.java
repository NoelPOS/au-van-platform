package com.auvan.api.booking;

import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentProofDecisionIntegrationTests extends PaymentProofReviewTestSupport {
    @Test
    void approvingConfirmsTheBookingAndRecordsWhoDecidedItAndWhen() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        approve(proofId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[2].type").value("PAYMENT_APPROVED"))
                .andExpect(jsonPath("$.events[2].actorUserId").value(adminId.toString()));

        PaymentProof decided = proofs.findById(proofId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(PaymentProofStatus.APPROVED);
        assertThat(decided.getReviewedByUserId()).isEqualTo(adminId);
        assertThat(decided.getReviewedAt()).isNotNull();
        assertThat(decided.getReviewNote()).isNull();
        mockMvc.perform(get("/api/v1/bookings/" + bookingId).header("Authorization", bearer(studentToken)))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void rejectingRecordsTheReasonAndLeavesTheSeatsHeld() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID proofId = proofIdOf(bookingId);

        reject(proofId, "The slip is too blurred to read.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.events.length()").value(3))
                .andExpect(jsonPath("$.events[2].type").value("PAYMENT_REJECTED"))
                .andExpect(jsonPath("$.events[2].detail").value("The slip is too blurred to read."))
                .andExpect(jsonPath("$.events[2].actorUserId").value(adminId.toString()));

        PaymentProof decided = proofs.findById(proofId).orElseThrow();
        assertThat(decided.getStatus()).isEqualTo(PaymentProofStatus.REJECTED);
        assertThat(decided.getReviewNote()).isEqualTo("The slip is too blurred to read.");
        assertThat(claims.findByBookingId(UUID.fromString(bookingId))).hasSize(1);
    }

    @Test
    void aStudentCanSubmitAnotherProofAfterARejectionAndTheOldOneStaysAsHistory() throws Exception {
        submit(bookingId, jpeg("the-slip"));
        UUID rejectedId = proofIdOf(bookingId);
        reject(rejectedId, "The slip is too blurred to read.").andExpect(status().isOk());

        submit(bookingId, jpeg("a-clearer-slip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAYMENT_UNDER_REVIEW"));

        assertThat(proofs.count()).isEqualTo(2);
        assertThat(storage.objects()).hasSize(2);
        PaymentProof rejected = proofs.findById(rejectedId).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(PaymentProofStatus.REJECTED);
        assertThat(rejected.getReviewNote()).isEqualTo("The slip is too blurred to read.");
        String body = queue().andExpect(jsonPath("$.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        UUID waiting = UUID.fromString(JsonPath.read(body, "$[0].id"));
        assertThat(waiting).isNotEqualTo(rejectedId);

        approve(waiting, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void theSchemaRefusesADecidedProofThatDoesNotSayWhoDecidedIt() {
        insertProof("SUBMITTED");

        assertThatThrownBy(() -> insertProof("APPROVED")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertProof("REJECTED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertProof(String status) {
        jdbc.update("""
                insert into payment_proofs (id, booking_id, submitted_by_user_id, object_key, content_type,
                                            size_bytes, status, created_at)
                values (?, ?, ?, ?, 'image/jpeg', 12, ?, ?)
                """, UUID.randomUUID(), UUID.fromString(bookingId), studentId,
                "payment-proofs/raw/" + UUID.randomUUID() + ".jpg", status, OffsetDateTime.now());
    }
}
