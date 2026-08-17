package com.senico.diagnostic.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
        @NotBlank(message = "Le refresh token est requis") String refreshToken
) {
}
