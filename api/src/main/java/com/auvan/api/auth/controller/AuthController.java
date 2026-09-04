package com.auvan.api.auth.controller;

import com.auvan.api.auth.dto.AuthResponse;
import com.auvan.api.auth.dto.LineExchangeRequest;
import com.auvan.api.auth.dto.UserResponse;
import com.auvan.api.auth.entity.ApplicationRole;
import com.auvan.api.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/line/exchange")
    public ResponseEntity<AuthResponse> exchangeLineToken(@Valid @RequestBody LineExchangeRequest request) {
        return ResponseEntity.ok(authService.exchangeLineToken(request.idToken()));
    }

    @GetMapping("/auth/me")
    public UserResponse currentUser(@AuthenticationPrincipal Jwt jwt) {
        return new UserResponse(
                UUID.fromString(jwt.getSubject()),
                ApplicationRole.valueOf(jwt.getClaimAsString("role")),
                jwt.getClaimAsString("displayName"));
    }

    @GetMapping("/admin/access-check")
    public ResponseEntity<Void> adminAccessCheck() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
