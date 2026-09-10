package com.senico.diagnostic.export;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Regle de consolidation : une contribution n'entre dans un document consolide que si le DG
 * l'a approuvee, apres validation par le comite de pilotage. Un brouillon, une section soumise
 * mais pas encore tranchee, une section renvoyee pour revision, ou une section validee que le
 * DG n'a pas encore approuvee restent dehors.
 *
 * <p>S'applique aux trois documents qui font foi : le Document de consolidation, la Note de
 * synthese et le Plan Strategique de SENICO. Le plan sectoriel d'une seule direction reste un
 * document de travail et affiche tout, statut a l'appui.</p>
 */
final class PsdApprovedContent {

    private PsdApprovedContent() {
    }

    /** Vrai si le DG a approuve cette section, donc si son contenu peut etre consolide. */
    static boolean isApproved(GroupSectionStatus status) {
        return status != null && status.isDgApproved();
    }

    /**
     * @param responsesByKey reponses indexees "groupId:sectionId"
     * @param statusesByKey  statuts indexes de la meme facon
     * @return les seules reponses dont la section est approuvee par le DG
     */
    static Map<String, SectionResponse> approvedOnly(Map<String, SectionResponse> responsesByKey,
                                                     Map<String, GroupSectionStatus> statusesByKey) {
        Map<String, SectionResponse> filtered = new LinkedHashMap<>();
        responsesByKey.forEach((key, response) -> {
            if (isApproved(statusesByKey.get(key))) {
                filtered.put(key, response);
            }
        });
        return filtered;
    }
}
