package com.senico.diagnostic.domain;

/**
 * Cles des blocs de texte narratif du "Plan Strategique de SENICO" : les parties
 * du sommaire client qui ne proviennent d'aucune section du canevas de diagnostic.
 * L'ordre de l'enum pilote l'ordre d'affichage dans l'UI admin d'edition.
 */
public enum NarrativeBlockKey {

    MOT_DU_DG("Mot du DG"),
    PREAMBULE("Préambule"),
    INTRODUCTION("Introduction"),
    SYNTHESE_PSD("Synthèse du PSD"),
    MISSIONS("Rappel des missions"),
    ORGANISATION("Organisation"),
    RESSOURCES("Ressources"),
    DEFIS_A_RELEVER("Défis à relever"),
    ENJEUX("Enjeux");

    private final String label;

    NarrativeBlockKey(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
