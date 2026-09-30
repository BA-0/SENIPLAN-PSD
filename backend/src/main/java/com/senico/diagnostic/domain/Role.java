package com.senico.diagnostic.domain;

public enum Role {
    ADMIN,
    /**
     * Direction generale : consulte et valide tout, sans l'administration technique
     * (groupes, mots de passe, cycles, edition des saisies), qui reste a ADMIN.
     * Voir SecurityConfig pour la traduction en regles d'acces.
     */
    DIRECTEUR_GENERAL,
    /**
     * Superviseur : suit en temps reel tout l'espace de pilotage, en lecture seule. Il ne
     * valide, n'approuve, ne modifie ni n'efface rien (cf. SecurityConfig).
     */
    SUPERVISEUR,
    GROUP_LEADER
}
