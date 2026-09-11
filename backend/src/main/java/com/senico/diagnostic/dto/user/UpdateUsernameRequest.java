package com.senico.diagnostic.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nouvel identifiant de connexion, soumis aux memes regles qu'a la creation du compte. */
public record UpdateUsernameRequest(
        @NotBlank(message = "L'identifiant est requis")
        @Size(max = 60, message = "L'identifiant ne doit pas depasser 60 caracteres")
        @Pattern(regexp = "[a-zA-Z0-9._-]+",
                message = "L'identifiant ne peut contenir que des lettres, chiffres, point, tiret ou tiret bas")
        String username
) {
}
