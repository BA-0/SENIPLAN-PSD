package com.senico.diagnostic.dto.section;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SectionRevisionSummaryDto(
        Integer version,
        LocalDateTime createdAt,
        String createdByName,
        boolean current
) {
}
