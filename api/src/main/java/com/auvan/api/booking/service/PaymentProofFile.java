package com.auvan.api.booking.service;

import com.auvan.api.booking.exception.Problems;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What a payment proof is allowed to be, and where it is filed.
 *
 * <p>The allowlist and the five-megabyte ceiling are the legacy application's
 * own rules ({@code payment-proof-storage.shared.ts}); what is deliberately
 * not ported is its habit of handing back a public URL (ADR-009).
 *
 * <p>The type is taken from the declared content type, not sniffed from the
 * bytes. That is enough for this slice: the object is private, the API never
 * serves it back to a browser in this change, and #52's review endpoint is
 * where a stored proof first becomes something anybody renders.
 */
public final class PaymentProofFile {
    /** The three image types the legacy application accepted, and their extensions. */
    private static final Map<String, String> ALLOWED_TYPES =
            Map.of("image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private PaymentProofFile() { }

    /** An image type the allowlist accepts, and the extension its key gets. */
    public record AcceptedImage(String contentType, String extension) { }

    /**
     * Accepts one of the three allowed image types. A content type outside the
     * allowlist — including none at all — is refused here rather than being
     * stored and discovered later by whoever tries to display it. What comes
     * back is the canonical type, so the stored metadata does not carry
     * whatever casing and parameters the client happened to send.
     */
    public static AcceptedImage accept(String contentType) {
        String type = normalise(contentType);
        String extension = ALLOWED_TYPES.get(type);
        if (extension == null) {
            throw Problems.badRequest("payment_proof_type_not_supported",
                    "A payment proof must be a JPEG, PNG, or WebP image.");
        }
        return new AcceptedImage(type, extension);
    }

    /** Refuses an empty upload and one over the configured ceiling. */
    public static void assertSizeWithin(long sizeBytes, long maxBytes) {
        if (sizeBytes <= 0) {
            throw Problems.badRequest("payment_proof_empty", "The payment proof file is empty.");
        }
        if (sizeBytes > maxBytes) {
            throw Problems.badRequest("payment_proof_too_large",
                    "A payment proof must be " + maxBytes / (1024 * 1024) + "MB or smaller.");
        }
    }

    /**
     * {@code payment-proofs/{bookingId}/{timestamp}-{uuid}{ext}}, as the legacy
     * application named them. The UUID is what makes a key unique, so a second
     * submission in the same millisecond cannot overwrite the first.
     */
    public static String objectKey(UUID bookingId, String extension, OffsetDateTime now) {
        return "payment-proofs/" + bookingId + "/" + now.toInstant().toEpochMilli() + "-"
                + UUID.randomUUID() + extension;
    }

    /** {@code image/jpeg; charset=binary} and {@code IMAGE/JPEG} are the same type. */
    private static String normalise(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parameters = contentType.indexOf(';');
        return (parameters < 0 ? contentType : contentType.substring(0, parameters)).trim()
                .toLowerCase(Locale.ROOT);
    }
}
