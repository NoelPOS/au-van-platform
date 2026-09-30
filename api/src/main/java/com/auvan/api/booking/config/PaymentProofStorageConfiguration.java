package com.auvan.api.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
public class PaymentProofStorageConfiguration {
    /**
     * Built without an explicit credentials provider, so the SDK's default
     * chain applies: environment variables locally and in {@code
     * compose.yaml}, the task role in the deployed target. Nothing here can
     * resolve a credential at startup, which is what lets the application boot
     * in CI and in a container check with no storage configured at all.
     */
    @Bean
    S3Client paymentProofS3Client(PaymentProofProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // The local S3-compatible store serves one host for every bucket,
            // so the bucket has to be the first path segment rather than a
            // subdomain.
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }
}
