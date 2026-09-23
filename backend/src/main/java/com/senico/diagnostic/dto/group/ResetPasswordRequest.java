package com.senico.diagnostic.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mot de passe choisi par l'admin lors d'une reinitialisation, a charge pour lui de le
 * transmettre. Le titulaire devra le remplacer des sa prochaine connexion.
 */
public record ResetPasswordRequest(
        @NotBlank(message = "Le nouveau mot de passe est requis")
        @Size(min = 8, max = 100, message = "Le mot de passe doit faire au moins 8 caracteres")
        String password
) {
}
