package com.auvan.api.booking.service;

import com.auvan.api.booking.exception.Problems;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PaymentProofFile {
    private static final Map<String, String> ALLOWED_TYPES =
            Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private PaymentProofFile() { }

    public record AcceptedImage(String contentType, String extension) { }

    public static AcceptedImage accept(String contentType) {
        String type = normalise(contentType);
        String extension = ALLOWED_TYPES.get(type);
        if (extension == null) {
            throw Problems.badRequest("payment_proof_type_not_supported",
                    "A payment proof must be a JPEG, PNG, or WebP image.");
        }
        return new AcceptedImage(type, extension);
    }

    public static void assertSizeWithin(long sizeBytes, long maxBytes) {
        if (sizeBytes <= 0) {
            throw Problems.badRequest("payment_proof_empty", "The payment proof file is empty.");
        }
        if (sizeBytes > maxBytes) {
            throw Problems.badRequest("payment_proof_too_large",
                    "A payment proof must be " + maxBytes / (1024 * 1024) + "MB or smaller.");
        }
    }

    public static String objectKey(UUID bookingId, String extension, OffsetDateTime now) {
        return "payment-proofs/" + bookingId + "/" + now.toInstant().toEpochMilli() + "-"
                + UUID.randomUUID() + extension;
    }

    private static String normalise(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim()
                .toLowerCase(Locale.ROOT);
    }
}
