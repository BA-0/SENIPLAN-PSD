package com.senico.diagnostic.dto.section;

import jakarta.validation.constraints.NotNull;

public record DgApprovalRequest(
        @NotNull(message = "La decision est requise") DgDecision decision,
        String comment
) {
}
