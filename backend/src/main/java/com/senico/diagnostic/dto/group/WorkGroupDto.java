package com.senico.diagnostic.dto.group;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record WorkGroupDto(
        Long id,
        String name,
        String description,
        boolean enabled,
        Long leaderUserId,
        String leaderUsername,
        String leaderFullName,
        LocalDateTime createdAt,
        Integer completionPercent,
        Integer sectionsSubmitted,
        Integer sectionsValidated,
        LocalDateTime lastActivityAt
) {
}
