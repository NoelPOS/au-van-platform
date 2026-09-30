package com.auvan.api.booking.service;

import com.auvan.api.booking.config.PaymentProofProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * The S3 implementation, used against AWS S3 in the deployed target and
 * against the local S3-compatible store locally and in CI — the same client
 * either way, per ADR-009, with only the endpoint differing.
 *
 * <p>Named for its backend rather than {@code PaymentProofStorageImpl}: the
 * port has a second implementation in test sources, so "Impl" would say less
 * than "S3" does.
 */
@Component
public class S3PaymentProofStorage implements PaymentProofStorage {
    private final S3Client s3;
    private final String bucket;

    public S3PaymentProofStorage(S3Client s3, PaymentProofProperties properties) {
        this.s3 = s3;
        this.bucket = properties.bucket();
    }

    @Override
    public void store(String objectKey, String contentType, byte[] content) {
        // No ACL and no public-read grant: the bucket is private and the API
        // is the only thing that ever reads back from it.
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] load(String objectKey) {
        // Throws NoSuchKeyException when the object is gone, which the caller
        // turns into a clean 503 rather than letting the SDK's own message —
        // bucket, endpoint, and all — reach the administrator's browser.
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build()).asByteArray();
    }
}
