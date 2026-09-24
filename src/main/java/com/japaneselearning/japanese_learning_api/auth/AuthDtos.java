package com.japaneselearning.japanese_learning_api.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 100) String displayName,
            @Size(max = 200) String deviceName) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Size(max = 200) String deviceName) {}

    public record RefreshRequest(@NotBlank String refreshToken, @Size(max = 200) String deviceName) {}
    public record LogoutRequest(@NotBlank String refreshToken) {}
    public record AuthResponse(String accessToken, String refreshToken, long expiresIn, UserResponse user) {}
    public record UserResponse(UUID id, String email, String displayName, String avatarUrl, String role,
                               String locale, String timezone, Instant createdAt) {}
}
