package com.senico.diagnostic.service;

import com.senico.diagnostic.dto.dashboard.MyDashboardDto;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MyDashboardService {

    private final WorkGroupRepository workGroupRepository;
    private final SectionEngineService sectionEngineService;
    private final ProgressService progressService;

    @Transactional(readOnly = true)
    public MyDashboardDto build(Long groupId) {
        var group = workGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable"));

        List<SectionStatusSummary> checklist = sectionEngineService.listStatuses(groupId);

        List<SectionStatusSummary> nextSections = checklist.stream()
                .filter(s -> Set.of("NOT_STARTED", "IN_PROGRESS", "REVISION_REQUESTED").contains(s.status()))
                .sorted(Comparator.comparing(SectionStatusSummary::order))
                .limit(5)
                .toList();

        List<SectionStatusSummary> withComments = checklist.stream()
                .filter(s -> s.adminComment() != null && !s.adminComment().isBlank())
                .sorted(Comparator.comparing(SectionStatusSummary::lastActivityAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return MyDashboardDto.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .completionPercent(progressService.completionPercent(groupId))
                .sectionsNotStarted(count(checklist, "NOT_STARTED"))
                .sectionsInProgress(count(checklist, "IN_PROGRESS"))
                .sectionsSubmitted(count(checklist, "SUBMITTED"))
                .sectionsValidated(count(checklist, "VALIDATED"))
                .sectionsRevisionRequested(count(checklist, "REVISION_REQUESTED"))
                .checklist(checklist)
                .nextSections(nextSections)
                .sectionsWithAdminComment(withComments)
                .build();
    }

    private int count(List<SectionStatusSummary> checklist, String status) {
        return (int) checklist.stream().filter(s -> s.status().equals(status)).count();
    }
}
