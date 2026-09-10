package com.senico.diagnostic.dto.auth;

import lombok.Builder;

@Builder
public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresIn,
        UserInfo user
) {
    @Builder
    public record UserInfo(
            Long id,
            String username,
            String fullName,
            String role,
            Long groupId,
            String groupName,
            /** Vrai tant que le titulaire n'a pas remplace le mot de passe qu'on lui a remis. */
            boolean mustChangePassword
    ) {
    }
}
