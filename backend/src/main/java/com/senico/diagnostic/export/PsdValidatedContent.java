package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionResponse;
import com.senico.diagnostic.domain.SectionStatus;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regle de consolidation demandee en revue client : le "Plan Strategique de SENICO"
 * ne reprend que les sections <b>validees</b> par la direction. Une contribution encore
 * en brouillon, soumise mais pas encore tranchee, ou renvoyee pour revision ne doit pas
 * se retrouver dans le document consolide, qui fait foi.
 *
 * <p>Ne s'applique qu'a ce document : le "Document de consolidation" et le plan sectoriel
 * par direction restent des documents de travail et affichent tout, statut a l'appui.</p>
 */
final class PsdValidatedContent {

    private PsdValidatedContent() {
    }

    /** Vrai si la direction a valide cette section, donc si son contenu peut etre consolide. */
    static boolean isValidated(GroupSectionStatus status) {
        return status != null && status.getStatus() == SectionStatus.VALIDATED;
    }

    /**
     * @param responsesByKey reponses indexees "groupId:sectionId"
     * @param statusesByKey  statuts indexes de la meme facon
     * @return les seules reponses dont la section est validee pour cette direction
     */
    static Map<String, SectionResponse> validatedOnly(Map<String, SectionResponse> responsesByKey,
                                                      Map<String, GroupSectionStatus> statusesByKey) {
        Map<String, SectionResponse> filtered = new LinkedHashMap<>();
        responsesByKey.forEach((key, response) -> {
            if (isValidated(statusesByKey.get(key))) {
                filtered.put(key, response);
            }
        });
        return filtered;
    }
}
