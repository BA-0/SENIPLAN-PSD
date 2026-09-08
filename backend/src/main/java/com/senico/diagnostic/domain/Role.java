package com.senico.diagnostic.domain;

public enum Role {
    ADMIN,
    /**
     * Direction generale : consulte et valide tout, sans l'administration technique
     * (groupes, mots de passe, cycles, edition des saisies), qui reste a ADMIN.
     * Voir SecurityConfig pour la traduction en regles d'acces.
     */
    DIRECTEUR_GENERAL,
    GROUP_LEADER
}
