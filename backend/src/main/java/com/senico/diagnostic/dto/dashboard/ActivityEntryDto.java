package com.senico.diagnostic.dto.dashboard;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ActivityEntryDto(
        Long id,
        Long groupId,
        String groupName,
        String userFullName,
        String action,
        String sectionCode,
        String sectionTitle,
        LocalDateTime timestamp
) {
}
