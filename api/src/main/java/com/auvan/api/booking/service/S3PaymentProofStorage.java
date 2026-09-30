package com.auvan.api.booking.service;

import com.auvan.api.booking.config.PaymentProofProperties;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public byte[] load(String objectKey) {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build()).asByteArray();
    }
}
