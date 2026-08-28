package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.*;
import com.senico.diagnostic.dto.dashboard.ActivityEntryDto;
import com.senico.diagnostic.dto.dashboard.AdminDashboardDto;
import com.senico.diagnostic.dto.dashboard.MatrixCellDto;
import com.senico.diagnostic.dto.dashboard.SubmissionSummaryDto;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionDefRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private static final Set<SectionStatus> COMPLETED = Set.of(SectionStatus.SUBMITTED, SectionStatus.VALIDATED);
    private static final ProgressService.GroupProgress EMPTY_PROGRESS =
            new ProgressService.GroupProgress(0, 0, 0, null);

    private final WorkGroupRepository workGroupRepository;
    private final SectionDefRepository sectionDefRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final ProgressService progressService;
    private final ActivityLogService activityLogService;

    @Transactional(readOnly = true)
    public AdminDashboardDto dashboard() {
        List<WorkGroup> groups = workGroupRepository.findAllWithLeader();
        List<SectionDef> sections = sectionDefRepository.findAllByOrderByOrderAsc();
        List<GroupSectionStatus> allStatuses = groupSectionStatusRepository.findAll();
        Map<Long, ProgressService.GroupProgress> progressByGroup = progressService.summarizeByGroup(allStatuses);

        int totalGroups = groups.size();
        int activeGroups = (int) groups.stream().filter(WorkGroup::isEnabled).count();

        int totalCompletion = groups.isEmpty() ? 0 : (int) Math.round(
                groups.stream()
                        .mapToInt(g -> progressByGroup.getOrDefault(g.getId(), EMPTY_PROGRESS).completionPercent())
                        .average().orElse(0));

        long submitted = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.SUBMITTED).count();
        long validated = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.VALIDATED).count();
        long revision = allStatuses.stream().filter(s -> s.getStatus() == SectionStatus.REVISION_REQUESTED).count();

        long activityToday = activityLogService.countSince(LocalDate.now().atStartOfDay());

        List<AdminDashboardDto.GroupProgressDto> groupProgress = groups.stream()
                .map(g -> {
                    ProgressService.GroupProgress progress = progressByGroup.getOrDefault(g.getId(), EMPTY_PROGRESS);
                    return AdminDashboardDto.GroupProgressDto.builder()
                            .groupId(g.getId())
                            .groupName(g.getName())
                            .color(g.getColor())
                            .leaderFullName(g.getLeader() != null ? g.getLeader().getFullName() : null)
                            .enabled(g.isEnabled())
                            .completionPercent(progress.completionPercent())
                            .submitted(progress.submitted())
                            .validated(progress.validated())
                            .lastActivityAt(progress.lastActivityAt())
                            .build();
                })
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
        return groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                .map(s -> MatrixCellDto.builder()
                        .groupId(s.getGroup().getId())
                        .groupName(s.getGroup().getName())
                        .color(s.getGroup().getColor())
                        .sectionId(s.getSection().getId())
                        .sectionCode(s.getSection().getCode())
                        .status(s.getStatus().name())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummaryDto> submissions() {
        Map<String, Integer> versionsByGroupAndSection = sectionResponseRepository.findAllVersions().stream()
                .collect(java.util.stream.Collectors.toMap(
                        p -> p.getGroupId() + ":" + p.getSectionId(),
                        SectionResponseRepository.VersionProjection::getVersion,
                        (a, b) -> b));

        return groupSectionStatusRepository.findAllWithGroupAndSection().stream()
                .map(s -> {
                    String key = s.getGroup().getId() + ":" + s.getSection().getId();
                    return SubmissionSummaryDto.builder()
                            .groupId(s.getGroup().getId())
                            .groupName(s.getGroup().getName())
                            .leaderFullName(s.getGroup().getLeader() != null ? s.getGroup().getLeader().getFullName() : null)
                            .sectionId(s.getSection().getId())
                            .sectionCode(s.getSection().getCode())
                            .sectionTitle(s.getSection().getTitle())
                            .sectionOrder(s.getSection().getOrder())
                            .status(s.getStatus().name())
                            .version(versionsByGroupAndSection.getOrDefault(key, 0))
                            .submittedAt(s.getSubmittedAt())
                            .validatedAt(s.getValidatedAt())
                            .lastActivityAt(s.getLastActivityAt())
                            .adminComment(s.getAdminComment())
                            .build();
                })
                .sorted(Comparator.comparing(SubmissionSummaryDto::groupName)
                        .thenComparing(SubmissionSummaryDto::sectionOrder))
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
                .id(log.getId())
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
