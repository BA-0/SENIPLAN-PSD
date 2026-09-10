package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.validation.DefaultSectionContentFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Calcule les champs derives (totaux, criticite, pourcentages, synchronisations inter-sections)
 * a la LECTURE, jamais persistes tels quels : la source de verite reste la saisie brute.
 * Sections concernees : S05 (sync S04), S07 (agregation S01/S03/S04/S06), S08->S09-S12/S17
 * (intitules d'axes), S11 (totaux budget), S14 (criticite N x Q), S15 (pourcentages),
 * S16 (resultats/tresorerie auto), S01B (taux de realisation 2026), S03B (reprise S02),
 * S09B (reprise S09), S14B (totaux effectifs par annee).
 */
@Service
@RequiredArgsConstructor
public class DerivedFieldsService {

    private static final JsonNodeFactory F = JsonNodeFactory.instance;
    private static final int[] YEARS = DefaultSectionContentFactory.YEARS;

    // IDs fixes du referentiel (V2__seed_data.sql)
    private static final int SECTION_STAKEHOLDERS_ID = 1;
    private static final int SECTION_PESTEL_ID = 3;
    private static final int SECTION_SWOT_ID = 4;
    private static final int SECTION_TOWS_ID = 5;
    private static final int SECTION_CAUSAL_ID = 6;
    private static final String[] TOWS_ACTION_FIELDS = {
            "maximizeStrengths", "minimizeWeaknesses", "strengthsControlWeaknesses",
            "maximizeOpportunities", "strengthsForOpportunities", "correctWeaknessesViaOpportunities",
            "minimizeThreats", "strengthsReduceThreats", "minimizeWeaknessesAndThreats",
            "opportunitiesMinimizeThreats"
    };
    private static final int SECTION_AXES_ID = 8;
    private static final int SECTION_ACTION_PLAN_ID = 10;
    private static final int SECTION_RESOURCES_MATRIX_ID = 2;
    private static final int SECTION_LOGICAL_FRAMEWORK_ID = 9;
    private static final String EFFETS_IMMEDIATS_LEVEL = "EFFETS_IMMEDIATS";

    private final SectionResponseRepository sectionResponseRepository;
    private final ObjectMapper objectMapper;

    public ObjectNode apply(SectionType type, Long groupId, ObjectNode content) {
        return switch (type) {
            case TOWS_MATRIX -> applyTowsSync(groupId, content);
            case CAUSAL_ANALYSIS -> applyCausalSync(groupId, content);
            case INVENTORY -> applyInventoryAggregation(groupId, content);
            case STAKEHOLDERS -> normalizeStakeholders(content);
            case STRATEGIC_AXES -> normalizeStrategicAxes(content);
            case LOGICAL_FRAMEWORK, STRATEGIC_SUMMARY, ACTION_PLAN -> applyAxisTitleSync(groupId, content);
            case PERFORMANCE_FRAMEWORK -> applyEffectsSync(groupId, applyAxisTitleSync(groupId, content));
            case BUDGET -> applyBudgetTotals(applyAxisTitleSync(groupId, content));
            case RISK_MATRIX -> applyRiskCriticality(content);
            case FINANCING_PLAN -> applyFinancingTotals(content);
            case PERFORMANCE_REVIEW_2026 -> applyPerformanceReviewRates(content);
            case RESOURCES_SYNTHESIS -> applyResourcesSynthesisSync(groupId, content);
            case LOGFRAME_SYNTHESIS -> applyLogframeSynthesisSync(groupId, content);
            case STAFF_EVOLUTION -> applyStaffTotals(content);
            default -> content;
        };
    }

    // ---- S05 : matrice de confrontation, listes SWOT synchronisees depuis S04 ----
    private ObjectNode applyTowsSync(Long groupId, ObjectNode content) {
        Optional<SectionResponse> swotResponse = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, SECTION_SWOT_ID);

        if (swotResponse.isPresent()) {
            JsonNode parsed = readTree(swotResponse.get());
            content.set("strengths", arrayOrEmpty(parsed, "strengths"));
            content.set("weaknesses", arrayOrEmpty(parsed, "weaknesses"));
            content.set("opportunities", arrayOrEmpty(parsed, "opportunities"));
            content.set("threats", arrayOrEmpty(parsed, "threats"));
        } else {
            content.set("strengths", F.arrayNode());
            content.set("weaknesses", F.arrayNode());
            content.set("opportunities", F.arrayNode());
            content.set("threats", F.arrayNode());
        }
        return content;
    }

    // ---- S06 : analyse causale, actions (matrice TOWS) synchronisees en lecture depuis S05 ----
    private ObjectNode applyCausalSync(Long groupId, ObjectNode content) {
        Optional<SectionResponse> towsResponse = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, SECTION_TOWS_ID);

        ObjectNode syncedActions = F.objectNode();
        if (towsResponse.isPresent()) {
            JsonNode parsed = readTree(towsResponse.get());
            for (String field : TOWS_ACTION_FIELDS) {
                syncedActions.put(field, parsed.path(field).asText(""));
            }
        } else {
            for (String field : TOWS_ACTION_FIELDS) {
                syncedActions.put(field, "");
            }
        }
        content.set("syncedTowsActions", syncedActions);
        return content;
    }

    // ---- S01 : compat ascendante - anciennes reponses avec un champ "actor" libre, sans categorie/portee ----
    private ObjectNode normalizeStakeholders(ObjectNode content) {
        JsonNode rows = content.get("rows");
        if (rows == null || !rows.isArray()) {
            return content;
        }
        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row)) {
                continue;
            }
            if (!row.has("category") || row.path("category").asText("").isBlank()) {
                row.put("category", "AUTRE");
            }
            if (!row.has("scope")) {
                row.put("scope", "");
            }
        }
        return content;
    }

    // ---- S08 : compat ascendante - anciennes reponses {title, description} -> {title, objective, specificObjectives} ----
    private ObjectNode normalizeStrategicAxes(ObjectNode content) {
        JsonNode axes = content.get("axes");
        if (axes == null || !axes.isArray()) {
            return content;
        }
        for (JsonNode axisNode : axes) {
            if (!(axisNode instanceof ObjectNode axis)) {
                continue;
            }
            if (!axis.has("objective")) {
                axis.put("objective", "");
            }
            if (!axis.has("specificObjectives") || !axis.get("specificObjectives").isArray()) {
                ArrayNode specificObjectives = F.arrayNode();
                String legacyDescription = axis.path("description").asText("");
                if (!legacyDescription.isBlank()) {
                    specificObjectives.add(legacyDescription);
                }
                axis.set("specificObjectives", specificObjectives);
            }
        }
        return content;
    }

    // ---- S07 : inventaire, agregation en lecture depuis S01/S03/S04/S06 ----
    private ObjectNode applyInventoryAggregation(Long groupId, ObjectNode content) {
        sectionResponseRepository.findByGroupIdAndSectionId(groupId, SECTION_STAKEHOLDERS_ID)
                .ifPresentOrElse(r -> content.set("stakeholders", arrayOrEmpty(readTree(r), "rows")),
                        () -> content.set("stakeholders", F.arrayNode()));

        sectionResponseRepository.findByGroupIdAndSectionId(groupId, SECTION_PESTEL_ID)
                .ifPresentOrElse(r -> content.set("pestel", arrayOrEmpty(readTree(r), "rows")),
                        () -> content.set("pestel", F.arrayNode()));

        sectionResponseRepository.findByGroupIdAndSectionId(groupId, SECTION_SWOT_ID)
                .ifPresentOrElse(r -> {
                    JsonNode swot = readTree(r);
                    ObjectNode swotNode = F.objectNode();
                    swotNode.set("strengths", arrayOrEmpty(swot, "strengths"));
                    swotNode.set("weaknesses", arrayOrEmpty(swot, "weaknesses"));
                    swotNode.set("opportunities", arrayOrEmpty(swot, "opportunities"));
                    swotNode.set("threats", arrayOrEmpty(swot, "threats"));
                    content.set("swot", swotNode);
                }, () -> {
                    ObjectNode emptySwot = F.objectNode();
                    emptySwot.set("strengths", F.arrayNode());
                    emptySwot.set("weaknesses", F.arrayNode());
                    emptySwot.set("opportunities", F.arrayNode());
                    emptySwot.set("threats", F.arrayNode());
                    content.set("swot", emptySwot);
                });

        sectionResponseRepository.findByGroupIdAndSectionId(groupId, SECTION_CAUSAL_ID)
                .ifPresentOrElse(r -> content.set("causalAnalysis", arrayOrEmpty(readTree(r), "rows")),
                        () -> content.set("causalAnalysis", F.arrayNode()));

        return content;
    }

    // ---- S09/S11/S12/S17 : intitules des 4 axes synchronises depuis S08 ----
    private ObjectNode applyAxisTitleSync(Long groupId, ObjectNode content) {
        Optional<SectionResponse> axesResponse = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, SECTION_AXES_ID);
        if (axesResponse.isEmpty() || !content.has("axes") || !content.get("axes").isArray()) {
            return content;
        }
        JsonNode axesSource = readTree(axesResponse.get()).get("axes");
        if (axesSource == null || !axesSource.isArray()) {
            return content;
        }

        for (JsonNode axisNode : content.get("axes")) {
            if (!(axisNode instanceof ObjectNode axis)) {
                continue;
            }
            String axisCode = axis.path("axisCode").asText(null);
            for (JsonNode src : axesSource) {
                if (src.path("axisCode").asText("").equals(axisCode)) {
                    axis.put("axisTitle", src.path("title").asText(""));
                    break;
                }
            }
        }
        return content;
    }

    // ---- S12 : groupe "Effets immediats" synchronise avec les effets (OS) definis en S10 ----
    private ObjectNode applyEffectsSync(Long groupId, ObjectNode content) {
        Optional<SectionResponse> actionPlanResponse = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, SECTION_ACTION_PLAN_ID);
        if (actionPlanResponse.isEmpty() || !content.has("axes") || !content.get("axes").isArray()) {
            return content;
        }
        JsonNode sourceAxes = readTree(actionPlanResponse.get()).get("axes");
        if (sourceAxes == null || !sourceAxes.isArray()) {
            return content;
        }

        for (JsonNode axisNode : content.get("axes")) {
            if (!(axisNode instanceof ObjectNode axis) || !axis.has("groups") || !axis.get("groups").isArray()) {
                continue;
            }
            String axisCode = axis.path("axisCode").asText(null);

            JsonNode matchingSourceAxis = null;
            for (JsonNode src : sourceAxes) {
                if (src.path("axisCode").asText("").equals(axisCode)) {
                    matchingSourceAxis = src;
                    break;
                }
            }

            ArrayNode syncedEffects = F.arrayNode();
            if (matchingSourceAxis != null && matchingSourceAxis.get("effects") != null) {
                for (JsonNode effect : matchingSourceAxis.get("effects")) {
                    ObjectNode entry = F.objectNode();
                    entry.put("osCode", effect.path("osCode").asText(""));
                    entry.put("effectLabel", effect.path("effectLabel").asText(""));
                    syncedEffects.add(entry);
                }
            }

            for (JsonNode groupNode : axis.get("groups")) {
                if (groupNode instanceof ObjectNode group
                        && EFFETS_IMMEDIATS_LEVEL.equals(group.path("level").asText(""))) {
                    group.set("syncedEffects", syncedEffects);
                }
            }
        }
        return content;
    }

    // ---- S11 : budget - totaux ligne + colonne (par effet) + total axe + total general ----
    private ObjectNode applyBudgetTotals(ObjectNode content) {
        JsonNode axes = content.get("axes");
        if (axes == null || !axes.isArray()) {
            return content;
        }

        double grandTotal = 0;
        for (JsonNode axisNode : axes) {
            if (!(axisNode instanceof ObjectNode axis)) {
                continue;
            }
            JsonNode effects = axis.get("effects");
            double axisTotal = 0;
            if (effects != null && effects.isArray()) {
                for (JsonNode effectNode : effects) {
                    if (!(effectNode instanceof ObjectNode effect)) {
                        continue;
                    }
                    ObjectNode yearTotals = F.objectNode();
                    for (int year : YEARS) {
                        yearTotals.put(String.valueOf(year), 0);
                    }
                    double effectTotal = 0;

                    JsonNode rows = effect.get("rows");
                    if (rows != null && rows.isArray()) {
                        for (JsonNode rowNode : rows) {
                            if (!(rowNode instanceof ObjectNode row)) {
                                continue;
                            }
                            double rowTotal = 0;
                            for (int year : YEARS) {
                                String y = String.valueOf(year);
                                double amount = yearValue(row, y);
                                rowTotal += amount;
                                yearTotals.put(y, yearTotals.path(y).asDouble(0) + amount);
                            }
                            row.put("rowTotal", rowTotal);
                            effectTotal += rowTotal;
                        }
                    }

                    effect.set("yearTotals", yearTotals);
                    effect.put("effectTotal", effectTotal);
                    axisTotal += effectTotal;
                }
            }
            axis.put("axisTotal", axisTotal);
            grandTotal += axisTotal;
        }
        content.put("grandTotal", grandTotal);
        return content;
    }

    // ---- S14 : criticite = niveau de risque N x quotation Q ----
    private ObjectNode applyRiskCriticality(ObjectNode content) {
        JsonNode rows = content.get("rows");
        if (rows == null || !rows.isArray()) {
            return content;
        }
        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row)) {
                continue;
            }
            int n = row.path("levelN").asInt(0);
            int q = row.path("quotationQ").asInt(0);
            int criticality = n * q;
            row.put("criticality", criticality);
            row.put("criticalityLabel", criticality >= 6 ? "ELEVEE" : criticality >= 3 ? "MOYENNE" : "FAIBLE");
        }
        return content;
    }

    // ---- S15 : pourcentage par ligne + total ----
    private ObjectNode applyFinancingTotals(ObjectNode content) {
        JsonNode rows = content.get("rows");
        if (rows == null || !rows.isArray()) {
            return content;
        }
        double total = 0;
        for (JsonNode row : rows) {
            total += row.path("amount").asDouble(0);
        }
        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row)) {
                continue;
            }
            double amount = row.path("amount").asDouble(0);
            double percent = total > 0 ? (amount / total) * 100.0 : 0;
            row.put("percent", Math.round(percent * 100.0) / 100.0);
        }
        content.put("total", total);
        return content;
    }

    // ---- S01B : taux de realisation 2026 = realise / cible, en pourcentage ----
    private ObjectNode applyPerformanceReviewRates(ObjectNode content) {
        JsonNode rows = content.get("rows");
        if (rows == null || !rows.isArray()) {
            return content;
        }
        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row)) {
                continue;
            }
            double target = row.path("target2026").asDouble(0);
            double achieved = row.path("achieved2026").asDouble(0);
            // Cible a zero : le taux n'a pas de sens, on laisse la case vide plutot
            // que d'afficher 0 % (qui se lirait comme un echec) ou une division infinie.
            if (target == 0) {
                row.putNull("rate");
            } else {
                row.put("rate", Math.round(achieved / target * 1000d) / 10d);
            }
        }
        return content;
    }

    // ---- S03B : rappel en lecture seule des lignes de la matrice des ressources (S02) ----
    private ObjectNode applyResourcesSynthesisSync(Long groupId, ObjectNode content) {
        sectionResponseRepository.findByGroupIdAndSectionId(groupId, SECTION_RESOURCES_MATRIX_ID)
                .ifPresentOrElse(r -> content.set("resources", arrayOrEmpty(readTree(r), "rows")),
                        () -> content.set("resources", F.arrayNode()));
        return content;
    }

    // ---- S09B : synthese en lecture seule du cadre logique (S09), un bloc par axe ----
    private ObjectNode applyLogframeSynthesisSync(Long groupId, ObjectNode content) {
        ArrayNode axes = F.arrayNode();
        Optional<SectionResponse> logframe = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, SECTION_LOGICAL_FRAMEWORK_ID);

        if (logframe.isPresent()) {
            JsonNode sourceAxes = readTree(logframe.get()).get("axes");
            if (sourceAxes != null && sourceAxes.isArray()) {
                for (JsonNode sourceAxis : sourceAxes) {
                    ObjectNode axis = F.objectNode();
                    axis.put("axisCode", sourceAxis.path("axisCode").asText(""));
                    axis.put("axisTitle", sourceAxis.path("axisTitle").asText(""));
                    axis.put("objective", sourceAxis.path("objective").asText(""));
                    // Un niveau du cadre logique peut porter plusieurs lignes : on les
                    // concatene pour n'avoir qu'une cellule par niveau dans la synthese.
                    for (String level : DefaultSectionContentFactory.LOGFRAME_LEVELS) {
                        axis.put(level, joinLevel(sourceAxis.get("rows"), level));
                    }
                    axes.add(axis);
                }
            }
        }
        content.set("axes", axes);
        // Les intitules d'axes de S09 ne sont eux-memes synchronises qu'a la lecture de S09 :
        // on refait donc ici la reprise depuis S08, sinon ils reviendraient vides.
        return applyAxisTitleSync(groupId, content);
    }

    private String joinLevel(JsonNode rows, String level) {
        if (rows == null || !rows.isArray()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (JsonNode row : rows) {
            if (!row.path("level").asText("").equals(level)) {
                continue;
            }
            String text = row.path("interventionLogic").asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            if (joined.length() > 0) {
                joined.append(System.lineSeparator());
            }
            joined.append(text);
        }
        return joined.toString();
    }

    // ---- S14B : total H+F par ligne et par annee, plus la ligne TOTAUX ----
    private ObjectNode applyStaffTotals(ObjectNode content) {
        JsonNode rows = content.get("rows");
        if (rows == null || !rows.isArray()) {
            return content;
        }

        ObjectNode totals = F.objectNode();
        for (int year : YEARS) {
            ObjectNode cell = F.objectNode();
            cell.put("male", 0);
            cell.put("female", 0);
            cell.put("total", 0);
            totals.set(String.valueOf(year), cell);
        }

        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row) || !(row.get("years") instanceof ObjectNode years)) {
                continue;
            }
            for (int year : YEARS) {
                String y = String.valueOf(year);
                if (!(years.get(y) instanceof ObjectNode cell)) {
                    continue;
                }
                int male = cell.path("male").asInt(0);
                int female = cell.path("female").asInt(0);
                cell.put("total", male + female);

                ObjectNode totalCell = (ObjectNode) totals.get(y);
                totalCell.put("male", totalCell.path("male").asInt(0) + male);
                totalCell.put("female", totalCell.path("female").asInt(0) + female);
                totalCell.put("total", totalCell.path("total").asInt(0) + male + female);
            }
        }
        content.set("totals", totals);
        return content;
    }

    private double yearValue(JsonNode row, String year) {
        JsonNode years = row.get("years");
        return years != null ? years.path(year).asDouble(0) : 0;
    }

    private ArrayNode arrayOrEmpty(JsonNode parent, String field) {
        JsonNode node = parent.get(field);
        return (node != null && node.isArray()) ? (ArrayNode) node : F.arrayNode();
    }

    private JsonNode readTree(SectionResponse response) {
        try {
            return objectMapper.readTree(response.getContentJson());
        } catch (Exception e) {
            return F.objectNode();
        }
    }
}
