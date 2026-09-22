package com.auvan.api.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(Jwt jwt, Line line, AdminBootstrap adminBootstrap) {
    public record Jwt(String issuer, String audience, String secret, Duration accessTokenTtl) { }

    /**
     * The LINE <em>Login</em> channel a LIFF id token is verified against, and
     * the host that verification is sent to.
     *
     * <p>{@code apiBaseUrl} defaults to {@code https://api.line.me} and exists
     * for the same reason {@code notification.line.api-base-url} does one
     * package over: a test has to be able to point the real client at a server
     * it controls. <strong>It changes the host and nothing else.</strong>
     * {@code LineTokenVerifierImpl} still requires {@code iss} to be
     * {@code https://access.line.me}, {@code aud} to equal {@code channelId},
     * and {@code sub} to be non-blank, whatever this is set to, so a made-up
     * token is still refused by an instance pointed at LINE (ADR-013).
     *
     * <p>A deployment that moves this away from LINE accepts whatever that
     * other host asserts. The default is the mitigation, and it is worth
     * checking on every change to the file that carries it.
     */
    public record Line(String channelId, String apiBaseUrl) { }

    public record AdminBootstrap(String lineSubject) { }
}
