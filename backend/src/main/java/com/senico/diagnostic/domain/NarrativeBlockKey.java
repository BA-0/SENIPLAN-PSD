package com.senico.diagnostic.domain;

/**
 * Cles des blocs de texte narratif du "Plan Strategique de SENICO" : les parties
 * du sommaire client qui ne proviennent d'aucune section du canevas de diagnostic.
 * L'ordre de l'enum pilote l'ordre d'affichage dans l'UI admin d'edition.
 *
 * <p>VISION, MISSION, VALEURS et AXES_CONSOLIDES portent le cadre strategique de l'entreprise,
 * arbitre par la Direction Generale : chaque direction propose le sien dans son canevas (S07B,
 * S08), mais un PSD publie n'en affiche qu'un. AXES_CONSOLIDES est un contenu structure (JSON,
 * cf. export.PsdConsolidatedAxes), edite par un ecran dedie plutot qu'en texte libre.</p>
 */
public enum NarrativeBlockKey {

    MOT_DU_DG("Mot du DG"),
    PREAMBULE("Préambule"),
    INTRODUCTION("Introduction"),
    APPROCHE_METHODOLOGIQUE("Approche méthodologique"),
    MISSIONS("Rappel des missions"),
    ORGANISATION("Organisation"),
    RESSOURCES("Ressources"),
    /** SENICO n'a pas encore eu de plan strategique : le bilan porte sur les performances passees. */
    BILAN_PSD_PRECEDENT("Bilan des performances des années précédentes"),
    DEFIS_A_RELEVER("Défis à relever"),
    ENJEUX("Enjeux"),
    FACTEURS_CLES("Facteurs clés de réussite et d'échec"),
    VISION("Vision de SENICO"),
    MISSION("Mission de SENICO"),
    VALEURS("Valeurs de SENICO"),
    AXES_CONSOLIDES("Axes stratégiques de SENICO"),
    DISPOSITIF_PILOTAGE("Dispositif de pilotage et de suivi-évaluation"),
    CONCLUSION("Conclusion");

    private final String label;

    NarrativeBlockKey(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
