package com.auvan.api.booking;

import com.auvan.api.booking.service.PaymentProofFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentProofFileTests {
    private static final long FIVE_MEGABYTES = 5L * 1024 * 1024;

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg", "image/png", "image/webp"})
    void everyAllowedImageTypeIsAcceptedAndCarriesAnExtension(String contentType) {
        PaymentProofFile.AcceptedImage accepted = PaymentProofFile.accept(contentType);

        assertThat(accepted.contentType()).isEqualTo(contentType);
        assertThat(accepted.extension()).isIn(".jpg", ".png", ".webp");
    }

    @ParameterizedTest
    @ValueSource(strings = {"IMAGE/JPEG", "image/jpeg; charset=binary"})
    void theDeclaredTypeIsNormalisedBeforeItIsMatched(String contentType) {
        assertThat(PaymentProofFile.accept(contentType).contentType()).isEqualTo("image/jpeg");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"application/pdf", "image/gif", "text/plain", "application/octet-stream", ""})
    void anythingOutsideTheAllowlistIsRefused(String contentType) {
        assertThatThrownBy(() -> PaymentProofFile.accept(contentType))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrown -> assertProblem(thrown, HttpStatus.BAD_REQUEST,
                        "payment_proof_type_not_supported"));
    }

    @Test
    void aFileAtTheCeilingIsAccepted() {
        PaymentProofFile.assertSizeWithin(FIVE_MEGABYTES, FIVE_MEGABYTES);
    }

    @Test
    void aFileOverTheCeilingIsRefused() {
        assertThatThrownBy(() -> PaymentProofFile.assertSizeWithin(FIVE_MEGABYTES + 1, FIVE_MEGABYTES))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrown -> assertProblem(thrown, HttpStatus.BAD_REQUEST, "payment_proof_too_large"));
    }

    @Test
    void anEmptyFileIsRefused() {
        assertThatThrownBy(() -> PaymentProofFile.assertSizeWithin(0, FIVE_MEGABYTES))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(thrown -> assertProblem(thrown, HttpStatus.BAD_REQUEST, "payment_proof_empty"));
    }

    @Test
    void everyObjectKeyIsUniqueAndFiledUnderItsBooking() {
        UUID bookingId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-09-21T10:00:00Z");

        String first = PaymentProofFile.objectKey(bookingId, ".jpg", now);
        String second = PaymentProofFile.objectKey(bookingId, ".jpg", now);

        assertThat(first).startsWith("payment-proofs/" + bookingId + "/").endsWith(".jpg");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void theContentHashIsTheHexSha256OfTheBytes() {
        assertThat(PaymentProofFile.sha256("abc".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    private static void assertProblem(Throwable thrown, HttpStatus status, String code) {
        ResponseStatusException problem = (ResponseStatusException) thrown;
        assertThat(problem.getStatusCode()).isEqualTo(status);
        assertThat(problem.getBody().getProperties()).containsEntry("code", code);
    }
}
