package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.*;
import com.senico.diagnostic.dto.dashboard.ActivityEntryDto;
import com.senico.diagnostic.dto.dashboard.AdminDashboardDto;
import com.senico.diagnostic.dto.dashboard.MatrixCellDto;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final Set<SectionStatus> COMPLETED = Set.of(SectionStatus.SUBMITTED, SectionStatus.VALIDATED);

    private final WorkGroupRepository workGroupRepository;
    private final SectionDefRepository sectionDefRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final ProgressService progressService;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public AdminDashboardDto dashboard() {
        List<WorkGroup> groups = workGroupRepository.findAll();
        List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
        List<GroupSectionStatus> allStatuses = groupSectionStatusRepository.findAll();

        int totalGroups = groups.size();
        int activeGroups = (int) groups.stream().filter(WorkGroup::isEnabled).count();

        int totalCompletion = groups.isEmpty() ? 0 : (int) Math.round(
                groups.stream().mapToInt(g -> progressService.completionPercent(g.getId())).average().orElse(0));

        long submitted = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.SUBMITTED).count();
        long validated = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.VALIDATED).count();
        long revision = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.REVISION_REQUESTED).count();

        LocalDate today = LocalDate.now();
        long activityToday = activityLogService.recent(500).stream()
                .filter(a -> a.getTimestamp() != null && a.getTimestamp().toLocalDate().equals(today))
                .count();

        List<AdminDashboardDto.GroupProgressDto> groupProgress = groups.stream()
                .map(g -> AdminDashboardDto.GroupProgressDto.builder()
                        .groupId(g.getId())
                        .groupName(g.getName())
                        .leaderFullName(g.getLeader() != null ? g.getLeader().getFullName() : null)
                        .enabled(g.isEnabled())
                        .completionPercent(progressService.completionPercent(g.getId()))
                        .submitted((int) progressService.countByStatus(g.getId(), SectionStatus.SUBMITTED))
                        .validated((int) progressService.countByStatus(g.getId(), SectionStatus.VALIDATED))
                        .lastActivityAt(progressService.lastActivity(g.getId()).orElse(null))
                        .build())
                .sorted(Comparator.comparing(AdminDashboardDto.GroupProgressDto::groupName))
                .toList();

        List<AdminDashboardDto.SectionAdvancementDto> sectionAdvancement = sections.stream()
                .map(section -> {
                    long completedCount = allStatuses.stream()
                            .filter(s -> s.getSection().getId().equals(section.getId()))
                            .filter(s -> COMPLETED.contains(s.getStatus()))
                            .count();
                    return AdminDashboardDto.SectionAdvancementDto.builder()
                            .sectionId(section.getId())
                            .code(section.getCode())
                            .title(section.getTitle())
                            .order(section.getOrder())
                            .groupsSubmittedOrValidated((int) completedCount)
                            .totalGroups(totalGroups)
                            .build();
                })
                .toList();

        return AdminDashboardDto.builder()
                .totalGroups(totalGroups)
                .activeGroups(activeGroups)
                .globalCompletionPercent(totalCompletion)
                .sectionsSubmitted((int) submitted)
                .sectionsValidated((int) validated)
                .sectionsRevisionRequested((int) revision)
                .activityToday((int) activityToday)
                .groups(groupProgress)
                .sectionAdvancement(sectionAdvancement)
                .build();
    }

    @Transactional(readOnly = true)
    public List<MatrixCellDto> matrix() {
        return groupSectionStatusRepository.findAll().stream()
                .map(s -> MatrixCellDto.builder()
                        .groupId(s.getGroup().getId())
                        .groupName(s.getGroup().getName())
                        .sectionId(s.getSection().getId())
                        .sectionCode(s.getSection().getCode())
                        .status(s.getStatus().name())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ActivityEntryDto> recentActivity(int limit) {
        return activityLogService.recent(limit).stream()
                .map(this::toActivityDto)
                .toList();
    }

    private ActivityEntryDto toActivityDto(ActivityLog log) {
        return ActivityEntryDto.builder()
                .groupId(log.getGroup() != null ? log.getGroup().getId() : null)
                .groupName(log.getGroup() != null ? log.getGroup().getName() : null)
                .userFullName(log.getUser() != null ? log.getUser().getFullName() : null)
                .action(log.getAction())
                .sectionCode(log.getSection() != null ? log.getSection().getCode() : null)
                .sectionTitle(log.getSection() != null ? log.getSection().getTitle() : null)
                .timestamp(log.getTimestamp())
                .build();
    }
}
