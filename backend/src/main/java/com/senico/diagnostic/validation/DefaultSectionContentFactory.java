package com.senico.diagnostic.validation;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionType;
import org.springframework.stereotype.Component;

/**
 * Construit le contenu JSON par defaut (squelette) d'une section neuve, avec les
 * lignes fixes predefinies par le canevas (ressources, PESTEL, causale, axes, financement...).
 */
@Component
public class DefaultSectionContentFactory {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /**
     * Lignes de la matrice des ressources et competences (S02), dans l'ordre de la "MATRICE
     * D'ANALYSE DE RESSOURCES ET DE COMPETENCES" transmise par le client, qui y ajoute les produits
     * et services, la clientele et la recherche et developpement. "Autres" ferme la matrice.
     */
    public static final String[] RESOURCE_KEYS = {
            "CADRE_JURIDIQUE_INSTITUTIONNEL", "LEADERSHIP_PILOTAGE_GOUVERNANCE", "CAPACITES_INSTITUTIONNELLES",
            "BUDGET_RESSOURCES_FINANCIERES", "COMPETENCES", "POSITION_CONCURRENTIELLE",
            "COMPTABILITE_GESTION_FINANCIERE", "SYSTEME_INFORMATION_GESTION", "SUIVI_EVALUATION", "COMMUNICATION",
            "SYSTEME_CONTROLE", "PRODUITS_SERVICES", "CLIENTELE_BENEFICIAIRES", "RECHERCHE_DEVELOPPEMENT",
            "AUTRES_ACHATS_EXPLOITATION_TECHNIQUE_RH"
    };

    public static final String[] PESTEL_AXES = {
            "POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"
    };

    public static final String[] CAUSAL_SOURCES = {
            "MANIFESTATION", "CAUSES_IMMEDIATES", "CAUSES_SOUS_JACENTES", "CAUSES_PROFONDES", "SOLUTIONS"
    };

    public static final String[] LOGFRAME_LEVELS = {
            "IMPACT", "EFFET", "EFFETS_IMMEDIATS", "EXTRANTS", "RESSOURCES_INTRANTS"
    };

    public static final String[] FINANCING_SOURCES = {
            "RESSOURCES_PROPRES", "SUBVENTIONS_PUBLIQUES", "PARTENAIRES_TECHNIQUES_FINANCIERS", "EMPRUNTS", "AUTRES_SOURCES"
    };

    /**
     * Lignes du plan d'evolution des effectifs (S14B), issues du modele client
     * "PLAN D'EVOLUTION DES EFFECTIFS (STATUT, HIERARCHIE, GENRE)" : "Journalier" ferme le bloc
     * hierarchie, "Fonctionnaire" — retiree en premiere revue, redemandee par le client avec son
     * modele de tableau — ouvre le bloc statut, "Expatrie" le ferme.
     */
    public static final String[][] STAFF_ROWS = {
            {"HIERARCHIE", "CADRE"},
            {"HIERARCHIE", "AGENTS_MAITRISE"},
            {"HIERARCHIE", "EMPLOYE"},
            {"HIERARCHIE", "JOURNALIER"},
            {"STATUT", "FONCTIONNAIRE"},
            {"STATUT", "CDI"},
            {"STATUT", "CDD"},
            {"STATUT", "EXPATRIE"},
    };

    /**
     * Domaines d'activites pre-remplis de la synthese des enjeux et contraintes (S06B),
     * repris du "TABLEAU 3" transmis par le client. Champ libre : chaque direction
     * renomme, ajoute ou supprime les lignes selon son perimetre.
     */
    public static final String[] CONSTRAINT_DOMAINS = {
            "Transport de passagers", "Transport de fret", "Activites offshore",
            "Manutention", "Agence maritime"
    };

    public static final int[] YEARS = {2027, 2028, 2029, 2030, 2031};
    public static final String[] AXIS_CODES = {"AXE1", "AXE2", "AXE3", "AXE4"};

