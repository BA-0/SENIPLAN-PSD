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
            String groupName
    ) {
    }
}
