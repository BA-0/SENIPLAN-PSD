package com.senico.diagnostic.dto.dashboard;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record SubmissionSummaryDto(
        Long groupId,
        String groupName,
        String leaderFullName,
        Integer sectionId,
        String sectionCode,
        String sectionTitle,
        Integer sectionOrder,
        String status,
        Integer version,
        LocalDateTime submittedAt,
        LocalDateTime validatedAt,
        LocalDateTime lastActivityAt,
        String adminComment
) {
}
