package com.auvan.api.booking;

import com.auvan.api.booking.entity.BookingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentProofUploadRulesIntegrationTests extends PaymentProofTestSupport {
    @Test
    void aFileThatIsNotAnAllowedImageIsRejected() throws Exception {
        submit(studentToken, bookingId,
                new MockMultipartFile("file", "slip.pdf", "application/pdf", "%PDF-1.7".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_type_not_supported"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void anImageOverTheCeilingIsRejected() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];

        submit(studentToken, bookingId, new MockMultipartFile("file", "slip.jpg", "image/jpeg", oversized))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_too_large"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }

    @Test
    void anEmptyFileIsRejected() throws Exception {
        submit(studentToken, bookingId, new MockMultipartFile("file", "slip.jpg", "image/jpeg", new byte[0]))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("payment_proof_empty"));

        assertNothingSubmitted(BookingStatus.PENDING_PAYMENT);
    }
}
