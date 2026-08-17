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
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private static final Set<SectionStatus> COMPLETED_STATUSES = Set.of(
            SectionStatus.SUBMITTED, SectionStatus.VALIDATED);

    private final GroupSectionStatusRepository groupSectionStatusRepository;

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
