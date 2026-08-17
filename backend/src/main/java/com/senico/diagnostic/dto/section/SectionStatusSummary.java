package com.senico.diagnostic.dto.section;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SectionStatusSummary(
        Integer sectionId,
        String code,
        String title,
        Integer order,
        String status,
        LocalDateTime submittedAt,
        LocalDateTime validatedAt,
        LocalDateTime lastActivityAt,
        String adminComment
) {
}
