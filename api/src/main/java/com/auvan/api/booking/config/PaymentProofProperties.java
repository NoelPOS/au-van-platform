package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * Where payment-proof images are stored and how large one may be.
 *
 * <p>{@code endpoint} is blank against AWS S3 and set to the local
 * S3-compatible store locally and in {@code compose.yaml}; ADR-009 chose one
 * client against a configurable endpoint over a second storage driver.
 * Credentials are deliberately absent: the AWS SDK's default chain reads them
 * from the environment locally and from the task role in the deployed target,
 * so no secret is ever bound into this record or printed with the
 * configuration.
 */
@ConfigurationProperties(prefix = "payment-proof")
public record PaymentProofProperties(String bucket, String region, String endpoint, DataSize maxFileSize) { }
