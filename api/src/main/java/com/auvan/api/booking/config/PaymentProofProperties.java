package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "payment-proof")
public record PaymentProofProperties(String bucket, String region, String endpoint, DataSize maxFileSize) { }
