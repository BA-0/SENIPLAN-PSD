package com.senico.diagnostic.dto.section;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SectionContentResponse(
        Integer sectionId,
        String code,
        String title,
        Integer order,
        String type,
        Long groupId,
        String groupName,
        String status,
        boolean locked,
        JsonNode content,
        Integer version,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime validatedAt,
        String adminComment,
        LocalDateTime lastActivityAt
) {
}
