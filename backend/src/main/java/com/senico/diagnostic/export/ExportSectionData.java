package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;

/**
 * Regroupe une section, son contenu JSON et ses metadonnees de soumission (statut, version,
 * dates, commentaire admin) pour l'export PDF/Word.
 */
record ExportSectionData(SectionDef section, JsonNode content, Integer version, GroupSectionStatus status) {
}
