package com.senico.diagnostic.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionDef;

/**
 * Regroupe une section, son contenu JSON et ses metadonnees de soumission (statut, version,
 * dates, commentaire admin) pour l'export PDF/Word.
 *
 * @param withheldPendingValidation contenu volontairement retire faute de validation
 *                                  (cf. {@link PsdValidatedContent}) : le contenu est alors
 *                                  vide sans que la direction ait rien laisse en blanc, ce que
 *                                  le rendu doit dire explicitement plutot que d'annoncer une
 *                                  section non saisie. Faux partout ailleurs.
 */
record ExportSectionData(SectionDef section, JsonNode content, Integer version, GroupSectionStatus status,
                         boolean withheldPendingValidation) {

    ExportSectionData(SectionDef section, JsonNode content, Integer version, GroupSectionStatus status) {
        this(section, content, version, status, false);
    }
}
