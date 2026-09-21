package com.auvan.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Backs the API's own CORS filter, kept in step with the actuator's identical
 * {@code management.endpoints.web.cors.allowed-origins} setting by sharing the
 * same {@code CORS_ALLOWED_ORIGINS} environment variable. Neither ever binds a
 * wildcard: requests here carry an {@code Authorization} header, and a
 * wildcard origin is both meaningless for a credentialed request and refused
 * by the browser.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) { }
