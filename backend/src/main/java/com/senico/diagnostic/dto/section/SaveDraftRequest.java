package com.senico.diagnostic.dto.section;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

/**
 * @param baseVersion version de la section sur laquelle la page a ete ouverte (0 si rien n'etait encore
 *                    enregistre) : le serveur refuse l'enregistrement si la section a change depuis.
 */
public record SaveDraftRequest(
        @NotNull(message = "Le contenu est requis") JsonNode content,
        Integer baseVersion
) {
}
