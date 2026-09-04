package com.auvan.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public abstract class AuthenticationTestSupport {
    @DynamicPropertySource
    static void authenticationProperties(DynamicPropertyRegistry registry) {
        registry.add("auth.jwt.secret", () -> Base64.getEncoder().encodeToString(
                "test-jwt-signing-key-must-be-32-bytes-long".getBytes(StandardCharsets.UTF_8)));
    }
}
