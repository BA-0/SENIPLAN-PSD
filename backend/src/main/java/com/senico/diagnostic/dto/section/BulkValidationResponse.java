package com.senico.diagnostic.dto.section;

/**
 * Resultat d'une validation en masse : combien de sections soumises ont ete validees.
 * Zero est un cas normal (rien n'etait en attente), pas une erreur.
 */
public record BulkValidationResponse(int validatedCount) {
}
