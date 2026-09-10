package com.senico.diagnostic.domain;

/**
 * Les 22 types de formulaires du canevas. Determine la structure JSON attendue
 * dans SectionResponse.contentJson (voir validation/SectionContentValidator).
 */
public enum SectionType {
    STAKEHOLDERS,           // S01
    PERFORMANCE_REVIEW_2026, // S01B - Analyse des performances de l'annee 2026, avant l'analyse des ressources
    RESOURCES_MATRIX,       // S02
    PESTEL,                 // S03
    RESOURCES_SYNTHESIS,     // S03B - Synthese de l'analyse des ressources, avant le SWOT
    SWOT,                   // S04
    TOWS_MATRIX,             // S05
    CAUSAL_ANALYSIS,        // S06
    CONSTRAINTS_SYNTHESIS,   // S06B - Synthese des enjeux, contraintes et defis prioritaires
    INVENTORY,               // S07
    STRATEGIC_FRAMEWORK,     // S07B - Cadre strategique (Mission, Valeurs, Vision), avant les axes
    STRATEGIC_AXES,          // S08
    LOGICAL_FRAMEWORK,       // S09
    LOGFRAME_SYNTHESIS,      // S09B - Synthese du cadre logique
    ACTION_PLAN,             // S10
    BUDGET,                  // S11
    PERFORMANCE_FRAMEWORK,   // S12
    INDICATOR_SHEET,         // S13
    RISK_MATRIX,             // S14
    STAFF_EVOLUTION,         // S14B - Plan d'evolution des effectifs (statut, hierarchie, genre)
    FINANCING_PLAN,          // S15
    STRATEGIC_SUMMARY        // S17
}
