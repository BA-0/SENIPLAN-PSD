package com.senico.diagnostic.domain;

/**
 * Cles des blocs de texte narratif du "Document final PSD 2027-2031" : les parties
 * du sommaire client qui ne proviennent d'aucune des 17 sections du canevas de diagnostic.
 * L'ordre de l'enum pilote l'ordre d'affichage dans l'UI admin d'edition.
 */
public enum NarrativeBlockKey {

    MOT_DU_DG("Mot du DG"),
    PREAMBULE("Préambule"),
    INTRODUCTION("Introduction"),
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
