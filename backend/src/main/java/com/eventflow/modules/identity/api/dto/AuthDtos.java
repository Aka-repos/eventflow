package com.eventflow.modules.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** DTOs espejo EXACTO de components.schemas del contrato (docs/api/05-openapi.yaml). Congelados. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(min = 1, max = 200) String fullName,
            @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "debe ser E.164") String phone) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(@NotBlank String refreshToken) {
    }

    public record UserProfileDto(UUID id, String email, String fullName, String phone,
                                 List<String> roles, Instant createdAt) {
    }

    public record AuthTokensResponse(String accessToken, long accessTokenExpiresIn,
                                     String refreshToken, UserProfileDto user) {
    }
}
