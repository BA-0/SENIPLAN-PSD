package com.senico.diagnostic.domain;

/**
 * Les 17 types de formulaires du canevas. Determine la structure JSON attendue
 * dans SectionResponse.contentJson (voir validation/SectionContentValidator).
 */
public enum SectionType {
    STAKEHOLDERS,           // S01
    RESOURCES_MATRIX,       // S02
    PESTEL,                 // S03
    SWOT,                   // S04
    TOWS_MATRIX,             // S05
    CAUSAL_ANALYSIS,        // S06
    INVENTORY,               // S07
    STRATEGIC_AXES,          // S08
    LOGICAL_FRAMEWORK,       // S09
    ACTION_PLAN,             // S10
    BUDGET,                  // S11
    PERFORMANCE_FRAMEWORK,   // S12
    INDICATOR_SHEET,         // S13
    RISK_MATRIX,             // S14
    FINANCING_PLAN,          // S15
    BUSINESS_PLAN,           // S16
    STRATEGIC_SUMMARY        // S17
}
