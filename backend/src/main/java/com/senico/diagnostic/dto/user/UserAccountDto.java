package com.senico.diagnostic.dto.user;

import java.time.LocalDateTime;

/**
 * Compte utilisateur vu par l'admin : aussi bien les comptes transverses (admin, direction
 * generale) que les chefs de groupe, qui portent en plus le nom de leur direction.
 *
 * @param temporaryPassword renseigne seulement en reponse a une creation, ou il s'affiche une
 *                          seule fois ; nul partout ailleurs, et jamais relu depuis la base —
 *                          seul le hash y est conserve.
 */
public record UserAccountDto(
        Long id,
        String username,
        String fullName,
        String role,
        String roleLabel,
        Long groupId,
        String groupName,
        boolean enabled,
        /** Le compte n'a pas encore remplace le mot de passe qu'on lui a remis. */
        boolean mustChangePassword,
        LocalDateTime lastLoginAt,
        String temporaryPassword
) {
    public UserAccountDto withTemporaryPassword(String rawPassword) {
        return new UserAccountDto(id, username, fullName, role, roleLabel, groupId, groupName,
                enabled, mustChangePassword, lastLoginAt, rawPassword);
    }
}
