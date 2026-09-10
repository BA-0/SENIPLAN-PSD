package com.senico.diagnostic.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Changement de mot de passe par son titulaire. L'ancien est redemande meme lorsque le
 * changement est impose : le jeton d'acces peut avoir ete laisse sur un poste ouvert, et
 * connaitre le mot de passe en cours est ce qui prouve qu'on est bien le titulaire.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Le mot de passe actuel est requis") String currentPassword,
        @NotBlank(message = "Le nouveau mot de passe est requis")
        @Size(min = 8, max = 100, message = "Le nouveau mot de passe doit faire au moins 8 caracteres")
        String newPassword
) {
}
