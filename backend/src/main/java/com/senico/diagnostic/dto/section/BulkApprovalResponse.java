package com.senico.diagnostic.dto.section;

/**
 * Resultat d'une approbation en masse par le DG : combien de sections validees ont ete
 * approuvees. Zero est un cas normal (rien n'attendait son arbitrage), pas une erreur.
 */
public record BulkApprovalResponse(int approvedCount) {
}
