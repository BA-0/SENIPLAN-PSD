package com.senico.diagnostic.dto.synthesis;

import com.senico.diagnostic.export.ExportBlock;

import java.util.List;

/**
 * Corrections d'un poste. Il n'envoie que les blocs qu'il a modifies : la note pese plusieurs
 * megaoctets, et le pare-feu comme le reseau supportent mal de la renvoyer a chaque enregistrement.
 *
 * @param baseVersion version de la note dont le poste est parti (cf. SynthesisNoteDto#version)
 * @param changes     blocs modifies, par leur place dans cette version
 * @param clientId    identifiant du poste (onglet), repris dans l'annonce temps reel
 * @param blocks      ancien format : la note entiere, qui remplace la version courante. Envoye par un
 *                    onglet ouvert avant la mise en ligne de la fusion, le temps qu'il soit recharge.
 */
public record UpdateSynthesisNoteRequest(String baseVersion, List<BlockChange> changes, String clientId,
                                         List<ExportBlock> blocks) {

    public record BlockChange(int index, ExportBlock block) {
    }
}
