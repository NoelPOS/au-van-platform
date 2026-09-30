package com.auvan.api.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.net.URI;

@Configuration
public class PaymentProofStorageConfiguration {
    @Bean
    S3Client paymentProofS3Client(PaymentProofProperties properties) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(properties.region()));
        if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
            // Path-style: the local store serves every bucket from one host.
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }
}
