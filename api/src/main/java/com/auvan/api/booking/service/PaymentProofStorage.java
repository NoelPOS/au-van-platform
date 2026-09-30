package com.auvan.api.booking.service;

/**
 * Where a payment-proof image goes. One production implementation talks S3
 * (AWS in the deployed target, the local S3-compatible store locally and in
 * {@code compose.yaml}); a second, in-memory one lives in test sources so the
 * submit path can be exercised — and made to fail — without a bucket.
 *
 * <p>Nothing here returns a URL. ADR-009 rejected the legacy application's
 * public-URL shape outright: every byte crosses the API, so the only way to
 * reach an image is an authenticated request the API authorizes itself.
 */
public interface PaymentProofStorage {
    /**
     * Writes the image under {@code objectKey}. The key carries a UUID, so a
     * store never overwrites an earlier submission.
     *
     * @throws RuntimeException if the object could not be stored; the caller
     *                          turns that into a clean response and leaves no
     *                          half-written booking behind.
     */
    void store(String objectKey, String contentType, byte[] content);

    /**
     * Reads the image back for the administrator reviewing it.
     *
     * <p>Returns the bytes rather than a stream: the ceiling is five megabytes
     * and {@code payment-proof.max-file-size} enforces it, so there is nothing
     * to stream defensively around. ADR-009's "streams it back" is about who
     * brokers the bytes — the API, never a presigned URL — not about the type.
     *
     * @throws RuntimeException if the object could not be read, including when
     *                          it is not there; never {@code null}, so a caller
     *                          cannot mistake a missing image for an empty one.
     */
    byte[] load(String objectKey);
}
