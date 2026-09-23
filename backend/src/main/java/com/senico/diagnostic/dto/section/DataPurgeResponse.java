package com.senico.diagnostic.dto.section;

/** Resultat d'un effacement des saisies : directions touchees et sections remises a zero. */
public record DataPurgeResponse(int groupsCount, int sectionsCount) {
}
