package com.senico.diagnostic.dto.dashboard;

import lombok.Builder;

import java.util.List;

@Builder
public record AdminDashboardDto(
        int totalGroups,
        int activeGroups,
        int globalCompletionPercent,
        int sectionsSubmitted,
        int sectionsValidated,
        int sectionsRevisionRequested,
        int activityToday,
        List<GroupProgressDto> groups,
        List<SectionAdvancementDto> sectionAdvancement
) {
    @Builder
    public record GroupProgressDto(
            Long groupId,
            String groupName,
            String leaderFullName,
            boolean enabled,
            int completionPercent,
            int submitted,
            int validated,
            java.time.LocalDateTime lastActivityAt
    ) {
    }

    @Builder
    public record SectionAdvancementDto(
            Integer sectionId,
            String code,
            String title,
            int order,
            int groupsSubmittedOrValidated,
            int totalGroups
    ) {
    }
}
