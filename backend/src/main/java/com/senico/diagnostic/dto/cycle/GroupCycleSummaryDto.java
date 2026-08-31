package com.senico.diagnostic.dto.cycle;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record GroupCycleSummaryDto(
        Integer cycleNumber,
        LocalDateTime archivedAt,
        String archivedByName,
        int sectionsCount
) {
}
