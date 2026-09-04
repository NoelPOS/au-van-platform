package com.auvan.api.auth.dto;

public record AuthResponse(String accessToken, long expiresIn, UserResponse user) { }