    public ObjectNode buildDefault(SectionType type) {
        return switch (type) {
            case STAKEHOLDERS, INDICATOR_SHEET, RISK_MATRIX, PERFORMANCE_REVIEW_2026 -> objectWithEmptyArray("rows");
            case RESOURCES_MATRIX -> resourcesMatrix();
            case PESTEL -> pestel();
            case SWOT -> swot();
            case TOWS_MATRIX -> towsMatrix();
            case CAUSAL_ANALYSIS -> causalAnalysis();
            case INVENTORY -> {
                ObjectNode n = F.objectNode();
                n.put("synthesisNote", "");
                yield n;
            }
            case RESOURCES_SYNTHESIS -> resourcesSynthesis();
            case CONSTRAINTS_SYNTHESIS -> constraintsSynthesis();
            case LOGFRAME_SYNTHESIS -> logframeSynthesis();
            case STAFF_EVOLUTION -> staffEvolution();
            case STRATEGIC_FRAMEWORK -> strategicFramework();
            case STRATEGIC_AXES -> strategicAxes();
            case LOGICAL_FRAMEWORK -> logicalFramework();
            case ACTION_PLAN -> actionPlanOrBudget(false);
            case BUDGET -> actionPlanOrBudget(true);
            case PERFORMANCE_FRAMEWORK -> performanceFramework();
            case FINANCING_PLAN -> financingPlan();
            case STRATEGIC_SUMMARY -> strategicSummary();
        };
    }

    private ObjectNode objectWithEmptyArray(String field) {
        ObjectNode n = F.objectNode();
        n.set(field, F.arrayNode());
        return n;
    }

