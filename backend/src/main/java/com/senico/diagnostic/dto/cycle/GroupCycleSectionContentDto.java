package com.senico.diagnostic.dto.cycle;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GroupCycleSectionContentDto(
        String code,
        String title,
        String type,
        Integer cycleNumber,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime validatedAt,
        String adminComment,
        LocalDateTime archivedAt,
        JsonNode content
) {
}
