package com.auvan.api.auth.dto;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.entity.ApplicationRole;
import java.util.UUID;

public record UserResponse(UUID id, ApplicationRole role, String displayName) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getRole(), user.getDisplayName());
    }
}
