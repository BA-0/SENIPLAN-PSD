package com.senico.diagnostic.dto.user;

import java.time.LocalDateTime;

/**
 * Compte transverse (admin, direction generale) : ceux qui ne sont rattaches a aucun groupe
 * de travail et n'apparaissent donc pas dans l'ecran des directions.
 */
public record StaffAccountDto(
        Long id,
        String username,
        String fullName,
        String role,
        String roleLabel,
        boolean enabled,
        LocalDateTime lastLoginAt
) {
}
