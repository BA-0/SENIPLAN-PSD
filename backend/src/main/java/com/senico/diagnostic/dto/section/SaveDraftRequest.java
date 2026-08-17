package com.senico.diagnostic.dto.section;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

public record SaveDraftRequest(
        @NotNull(message = "Le contenu est requis") JsonNode content
) {
}