    private ObjectNode resourcesMatrix() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String key : RESOURCE_KEYS) {
            ObjectNode row = F.objectNode();
            row.put("resourceKey", key);
            row.put("strengths", "");
            row.put("weaknesses", "");
            row.put("challenges", "");
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

    private ObjectNode pestel() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String axis : PESTEL_AXES) {
            ObjectNode row = F.objectNode();
            row.put("axis", axis);
            row.put("threats", "");
            row.put("opportunities", "");
            row.put("actions", "");
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

    private ObjectNode swot() {
        ObjectNode n = F.objectNode();
        n.set("strengths", F.arrayNode());
        n.set("weaknesses", F.arrayNode());
        n.set("opportunities", F.arrayNode());
        n.set("threats", F.arrayNode());
        return n;
    }

    private ObjectNode towsMatrix() {
        ObjectNode n = F.objectNode();
        for (String field : new String[]{
                "maximizeStrengths", "minimizeWeaknesses", "strengthsControlWeaknesses",
                "maximizeOpportunities", "strengthsForOpportunities", "correctWeaknessesViaOpportunities",
                "minimizeThreats", "strengthsReduceThreats", "minimizeWeaknessesAndThreats",
                "opportunitiesMinimizeThreats"}) {
            n.put(field, "");
        }
        return n;
    }

    private ObjectNode causalAnalysis() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String source : CAUSAL_SOURCES) {
            ObjectNode row = F.objectNode();
            row.put("source", source);
            row.set("items", F.arrayNode());
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

    private ObjectNode strategicSummary() {
        ObjectNode n = F.objectNode();
        n.put("vision", "");
        ArrayNode axes = F.arrayNode();
        for (String code : AXIS_CODES) {
            ObjectNode axis = F.objectNode();
            axis.put("axisCode", code);
            axis.set("orientations", F.arrayNode());
            axes.add(axis);
        }
        n.set("axes", axes);
        return n;
    }

    /** S03B : la reprise des lignes de la matrice S02 est injectee a la lecture par DerivedFieldsService. */
    private ObjectNode resourcesSynthesis() {
        ObjectNode n = F.objectNode();
        n.put("synthesisNote", "");
        n.set("majorStrengths", F.arrayNode());
        n.set("majorWeaknesses", F.arrayNode());
        n.set("priorityChallenges", F.arrayNode());
        return n;
    }

    private ObjectNode constraintsSynthesis() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String domain : CONSTRAINT_DOMAINS) {
            ObjectNode row = F.objectNode();
            row.put("domain", domain);
            row.set("constraints", F.arrayNode());
            row.set("challenges", F.arrayNode());
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

    /** S09B : les axes sont reconstruits a la lecture depuis S09 par DerivedFieldsService. */
    private ObjectNode logframeSynthesis() {
        ObjectNode n = F.objectNode();
        n.put("synthesisNote", "");
        return n;
    }

    private ObjectNode staffEvolution() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String[] staffRow : STAFF_ROWS) {
            ObjectNode row = F.objectNode();
            row.put("category", staffRow[0]);
            row.put("staffKey", staffRow[1]);
            row.put("label", "");
            ObjectNode years = F.objectNode();
            for (int y : YEARS) {
                ObjectNode cell = F.objectNode();
                cell.put("male", 0);
                cell.put("female", 0);
                years.set(String.valueOf(y), cell);
            }
            row.set("years", years);
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

    private ObjectNode strategicFramework() {
        ObjectNode n = F.objectNode();
        n.set("mission", F.arrayNode());
        n.set("values", F.arrayNode());
        n.put("vision", "");
        return n;
    }

    private ObjectNode strategicAxes() {
        ObjectNode n = F.objectNode();
        ArrayNode axes = F.arrayNode();
        for (String code : AXIS_CODES) {
            ObjectNode axis = F.objectNode();
            axis.put("axisCode", code);
            axis.put("title", "");
            axis.put("objective", "");
            axis.set("specificObjectives", F.arrayNode());
            axes.add(axis);
        }
        n.set("axes", axes);
        return n;
    }

    private ObjectNode logicalFramework() {
        ObjectNode n = F.objectNode();
        ArrayNode axes = F.arrayNode();
        for (String code : AXIS_CODES) {
            ObjectNode axis = F.objectNode();
            axis.put("axisCode", code);
            axis.put("objective", "");
            ArrayNode rows = F.arrayNode();
            for (String level : LOGFRAME_LEVELS) {
                ObjectNode row = F.objectNode();
                row.put("level", level);
                row.put("interventionLogic", "");
                row.put("iov", "");
                row.put("verificationMeans", "");
                row.put("assumptions", "");
                rows.add(row);
            }
            axis.set("rows", rows);
            axes.add(axis);
        }
        n.set("axes", axes);
        return n;
    }

    private ObjectNode actionPlanOrBudget(boolean withAmounts) {
        ObjectNode n = F.objectNode();
        ArrayNode axes = F.arrayNode();
        for (String axisCode : AXIS_CODES) {
            ObjectNode axis = F.objectNode();
            axis.put("axisCode", axisCode);
            ArrayNode effects = F.arrayNode();
            for (int i = 1; i <= 4; i++) {
                ObjectNode effect = F.objectNode();
                effect.put("effectCode", "EFFET" + i);
                effect.put("osCode", "OS" + i);
                effect.put("effectLabel", "");
                effect.set("rows", F.arrayNode());
                effects.add(effect);
            }
            axis.set("effects", effects);
            axes.add(axis);
        }
        n.set("axes", axes);
        n.put("withAmounts", withAmounts);
        return n;
    }

    private ObjectNode performanceFramework() {
        ObjectNode n = F.objectNode();
        ArrayNode axes = F.arrayNode();
        for (String axisCode : AXIS_CODES) {
            ObjectNode axis = F.objectNode();
            axis.put("axisCode", axisCode);
            ArrayNode groups = F.arrayNode();
            for (String level : LOGFRAME_LEVELS) {
                ObjectNode group = F.objectNode();
                group.put("level", level);
                group.set("rows", F.arrayNode());
                groups.add(group);
            }
            axis.set("groups", groups);
            axes.add(axis);
        }
        n.set("axes", axes);
        return n;
    }

    private ObjectNode financingPlan() {
        ObjectNode n = F.objectNode();
        ArrayNode rows = F.arrayNode();
        for (String source : FINANCING_SOURCES) {
            ObjectNode row = F.objectNode();
            row.put("source", source);
            row.put("amount", 0);
            row.put("modalities", "");
            row.put("period", "");
            row.put("responsible", "");
            rows.add(row);
        }
        n.set("rows", rows);
        return n;
    }

}
