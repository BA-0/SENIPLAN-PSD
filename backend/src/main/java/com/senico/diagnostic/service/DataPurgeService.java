package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.GroupSectionStatus;
import com.senico.diagnostic.domain.SectionStatus;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.realtime.SectionProgressEvent;
import com.senico.diagnostic.dto.section.DataPurgeResponse;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.repository.ActivityLogRepository;
import com.senico.diagnostic.repository.GroupCycleArchiveRepository;
import com.senico.diagnostic.repository.GroupSectionStatusRepository;
import com.senico.diagnostic.repository.SectionResponseRepository;
import com.senico.diagnostic.repository.WorkGroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Effacement definitif des saisies d'une direction, ou de toutes : pour repartir d'une base
 * propre apres les essais, avant le lancement reel de la campagne.
 *
 * <p>A la difference de la reinitialisation d'une section ou du nouveau cycle, rien n'est
 * archive : contenus, historique des versions (supprime en cascade avec les reponses), cycles
 * archives et journal d'activite de la direction disparaissent. Les directions, les comptes et
 * les textes du plan strategique restent en place ; chaque section repart a « non commencee »,
 * au cycle 1.</p>
 */
@Service
@RequiredArgsConstructor
public class DataPurgeService {

    private final WorkGroupRepository workGroupRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final GroupCycleArchiveRepository groupCycleArchiveRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ActivityLogService activityLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Transactional
    public DataPurgeResponse purgeGroup(Long groupId, User adminUser) {
        if (!workGroupRepository.existsById(groupId)) {
            throw new ResourceNotFoundException("Groupe introuvable : " + groupId);
        }
        return new DataPurgeResponse(1, purge(groupId, adminUser));
    }

    @Transactional
    public DataPurgeResponse purgeAllGroups(User adminUser) {
        List<Long> groupIds = workGroupRepository.findAll().stream().map(WorkGroup::getId).toList();
        int sections = 0;
        for (Long groupId : groupIds) {
            sections += purge(groupId, adminUser);
        }
        return new DataPurgeResponse(groupIds.size(), sections);
    }

    /** Rend le nombre de sections remises a zero. */
    private int purge(Long groupId, User adminUser) {
        // Suppressions en masse d'abord : elles vident le contexte de persistance, les entites
        // sont donc relues ensuite.
        sectionResponseRepository.deleteAllByGroupId(groupId);
        groupCycleArchiveRepository.deleteAllByGroupId(groupId);
        activityLogRepository.deleteAllByGroupId(groupId);

        WorkGroup group = workGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable : " + groupId));
        group.setCurrentCycle(1);
        workGroupRepository.save(group);

        List<GroupSectionStatus> statuses = groupSectionStatusRepository.findByGroupIdWithSection(groupId);
        for (GroupSectionStatus status : statuses) {
            status.setStatus(SectionStatus.NOT_STARTED);
            status.setSubmittedAt(null);
            status.setValidatedAt(null);
            status.setAdminComment(null);
            status.setDgApprovedAt(null);
            status.setDgApprovedBy(null);
            status.setDgComment(null);
            status.setLastActivityAt(null);
        }
        groupSectionStatusRepository.saveAll(statuses);

        // Seule trace laissee : qui a efface, et quand.
        activityLogService.log(group, adminUser, ActivityLogService.ACTION_PURGE_DATA, null);

        LocalDateTime now = LocalDateTime.now();
        for (GroupSectionStatus status : statuses) {
            realtimeEventPublisher.publishProgress(SectionProgressEvent.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .sectionId(status.getSection().getId())
                    .sectionCode(status.getSection().getCode())
                    .sectionTitle(status.getSection().getTitle())
                    .status(SectionStatus.NOT_STARTED.name())
                    .groupCompletionPercent(0)
                    .timestamp(now)
                    .build());
        }
        return statuses.size();
    }
}
