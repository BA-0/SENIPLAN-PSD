package com.senico.diagnostic.export;

import com.senico.diagnostic.validation.DefaultSectionContentFactory;

import java.util.Map;
import java.util.Set;

/**
 * Libelles francais affiches dans les exports, portage cote serveur des constantes
 * definies cote client dans frontend/src/types/sections.ts. Reutilise les tableaux
 * de cles deja presents dans DefaultSectionContentFactory plutot que de les redefinir.
 */
final class SectionLabels {

    private SectionLabels() {
    }

    static final Map<String, String> RESOURCE_LABELS = Map.ofEntries(
            Map.entry("CADRE_JURIDIQUE_INSTITUTIONNEL", "Cadre juridique, institutionnel et organisationnel"),
            Map.entry("LEADERSHIP_PILOTAGE_GOUVERNANCE", "Leadership, Pilotage, Management et Gouvernance"),
            Map.entry("POSITION_CONCURRENTIELLE", "Position concurrentielle"),
            Map.entry("CAPACITES_INSTITUTIONNELLES", "Capacités institutionnelles (ressources matérielles, financières, humaines et immatérielles)"),
            Map.entry("BUDGET_RESSOURCES_FINANCIERES", "Budget ou ressources financières"),
            Map.entry("COMPTABILITE_GESTION_FINANCIERE", "Comptabilité et gestion financière"),
            Map.entry("SYSTEME_CONTROLE", "Système de contrôle"),
            Map.entry("SYSTEME_INFORMATION_GESTION", "Système d'information et de gestion"),
            Map.entry("SUIVI_EVALUATION", "Suivi évaluation"),
            Map.entry("COMMUNICATION", "Communication"),
            Map.entry("AUTRES_ACHATS_EXPLOITATION_TECHNIQUE_RH", "Autres (Achats, Exploitation commerciale, Technique et armement, RH)"),
            Map.entry("COMPETENCES", "Compétences")
    );

    static final Map<String, String> PESTEL_LABELS = Map.of(
            "POLITIQUE", "Politique",
            "ECONOMIQUE", "Économique",
            "SOCIAL_CULTUREL", "Social et culturel",
            "TECHNOLOGIQUE", "Technologique",
            "ENVIRONNEMENTAL", "Environnemental",
            "LEGAL", "Légal"
    );

    static final Map<String, String> CAUSAL_LABELS = Map.of(
            "MANIFESTATION", "Manifestation des problèmes (effet négatif, besoins)",
            "CAUSES_IMMEDIATES", "Causes immédiates",
            "CAUSES_SOUS_JACENTES", "Causes sous-jacentes",
            "CAUSES_PROFONDES", "Causes profondes",
            "SOLUTIONS", "Solutions"
    );

    static final Map<String, String> LOGFRAME_LABELS = Map.of(
            "IMPACT", "Impact (Finalité)",
            "EFFET", "Effet (Objectif spécifique)",
            "EFFETS_IMMEDIATS", "Effets immédiats (Résultats immédiats)",
            "EXTRANTS", "Extrants (Produits / Activités)",
            "RESSOURCES_INTRANTS", "Ressources / Intrants (Moyens)"
    );

    static final Map<String, String> FINANCING_LABELS = Map.of(
            "RESSOURCES_PROPRES", "Ressources propres",
            "SUBVENTIONS_PUBLIQUES", "Subventions publiques",
            "PARTENAIRES_TECHNIQUES_FINANCIERS", "Partenaires techniques et financiers (PTF)",
            "EMPRUNTS", "Emprunts",
            "AUTRES_SOURCES", "Autres sources"
    );

    static final Map<String, String> OPERATING_ACCOUNT_LABELS = Map.of(
            "PRODUITS_EXPLOITATION", "Produits d'exploitation / Chiffre d'affaires",
            "CHARGES_EXPLOITATION", "Charges d'exploitation",
            "RESULTAT_EXPLOITATION", "Résultat d'exploitation",
            "CHARGES_FINANCIERES", "Charges financières",
            "RESULTAT_NET", "Résultat net"
    );

    static final Map<String, String> CASH_FLOW_LABELS = Map.of(
            "FLUX_EXPLOITATION", "Flux d'exploitation",
            "FLUX_INVESTISSEMENT", "Flux d'investissement",
            "FLUX_FINANCEMENT", "Flux de financement",
            "VARIATION_NETTE_TRESORERIE", "Variation nette de trésorerie",
            "TRESORERIE_FIN_PERIODE", "Trésorerie de fin de période"
    );

    static final Set<String> COMPUTED_ROW_LABELS = Set.of(
            "RESULTAT_EXPLOITATION", "RESULTAT_NET", "VARIATION_NETTE_TRESORERIE", "TRESORERIE_FIN_PERIODE"
    );

    static final String[] YEARS = toStrings(DefaultSectionContentFactory.YEARS);

    static String resource(String key) {
        return RESOURCE_LABELS.getOrDefault(key, key);
    }

    static String pestel(String key) {
        return PESTEL_LABELS.getOrDefault(key, key);
    }

    static String causal(String key) {
        return CAUSAL_LABELS.getOrDefault(key, key);
    }

    static String logframe(String key) {
        return LOGFRAME_LABELS.getOrDefault(key, key);
    }

    static String financing(String key) {
        return FINANCING_LABELS.getOrDefault(key, key);
    }

    static String operatingAccount(String key) {
        return OPERATING_ACCOUNT_LABELS.getOrDefault(key, key);
    }

    static String cashFlow(String key) {
        return CASH_FLOW_LABELS.getOrDefault(key, key);
    }

    static String criticality(String label) {
        if (label == null) return "—";
        return switch (label) {
            case "ELEVEE" -> "Élevée";
            case "MOYENNE" -> "Moyenne";
            case "FAIBLE" -> "Faible";
            default -> label;
        };
    }

    static ExportBlock.Background criticalityBackground(String label) {
        if (label == null) return ExportBlock.Background.NONE;
        return switch (label) {
            case "ELEVEE" -> ExportBlock.Background.RED;
            case "MOYENNE" -> ExportBlock.Background.ORANGE;
            case "FAIBLE" -> ExportBlock.Background.GREEN;
            default -> ExportBlock.Background.NONE;
        };
    }

    private static String[] toStrings(int[] values) {
        String[] out = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            out[i] = String.valueOf(values[i]);
        }
        return out;
    }
}
