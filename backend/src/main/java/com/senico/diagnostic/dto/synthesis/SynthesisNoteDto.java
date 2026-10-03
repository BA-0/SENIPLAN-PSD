package com.senico.diagnostic.dto.synthesis;

import com.senico.diagnostic.export.ExportBlock;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Contenu de la note de synthese tel que les exports le rendront.
 *
 * @param edited        vrai si la Direction Generale a enregistre une version corrigee
 * @param sourceChanged vrai si le document genere a change depuis cette correction (nouvelles
 *                      approbations, textes modifies) : {@code blocks} les reprend, fusionnes avec
 *                      la version corrigee
 * @param version       empreinte de {@code blocks}, que le poste renvoie avec ses corrections
 * @param conflicts     a l'enregistrement : elements que quelqu'un d'autre avait modifies entre-temps,
 *                      et ou cette version l'a emporte
 */
public record SynthesisNoteDto(
        List<ExportBlock> blocks,
        boolean edited,
        boolean sourceChanged,
        LocalDateTime updatedAt,
        String updatedBy,
        String version,
        int conflicts
) {
}
