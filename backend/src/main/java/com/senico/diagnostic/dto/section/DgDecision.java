package com.senico.diagnostic.dto.section;

/**
 * Arbitrage du DG sur une section deja validee par le comite de pilotage, second et dernier
 * niveau avant l'entree dans les documents consolides.
 */
public enum DgDecision {
    /** Le DG approuve : la section peut alimenter les documents consolides. */
    APPROVE,
    /** Le DG refuse : la section repart en revision cote direction, et sort des documents. */
    REJECT
}
