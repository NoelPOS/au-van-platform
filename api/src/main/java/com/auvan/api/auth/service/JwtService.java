package com.auvan.api.auth.service;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.entity.AppUser;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
class JwtService {
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    JwtService(JwtEncoder jwtEncoder, AuthProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    String issue(AppUser user) {
        Instant issuedAt = Instant.now();
        var claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .audience(java.util.List.of(properties.jwt().audience()))
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.jwt().accessTokenTtl()))
                .claim("role", user.getRole().name())
                .claim("displayName", user.getDisplayName())
                .build();
        var headers = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();
    }
}
