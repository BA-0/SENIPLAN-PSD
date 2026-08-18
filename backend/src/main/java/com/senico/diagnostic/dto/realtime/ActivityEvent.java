package com.senico.diagnostic.dto.realtime;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ActivityEvent(
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
