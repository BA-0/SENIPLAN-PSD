package com.senico.diagnostic.dto.group;

public record ResetPasswordResponse(
        String username,
        String temporaryPassword
) {
}
