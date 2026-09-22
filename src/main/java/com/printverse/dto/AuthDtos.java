package com.printverse.dto;

import com.printverse.domain.AppUser;
import com.printverse.domain.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 200) String password) {
    }

    public record UserResponse(String username, String displayName, UserRole role) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getUsername(), user.getDisplayName(), user.getRole());
        }
    }

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
    }
}
