package com.senico.diagnostic.dto.section;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SectionRevisionContentResponse(
        String code,
        String title,
        String type,
        Integer version,
        LocalDateTime createdAt,
        String createdByName,
        JsonNode content
) {
}
