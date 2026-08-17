package com.senico.diagnostic.dto.dashboard;

import com.senico.diagnostic.dto.section.SectionStatusSummary;
import lombok.Builder;

import java.util.List;

@Builder
public record MyDashboardDto(
        Long groupId,
        String groupName,
        int completionPercent,
        int sectionsNotStarted,
        int sectionsInProgress,
        int sectionsSubmitted,
        int sectionsValidated,
        int sectionsRevisionRequested,
        List<SectionStatusSummary> checklist,
        List<SectionStatusSummary> nextSections,
        List<SectionStatusSummary> sectionsWithAdminComment
) {
}
