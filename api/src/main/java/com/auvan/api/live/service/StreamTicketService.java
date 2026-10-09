package com.auvan.api.live.service;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.live.config.LiveUpdatesProperties;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// SECURITY CONTROL: a ticket's own audience keeps tickets and access tokens apart.
@Service
public class StreamTicketService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties auth;
    private final LiveUpdatesProperties properties;

    public StreamTicketService(JwtEncoder encoder, SecretKey jwtSigningKey, AuthProperties auth,
                               LiveUpdatesProperties properties) {
        this.encoder = encoder;
        this.auth = auth;
        this.properties = properties;
        NimbusJwtDecoder ticketDecoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256).build();
        ticketDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(auth.jwt().issuer()),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                        audience -> audience != null && audience.contains(audience()))));
        this.decoder = ticketDecoder;
    }

    public String issue(Jwt accessToken) {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(auth.jwt().issuer())
                .audience(List.of(audience()))
                .subject(accessToken.getSubject())
                .issuedAt(now)
                .expiresAt(now.plus(properties.ticketTtl()))
                .claim("role", accessToken.getClaimAsString("role"))
                .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }

    public LiveSubscriber verify(String ticket) {
        try {
            Jwt verified = decoder.decode(ticket);
            return new LiveSubscriber(UUID.fromString(verified.getSubject()),
                    "ADMIN".equals(verified.getClaimAsString("role")));
        } catch (JwtException | IllegalArgumentException rejected) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The live-updates ticket is not valid.");
        }
    }

    private String audience() {
        return auth.jwt().audience() + "-events";
    }
}
