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
 * S16 (resultats/tresorerie auto).
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
    private static final int SECTION_CAUSAL_ID = 6;
    private static final int SECTION_AXES_ID = 8;
    private static final int SECTION_ACTION_PLAN_ID = 10;
    private static final String EFFETS_IMMEDIATS_LEVEL = "EFFETS_IMMEDIATS";

    private final SectionResponseRepository sectionResponseRepository;
    private final ObjectMapper objectMapper;

    public ObjectNode apply(SectionType type, Long groupId, ObjectNode content) {
        return switch (type) {
            case TOWS_MATRIX -> applyTowsSync(groupId, content);
            case INVENTORY -> applyInventoryAggregation(groupId, content);
            case LOGICAL_FRAMEWORK, STRATEGIC_SUMMARY, ACTION_PLAN -> applyAxisTitleSync(groupId, content);
            case PERFORMANCE_FRAMEWORK -> applyEffectsSync(groupId, applyAxisTitleSync(groupId, content));
            case BUDGET -> applyBudgetTotals(applyAxisTitleSync(groupId, content));
            case RISK_MATRIX -> applyRiskCriticality(content);
            case FINANCING_PLAN -> applyFinancingTotals(content);
            case BUSINESS_PLAN -> applyBusinessPlanComputations(content);
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
                }, () -> content.set("swot", F.objectNode()));

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

    // ---- S16 : resultats d'exploitation/net + variation et tresorerie cumulee ----
    private ObjectNode applyBusinessPlanComputations(ObjectNode content) {
        computeOperatingAccount((ObjectNode) content.path("operatingAccount"));
        computeCashFlow((ObjectNode) content.path("cashFlow"));
        return content;
    }

    private void computeOperatingAccount(ObjectNode operatingAccount) {
        if (operatingAccount == null || operatingAccount.isMissingNode()) {
            return;
        }
        ObjectNode produits = findRow(operatingAccount, "PRODUITS_EXPLOITATION");
        ObjectNode charges = findRow(operatingAccount, "CHARGES_EXPLOITATION");
        ObjectNode resultatExploitation = findRow(operatingAccount, "RESULTAT_EXPLOITATION");
        ObjectNode chargesFinancieres = findRow(operatingAccount, "CHARGES_FINANCIERES");
        ObjectNode resultatNet = findRow(operatingAccount, "RESULTAT_NET");

        if (produits == null || charges == null || resultatExploitation == null
                || chargesFinancieres == null || resultatNet == null) {
            return;
        }

        for (int year : YEARS) {
            String y = String.valueOf(year);
            double p = yearValue(produits, y);
            double c = yearValue(charges, y);
            double re = p - c;
            double cf = yearValue(chargesFinancieres, y);
            double rn = re - cf;
            ((ObjectNode) resultatExploitation.path("years")).put(y, re);
            ((ObjectNode) resultatNet.path("years")).put(y, rn);
        }
        addRowTotals(operatingAccount);
    }

    private void computeCashFlow(ObjectNode cashFlow) {
        if (cashFlow == null || cashFlow.isMissingNode()) {
            return;
        }
        ObjectNode exploitation = findRow(cashFlow, "FLUX_EXPLOITATION");
        ObjectNode investissement = findRow(cashFlow, "FLUX_INVESTISSEMENT");
        ObjectNode financement = findRow(cashFlow, "FLUX_FINANCEMENT");
        ObjectNode variationNette = findRow(cashFlow, "VARIATION_NETTE_TRESORERIE");
        ObjectNode tresorerieFin = findRow(cashFlow, "TRESORERIE_FIN_PERIODE");

        if (exploitation == null || investissement == null || financement == null
                || variationNette == null || tresorerieFin == null) {
            return;
        }

        double cumulative = 0;
        for (int year : YEARS) {
            String y = String.valueOf(year);
            double variation = yearValue(exploitation, y) + yearValue(investissement, y) + yearValue(financement, y);
            cumulative += variation;
            ((ObjectNode) variationNette.path("years")).put(y, variation);
            ((ObjectNode) tresorerieFin.path("years")).put(y, cumulative);
        }
        addRowTotals(cashFlow);
    }

    private void addRowTotals(ObjectNode block) {
        JsonNode rows = block.get("rows");
        if (rows == null || !rows.isArray()) {
            return;
        }
        for (JsonNode rowNode : rows) {
            if (!(rowNode instanceof ObjectNode row)) {
                continue;
            }
            double total = 0;
            for (int year : YEARS) {
                total += yearValue(row, String.valueOf(year));
            }
            row.put("total", total);
        }
    }

    private ObjectNode findRow(ObjectNode block, String label) {
        JsonNode rows = block.get("rows");
        if (rows == null || !rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (row.path("label").asText("").equals(label)) {
                return (ObjectNode) row;
            }
        }
        return null;
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
