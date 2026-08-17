package com.senico.diagnostic.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.SectionType;
import com.senico.diagnostic.exception.InvalidSectionContentException;
import org.springframework.stereotype.Component;

/**
 * Validation structurelle du JSON de chaque section, par type.
 * En mode brouillon (strict=false) : on exige seulement la presence des cles racine
 * attendues, avec le bon type JSON (objet/tableau), pour permettre la saisie progressive.
 * En mode soumission (strict=true) : on exige en plus qu'il y ait au moins une ligne de
 * contenu la ou une saisie est attendue.
 */
@Component
public class SectionContentValidator {

    public void validate(SectionType type, JsonNode content, boolean strict) {
        if (content == null || !content.isObject()) {
            throw new InvalidSectionContentException("Le contenu de la section doit etre un objet JSON");
        }

        switch (type) {
            case STAKEHOLDERS -> requireArray(content, "rows", strict);
            case RESOURCES_MATRIX -> requireArray(content, "rows", strict);
            case PESTEL -> requireArray(content, "rows", strict);
            case SWOT -> {
                requireArray(content, "strengths", false);
                requireArray(content, "weaknesses", false);
                requireArray(content, "opportunities", false);
                requireArray(content, "threats", false);
                if (strict) {
                    boolean anyFilled = content.get("strengths").size() > 0
                            || content.get("weaknesses").size() > 0
                            || content.get("opportunities").size() > 0
                            || content.get("threats").size() > 0;
                    if (!anyFilled) {
                        throw new InvalidSectionContentException(
                                "La matrice SWOT doit contenir au moins un element avant soumission");
                    }
                }
            }
            case TOWS_MATRIX -> {
                requireField(content, "maximizeStrengths");
                requireField(content, "minimizeWeaknesses");
                requireField(content, "maximizeOpportunities");
                requireField(content, "minimizeThreats");
            }
            case CAUSAL_ANALYSIS -> requireArray(content, "rows", strict);
            case INVENTORY -> requireField(content, "synthesisNote");
            case STRATEGIC_AXES -> {
                requireArray(content, "axes", strict);
                if (content.get("axes").size() > 4) {
                    throw new InvalidSectionContentException("Le canevas prevoit au maximum 4 axes strategiques");
                }
            }
            case LOGICAL_FRAMEWORK, ACTION_PLAN, BUDGET, PERFORMANCE_FRAMEWORK -> requireArray(content, "axes", strict);
            case INDICATOR_SHEET -> requireArray(content, "rows", strict);
            case RISK_MATRIX -> requireArray(content, "rows", strict);
            case FINANCING_PLAN -> requireArray(content, "rows", strict);
            case BUSINESS_PLAN -> {
                requireField(content, "operatingAccount");
                requireField(content, "cashFlow");
            }
            case STRATEGIC_SUMMARY -> {
                requireField(content, "vision");
                requireArray(content, "axes", strict);
            }
        }
    }

    private void requireField(JsonNode content, String field) {
        if (!content.has(field)) {
            throw new InvalidSectionContentException("Le champ '" + field + "' est requis dans le contenu de la section");
        }
    }

    private void requireArray(JsonNode content, String field, boolean strictNonEmpty) {
        JsonNode node = content.get(field);
        if (node == null || !node.isArray()) {
            throw new InvalidSectionContentException("Le champ '" + field + "' doit etre un tableau JSON");
        }
        if (strictNonEmpty && node.isEmpty()) {
            throw new InvalidSectionContentException(
                    "La section doit contenir au moins une ligne dans '" + field + "' avant soumission");
        }
    }
}
