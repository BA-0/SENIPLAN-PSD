package com.senico.diagnostic.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateWorkGroupRequest(
        @NotBlank(message = "Le nom du groupe est requis")
        @Size(max = 150)
        String name,

        String description,

        /** Couleur hexadecimale (#RRGGBB) ; assignee automatiquement depuis une palette si absente. */
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "La couleur doit etre au format hexadecimal #RRGGBB")
        String color,

        @NotBlank(message = "L'identifiant du chef de groupe est requis")
        @Size(max = 60)
        String leaderUsername,

        @NotBlank(message = "Le nom complet du chef de groupe est requis")
        @Size(max = 150)
        String leaderFullName,

        /** Mot de passe initial optionnel ; genere aleatoirement si absent. */
        String leaderPassword
) {
}
