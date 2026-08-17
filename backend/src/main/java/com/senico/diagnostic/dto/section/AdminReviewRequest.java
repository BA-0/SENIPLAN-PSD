package com.senico.diagnostic.dto.section;

import jakarta.validation.constraints.NotNull;

public record AdminReviewRequest(
        @NotNull(message = "La decision est requise") ReviewDecision decision,
        String comment
) {
}
