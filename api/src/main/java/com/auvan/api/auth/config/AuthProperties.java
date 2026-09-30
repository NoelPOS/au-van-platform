package com.auvan.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Line line, AdminBootstrap adminBootstrap) {
    public record Jwt(String issuer, String audience, String secret, Duration accessTokenTtl) { }

    public record Line(String channelId, String apiBaseUrl) { }

    public record AdminBootstrap(String lineSubject) { }
}
