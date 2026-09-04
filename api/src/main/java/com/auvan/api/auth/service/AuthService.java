package com.auvan.api.auth.service;

import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.dto.AuthResponse;
import com.auvan.api.auth.dto.UserResponse;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final LineTokenVerifier lineTokenVerifier;
    private final AppUserRepository users;
    private final JwtService jwtService;
    private final AuthProperties properties;

    AuthService(LineTokenVerifier lineTokenVerifier, AppUserRepository users, JwtService jwtService, AuthProperties properties) {
        this.lineTokenVerifier = lineTokenVerifier;
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse exchangeLineToken(String idToken) {
        var identity = lineTokenVerifier.verify(idToken);
        var user = users.findByLineSubject(identity.subject())
                .map(existing -> {
                    existing.updateDisplayName(identity.displayName());
                    return existing;
                })
                .orElseGet(() -> users.save(new AppUser(identity.subject(), identity.displayName())));

        return new AuthResponse(
                jwtService.issue(user),
                properties.jwt().accessTokenTtl().toSeconds(),
                UserResponse.from(user));
    }
}
