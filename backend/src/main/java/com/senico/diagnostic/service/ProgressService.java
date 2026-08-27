package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private static final Set<SectionStatus> COMPLETED_STATUSES = Set.of(
            SectionStatus.SUBMITTED, SectionStatus.VALIDATED);

    private static final GroupProgress EMPTY_PROGRESS = new GroupProgress(0, 0, 0, null);

    private final GroupSectionStatusRepository groupSectionStatusRepository;

    /** Metriques de progression d'un groupe, calculees a partir d'une seule liste de statuts. */
    public record GroupProgress(int completionPercent, int submitted, int validated, LocalDateTime lastActivityAt) {}

    /** Calcule toutes les metriques d'un groupe en une seule requete, au lieu de 4 requetes separees. */
    @Transactional(readOnly = true)
    public GroupProgress progressFor(Long groupId) {
        return summarize(groupSectionStatusRepository.findByGroupId(groupId));
    }

    /**
     * Calcule la progression de plusieurs groupes a partir d'une liste de statuts deja chargee
     * (par exemple via findAll()), sans emettre de requete supplementaire par groupe.
     */
    public Map<Long, GroupProgress> summarizeByGroup(List<GroupSectionStatus> statuses) {
        return statuses.stream()
                .collect(Collectors.groupingBy(s -> s.getGroup().getId()))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> summarize(e.getValue())));
    }

    private GroupProgress summarize(List<GroupSectionStatus> statuses) {
        if (statuses.isEmpty()) {
            return EMPTY_PROGRESS;
        }
        long completed = statuses.stream().filter(s -> COMPLETED_STATUSES.contains(s.getStatus())).count();
        int percent = (int) Math.round((completed * 100.0) / statuses.size());
        int submitted = (int) statuses.stream().filter(s -> s.getStatus() == SectionStatus.SUBMITTED).count();
        int validated = (int) statuses.stream().filter(s -> s.getStatus() == SectionStatus.VALIDATED).count();
        LocalDateTime lastActivity = statuses.stream()
                .map(GroupSectionStatus::getLastActivityAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new GroupProgress(percent, submitted, validated, lastActivity);
    }

    @Transactional(readOnly = true)
    public int completionPercent(Long groupId) {
        List<GroupSectionStatus> statuses = groupSectionStatusRepository.findByGroupId(groupId);
        if (statuses.isEmpty()) {
            return 0;
        }
        long completed = statuses.stream().filter(s -> COMPLETED_STATUSES.contains(s.getStatus())).count();
        return (int) Math.round((completed * 100.0) / statuses.size());
    }

    @Transactional(readOnly = true)
    public long countByStatus(Long groupId, SectionStatus status) {
        return groupSectionStatusRepository.findByGroupId(groupId).stream()
                .filter(s -> s.getStatus() == status)
                .count();
    }

    @Transactional(readOnly = true)
    public Optional<LocalDateTime> lastActivity(Long groupId) {
        return groupSectionStatusRepository.findByGroupId(groupId).stream()
                .map(GroupSectionStatus::getLastActivityAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder());
    }
}
