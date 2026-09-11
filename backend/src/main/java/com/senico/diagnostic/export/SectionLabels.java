package com.senico.diagnostic.export;

import com.senico.diagnostic.validation.DefaultSectionContentFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Libelles francais affiches dans les exports, portage cote serveur des constantes
 * definies cote client dans frontend/src/types/sections.ts. Reutilise les tableaux
 * de cles deja presents dans DefaultSectionContentFactory plutot que de les redefinir.
 */
final class SectionLabels {

    private SectionLabels() {
    }

    static final Map<String, String> STAKEHOLDER_CATEGORY_LABELS = Map.of(
            "MAIRIE", "Mairie",
            "BANQUE", "Banque",
            "ETAT", "État",
            "PRESTATAIRE", "Prestataire",
            "FOURNISSEUR", "Fournisseur",
            "AUTRE", "Autre"
    );

    static final Map<String, String> STAKEHOLDER_SCOPE_LABELS = Map.of(
            "INTERNE", "Interne",
            "EXTERNE", "Externe"
    );

    static final Map<String, String> TOWS_ACTION_LABELS = new LinkedHashMap<>();
    static {
        TOWS_ACTION_LABELS.put("maximizeStrengths", "Comment maximiser les forces ?");
        TOWS_ACTION_LABELS.put("minimizeWeaknesses", "Comment minimiser les faiblesses ?");
        TOWS_ACTION_LABELS.put("strengthsControlWeaknesses", "En quoi les forces permettent-elles de maîtriser les faiblesses ?");
        TOWS_ACTION_LABELS.put("maximizeOpportunities", "Comment maximiser les opportunités ?");
        TOWS_ACTION_LABELS.put("strengthsForOpportunities", "Comment utiliser les forces pour tirer parti des opportunités ?");
        TOWS_ACTION_LABELS.put("correctWeaknessesViaOpportunities", "Comment corriger les faiblesses en tirant parti des opportunités ?");
        TOWS_ACTION_LABELS.put("minimizeThreats", "Comment minimiser les menaces ?");
        TOWS_ACTION_LABELS.put("strengthsReduceThreats", "Comment utiliser les forces pour réduire les menaces ?");
        TOWS_ACTION_LABELS.put("minimizeWeaknessesAndThreats", "Comment minimiser les faiblesses et les menaces ?");
        TOWS_ACTION_LABELS.put("opportunitiesMinimizeThreats", "En quoi les opportunités permettent-elles de minimiser les menaces ?");
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
            Map.entry("COMPETENCES", "Compétences"),
            Map.entry("PRODUITS_SERVICES", "Produits et services à délivrer (portefeuille, qualité, production, marque, tarification, force de vente, compétitivité…)"),
            Map.entry("CLIENTELE_BENEFICIAIRES", "Clientèle ou bénéficiaires des prestations (taille, fidélité…)"),
            Map.entry("RECHERCHE_DEVELOPPEMENT", "Recherche et développement")
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

    /** S14B : blocs et lignes du plan d'evolution des effectifs. */
    static final Map<String, String> STAFF_CATEGORY_LABELS = Map.of(
            "HIERARCHIE", "Hiérarchie",
            "STATUT", "Statut"
    );

    static final Map<String, String> STAFF_LABELS = Map.of(
            "CADRE", "Cadre",
            "AGENTS_MAITRISE", "Agents de maîtrise",
            "EMPLOYE", "Employé",
            "JOURNALIER", "Journalier",
            "FONCTIONNAIRE", "Fonctionnaire",
            "CDI", "CDI",
            "CDD", "CDD",
            "EXPATRIE", "Expatrié"
    );

    static final String[] YEARS = toStrings(DefaultSectionContentFactory.YEARS);

    static String stakeholderCategory(String key) {
        return STAKEHOLDER_CATEGORY_LABELS.getOrDefault(key, key);
    }

    static String stakeholderScope(String key) {
        return STAKEHOLDER_SCOPE_LABELS.getOrDefault(key, key);
    }

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

    static String staffCategory(String key) {
        return STAFF_CATEGORY_LABELS.getOrDefault(key, key);
    }

    /** Une ligne ajoutee a la main par une direction n'a pas de cle : son intitule libre fait foi. */
    static String staffRow(String key, String freeLabel) {
        if (freeLabel != null && !freeLabel.isBlank()) {
            return freeLabel;
        }
        return STAFF_LABELS.getOrDefault(key, key);
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
            case "MOYENNE" -> ExportBlock.Background.BLUE;
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
