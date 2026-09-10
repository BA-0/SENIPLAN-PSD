package com.senico.diagnostic.dto.user;

import com.senico.diagnostic.domain.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Creation d'un compte par l'admin.
 *
 * @param groupId  direction de rattachement, exigee pour un chef de groupe et refusee pour les
 *                 autres roles : c'est ce rattachement, et lui seul, qui decide du canevas que
 *                 le compte remplit.
 * @param password laisse vide, un mot de passe est genere et affiche une seule fois. Dans les
 *                 deux cas le titulaire devra le remplacer a sa premiere connexion.
 */
public record CreateUserAccountRequest(
        @NotBlank(message = "L'identifiant est requis")
        @Size(max = 60, message = "L'identifiant ne doit pas depasser 60 caracteres")
        @Pattern(regexp = "[a-zA-Z0-9._-]+",
                message = "L'identifiant ne peut contenir que des lettres, chiffres, point, tiret ou tiret bas")
        String username,

        @NotBlank(message = "Le nom complet est requis")
        @Size(max = 150, message = "Le nom complet ne doit pas depasser 150 caracteres")
        String fullName,

        @NotNull(message = "Le role est requis") Role role,

        Long groupId,

        @Size(min = 8, max = 100, message = "Le mot de passe doit faire au moins 8 caracteres")
        String password
) {
    /**
     * Un champ laisse vide arrive en chaine vide et non en absence de valeur : sans cette
     * normalisation, la contrainte de longueur le rejetterait au lieu de declencher la
     * generation automatique.
     */
    public CreateUserAccountRequest {
        if (password != null && password.isBlank()) {
            password = null;
        }
    }
}
