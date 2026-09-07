package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.*;
import com.senico.diagnostic.dto.cycle.GroupCycleSectionContentDto;
import com.senico.diagnostic.dto.cycle.GroupCycleSummaryDto;
import com.senico.diagnostic.dto.realtime.SectionProgressEvent;
import com.senico.diagnostic.dto.section.AdminReviewRequest;
import com.senico.diagnostic.dto.section.SectionContentResponse;
import com.senico.diagnostic.dto.section.SectionRevisionContentResponse;
import com.senico.diagnostic.dto.section.SectionRevisionSummaryDto;
import com.senico.diagnostic.dto.section.SectionStatusSummary;
import com.senico.diagnostic.exception.ResourceNotFoundException;
import com.senico.diagnostic.exception.SectionLockedException;
import com.senico.diagnostic.repository.*;
import com.senico.diagnostic.validation.DefaultSectionContentFactory;
import com.senico.diagnostic.validation.SectionContentValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SectionEngineService {

    private static final Set<SectionStatus> LOCKED_STATUSES = Set.of(SectionStatus.SUBMITTED, SectionStatus.VALIDATED);
    private static final int MAX_REVISIONS = 20;

    private final WorkGroupRepository workGroupRepository;
    private final SectionDefRepository sectionDefRepository;
    private final GroupSectionStatusRepository groupSectionStatusRepository;
    private final SectionResponseRepository sectionResponseRepository;
    private final SectionResponseRevisionRepository revisionRepository;
    private final GroupCycleArchiveRepository groupCycleArchiveRepository;
    private final UserRepository userRepository;

    private final SectionContentValidator contentValidator;
    private final DefaultSectionContentFactory defaultContentFactory;
    private final DerivedFieldsService derivedFieldsService;
    private final ProgressService progressService;
    private final ActivityLogService activityLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SectionStatusSummary> listStatuses(Long groupId) {
        return groupSectionStatusRepository.findByGroupIdWithSection(groupId).stream()
                .sorted((a, b) -> a.getSection().getOrder().compareTo(b.getSection().getOrder()))
                .map(this::toSummary)
                .toList();
    }

    /**
     * Batch de {@link #getContent} pour eviter le N+1 : les groupes, le statut et la reponse de
     * chaque groupe pour cette section sont charges en 3 requetes au total plutot qu'en 4 par groupe.
     */
    @Transactional
    public List<SectionContentResponse> compare(String sectionCode, List<Long> groupIds) {
        SectionDef section = resolveSection(sectionCode);

        Map<Long, WorkGroup> groupsById = workGroupRepository.findAllById(groupIds).stream()
                .collect(java.util.stream.Collectors.toMap(WorkGroup::getId, g -> g));

        Map<Long, GroupSectionStatus> statusByGroupId = groupSectionStatusRepository.findBySectionId(section.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(s -> s.getGroup().getId(), s -> s));

        Map<Long, SectionResponse> responseByGroupId = sectionResponseRepository.findBySectionId(section.getId())
                .stream()
                .collect(java.util.stream.Collectors.toMap(r -> r.getGroup().getId(), r -> r));

        return groupIds.stream()
                .map(groupId -> {
                    WorkGroup group = groupsById.get(groupId);
                    if (group == null) {
                        throw new ResourceNotFoundException("Groupe introuvable : " + groupId);
                    }
                    GroupSectionStatus status = statusByGroupId.computeIfAbsent(groupId, id ->
                            groupSectionStatusRepository.save(GroupSectionStatus.builder()
                                    .group(group)
                                    .section(section)
                                    .status(SectionStatus.NOT_STARTED)
                                    .build()));
                    return buildResponse(group, section, responseByGroupId.get(groupId), status);
                })
                .toList();
    }

    @Transactional
    public SectionContentResponse getContent(Long groupId, String sectionCode) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);
        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        return buildResponse(group, section, response, status);
    }

    @Transactional
    public SectionContentResponse saveDraft(Long groupId, String sectionCode, JsonNode rawContent, User actingUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        if (LOCKED_STATUSES.contains(status.getStatus())) {
            throw new SectionLockedException("Cette section est soumise et ne peut plus etre modifiee");
        }

        contentValidator.validate(section.getType(), rawContent, false);
        SectionResponse saved = persistContent(group, section, rawContent, actingUser);

        if (status.getStatus() == SectionStatus.NOT_STARTED) {
            status.setStatus(SectionStatus.IN_PROGRESS);
        }
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        activityLogService.log(group, actingUser, ActivityLogService.ACTION_SAVE_DRAFT, section);
        publishProgress(group, section, status);

        return buildResponse(group, section, saved, status);
    }

    /**
     * Correction directe par l'admin : contourne le verrouillage (SUBMITTED/VALIDATED)
     * sans changer le statut de la section, pour de simples corrections ponctuelles.
     */
    @Transactional
    public SectionContentResponse adminUpdateContent(Long groupId, String sectionCode, JsonNode rawContent, User adminUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        contentValidator.validate(section.getType(), rawContent, false);
        SectionResponse saved = persistContent(group, section, rawContent, adminUser);

        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        activityLogService.log(group, adminUser, ActivityLogService.ACTION_ADMIN_EDIT, section);
        publishProgress(group, section, status);

        return buildResponse(group, section, saved, status);
    }

    /**
     * Efface le contenu saisi et repasse la section a NOT_STARTED, pour qu'un groupe
     * puisse repartir de zero apres une soumission erronee. Le contenu precedent est
     * archive comme revision pour audit (jamais de suppression de la ligne SectionResponse,
     * car section_response_revisions est ON DELETE CASCADE dessus).
     */
    @Transactional
    public SectionContentResponse adminReset(Long groupId, String sectionCode, User adminUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        SectionResponse existing = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        if (existing != null) {
            archiveRevision(existing, adminUser);
            existing.setContentJson(writeJson(defaultContentFactory.buildDefault(section.getType())));
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(adminUser.getId());
            sectionResponseRepository.save(existing);
        }

        status.setStatus(SectionStatus.NOT_STARTED);
        status.setSubmittedAt(null);
        status.setValidatedAt(null);
        status.setAdminComment(null);
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        activityLogService.log(group, adminUser, ActivityLogService.ACTION_RESET, section);
        publishProgress(group, section, status);

        return buildResponse(group, section, existing, status);
    }

    /**
     * Cloture le cycle de saisie en cours pour une direction (toutes les sections doivent
     * etre soumises ou validees) : chaque section est figee dans group_cycle_archives pour
     * consultation ulterieure, puis remise a NOT_STARTED avec un contenu vierge pour que la
     * direction puisse redemarrer une nouvelle saisie. Rien n'est jamais efface : l'ancienne
     * saisie soumise reste consultable via {@link #listCycles} / {@link #getCycleSectionContent}.
     */
    @Transactional
    public GroupCycleSummaryDto startNewCycle(Long groupId, User adminUser) {
        WorkGroup group = resolveGroup(groupId);

        if (progressService.completionPercent(groupId) < 100) {
            throw new SectionLockedException(
                    "Toutes les sections doivent etre soumises avant de demarrer un nouveau cycle pour cette direction");
        }

        List<GroupSectionStatus> statuses = groupSectionStatusRepository.findByGroupId(groupId);
        int closingCycle = group.getCurrentCycle();
        LocalDateTime now = LocalDateTime.now();

        for (GroupSectionStatus status : statuses) {
            SectionDef section = status.getSection();
            SectionResponse response = sectionResponseRepository
                    .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
            String contentJson = response != null
                    ? response.getContentJson()
                    : writeJson(defaultContentFactory.buildDefault(section.getType()));

            groupCycleArchiveRepository.save(GroupCycleArchive.builder()
                    .group(group)
                    .cycleNumber(closingCycle)
                    .section(section)
                    .contentJson(contentJson)
                    .status(status.getStatus())
                    .submittedAt(status.getSubmittedAt())
                    .validatedAt(status.getValidatedAt())
                    .adminComment(status.getAdminComment())
                    .archivedAt(now)
                    .archivedBy(adminUser.getId())
                    .build());

            if (response != null) {
                response.setContentJson(writeJson(defaultContentFactory.buildDefault(section.getType())));
                response.setVersion(response.getVersion() + 1);
                response.setUpdatedAt(now);
                response.setUpdatedBy(adminUser.getId());
                sectionResponseRepository.save(response);
            }

            status.setStatus(SectionStatus.NOT_STARTED);
            status.setSubmittedAt(null);
            status.setValidatedAt(null);
            status.setAdminComment(null);
            status.setLastActivityAt(now);
            groupSectionStatusRepository.save(status);

            publishProgress(group, section, status);
        }

        group.setCurrentCycle(closingCycle + 1);
        workGroupRepository.save(group);

        activityLogService.log(group, adminUser, ActivityLogService.ACTION_START_NEW_CYCLE, null);

        return GroupCycleSummaryDto.builder()
                .cycleNumber(closingCycle)
                .archivedAt(now)
                .archivedByName(adminUser.getFullName())
                .sectionsCount(statuses.size())
                .build();
    }

    @Transactional(readOnly = true)
    public List<GroupCycleSummaryDto> listCycles(Long groupId) {
        List<GroupCycleArchive> all = groupCycleArchiveRepository.findByGroupIdOrderByCycleNumberDesc(groupId);
        if (all.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = all.stream()
                .map(GroupCycleArchive::getArchivedBy)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, String> namesById = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));

        return all.stream()
                .collect(java.util.stream.Collectors.groupingBy(GroupCycleArchive::getCycleNumber))
                .entrySet().stream()
                .map(e -> {
                    GroupCycleArchive first = e.getValue().get(0);
                    LocalDateTime archivedAt = e.getValue().stream()
                            .map(GroupCycleArchive::getArchivedAt)
                            .min(Comparator.naturalOrder())
                            .orElse(first.getArchivedAt());
                    return GroupCycleSummaryDto.builder()
                            .cycleNumber(e.getKey())
                            .archivedAt(archivedAt)
                            .archivedByName(namesById.get(first.getArchivedBy()))
                            .sectionsCount(e.getValue().size())
                            .build();
                })
                .sorted(Comparator.comparing(GroupCycleSummaryDto::cycleNumber).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SectionStatusSummary> getCycleSections(Long groupId, Integer cycleNumber) {
        List<GroupCycleArchive> rows = groupCycleArchiveRepository.findByGroupIdAndCycleNumber(groupId, cycleNumber);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Cycle introuvable : " + cycleNumber);
        }
        return rows.stream()
                .sorted(Comparator.comparing(a -> a.getSection().getOrder()))
                .map(a -> SectionStatusSummary.builder()
                        .sectionId(a.getSection().getId())
                        .code(a.getSection().getCode())
                        .title(a.getSection().getTitle())
                        .order(a.getSection().getOrder())
                        .status(a.getStatus().name())
                        .submittedAt(a.getSubmittedAt())
                        .validatedAt(a.getValidatedAt())
                        .lastActivityAt(a.getArchivedAt())
                        .adminComment(a.getAdminComment())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupCycleSectionContentDto getCycleSectionContent(Long groupId, Integer cycleNumber, String sectionCode) {
        SectionDef section = resolveSection(sectionCode);
        GroupCycleArchive archive = groupCycleArchiveRepository
                .findByGroupIdAndCycleNumberAndSectionId(groupId, cycleNumber, section.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Aucune archive pour cette section dans ce cycle"));

        return GroupCycleSectionContentDto.builder()
                .code(section.getCode())
                .title(section.getTitle())
                .type(section.getType().name())
                .cycleNumber(cycleNumber)
                .status(archive.getStatus().name())
                .submittedAt(archive.getSubmittedAt())
                .validatedAt(archive.getValidatedAt())
                .adminComment(archive.getAdminComment())
                .archivedAt(archive.getArchivedAt())
                .content(parseJson(archive.getContentJson()))
                .build();
    }

    private SectionResponse persistContent(WorkGroup group, SectionDef section, JsonNode rawContent, User actingUser) {
        SectionResponse existing = sectionResponseRepository
                .findByGroupIdAndSectionId(group.getId(), section.getId()).orElse(null);

        if (existing != null) {
            archiveRevision(existing, actingUser);
            existing.setContentJson(writeJson(rawContent));
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(actingUser.getId());
            return sectionResponseRepository.save(existing);
        }

        SectionResponse created = SectionResponse.builder()
                .group(group)
                .section(section)
                .contentJson(writeJson(rawContent))
                .version(1)
                .updatedAt(LocalDateTime.now())
                .updatedBy(actingUser.getId())
                .build();
        return sectionResponseRepository.save(created);
    }

    @Transactional
    public SectionContentResponse submit(Long groupId, String sectionCode, User actingUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        if (LOCKED_STATUSES.contains(status.getStatus())) {
            throw new SectionLockedException("Cette section est deja soumise");
        }

        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId())
                .orElseGet(() -> SectionResponse.builder()
                        .group(group)
                        .section(section)
                        .contentJson(writeJson(defaultContentFactory.buildDefault(section.getType())))
                        .version(1)
                        .updatedAt(LocalDateTime.now())
                        .updatedBy(actingUser.getId())
                        .build());

        JsonNode content = parseJson(response.getContentJson());
        contentValidator.validate(section.getType(), content, true);

        status.setStatus(SectionStatus.SUBMITTED);
        status.setSubmittedAt(LocalDateTime.now());
        status.setValidatedAt(null);
        status.setAdminComment(null);
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        activityLogService.log(group, actingUser, ActivityLogService.ACTION_SUBMIT, section);
        if (progressService.completionPercent(groupId) == 100) {
            activityLogService.log(group, actingUser, ActivityLogService.ACTION_SUBMIT_ALL, null);
        }
        publishProgress(group, section, status);

        return buildResponse(group, section, response, status);
    }

    /**
     * Valide d'un coup toutes les sections soumises d'une direction. Sans cela, alimenter le
     * Plan Strategique de SENICO — qui ne reprend que le valide — demande d'ouvrir chaque
     * section une par une, soit une vingtaine de passages par direction.
     *
     * <p>Ne touche qu'aux sections au statut SUBMITTED : un brouillon en cours, une section
     * renvoyee pour revision ou deja validee est laissee telle quelle, et la methode est donc
     * sans effet si on la rejoue.</p>
     *
     * @return le nombre de sections effectivement validees
     */
    @Transactional
    public int adminValidateAllSubmitted(Long groupId, String comment, User adminUser) {
        WorkGroup group = resolveGroup(groupId);
        LocalDateTime now = LocalDateTime.now();
        int validated = 0;

        for (GroupSectionStatus status : groupSectionStatusRepository.findByGroupIdWithSection(groupId)) {
            if (status.getStatus() != SectionStatus.SUBMITTED) {
                continue;
            }
            status.setStatus(SectionStatus.VALIDATED);
            status.setValidatedAt(now);
            status.setAdminComment(comment);
            status.setLastActivityAt(now);
            groupSectionStatusRepository.save(status);
            activityLogService.log(group, adminUser, ActivityLogService.ACTION_VALIDATE, status.getSection());
            publishProgress(group, status.getSection(), status);
            validated++;
        }
        return validated;
    }

    @Transactional
    public SectionContentResponse adminReview(Long groupId, String sectionCode, AdminReviewRequest request, User adminUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        switch (request.decision()) {
            case VALIDATE -> {
                requireStatus(status, "Seules les sections soumises peuvent etre validees", SectionStatus.SUBMITTED);
                status.setStatus(SectionStatus.VALIDATED);
                status.setValidatedAt(LocalDateTime.now());
                status.setAdminComment(request.comment());
                activityLogService.log(group, adminUser, ActivityLogService.ACTION_VALIDATE, section);
            }
            case REQUEST_REVISION -> {
                requireStatus(status, "Seules les sections soumises peuvent etre renvoyees pour revision", SectionStatus.SUBMITTED);
                status.setStatus(SectionStatus.REVISION_REQUESTED);
                status.setValidatedAt(null);
                status.setAdminComment(request.comment());
                activityLogService.log(group, adminUser, ActivityLogService.ACTION_REQUEST_REVISION, section);
            }
            case RETURN_TO_GROUP -> {
                requireStatus(status, "Seules les sections soumises ou validees peuvent etre redonnees au groupe",
                        SectionStatus.SUBMITTED, SectionStatus.VALIDATED);
                status.setStatus(SectionStatus.IN_PROGRESS);
                status.setSubmittedAt(null);
                status.setValidatedAt(null);
                status.setAdminComment(request.comment());
                activityLogService.log(group, adminUser, ActivityLogService.ACTION_RETURN_TO_GROUP, section);
            }
        }
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        publishProgress(group, section, status);

        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        return buildResponse(group, section, response, status);
    }

    @Transactional(readOnly = true)
    public List<SectionRevisionSummaryDto> getHistory(Long groupId, String sectionCode) {
        SectionDef section = resolveSection(sectionCode);
        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        if (response == null) {
            return List.of();
        }

        List<SectionResponseRevision> revisions = revisionRepository
                .findBySectionResponseIdOrderByCreatedAtDesc(response.getId());

        Set<Long> userIds = new java.util.HashSet<>(revisions.stream().map(SectionResponseRevision::getCreatedBy).toList());
        userIds.add(response.getUpdatedBy());
        Map<Long, String> namesById = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));

        List<SectionRevisionSummaryDto> history = new java.util.ArrayList<>();
        history.add(SectionRevisionSummaryDto.builder()
                .version(response.getVersion())
                .createdAt(response.getUpdatedAt())
                .createdByName(namesById.get(response.getUpdatedBy()))
                .current(true)
                .build());
        revisions.forEach(r -> history.add(SectionRevisionSummaryDto.builder()
                .version(r.getVersion())
                .createdAt(r.getCreatedAt())
                .createdByName(namesById.get(r.getCreatedBy()))
                .current(false)
                .build()));

        return history.stream()
                .sorted(Comparator.comparing(SectionRevisionSummaryDto::version).reversed())
                .toList();
    }

    @Transactional(readOnly = true)
    public SectionRevisionContentResponse getHistoryContent(Long groupId, String sectionCode, Integer version) {
        SectionDef section = resolveSection(sectionCode);
        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Aucune reponse pour cette section"));

        if (version.equals(response.getVersion())) {
            User author = response.getUpdatedBy() != null ? userRepository.findById(response.getUpdatedBy()).orElse(null) : null;
            return SectionRevisionContentResponse.builder()
                    .code(section.getCode())
                    .title(section.getTitle())
                    .type(section.getType().name())
                    .version(response.getVersion())
                    .createdAt(response.getUpdatedAt())
                    .createdByName(author != null ? author.getFullName() : null)
                    .content(parseJson(response.getContentJson()))
                    .build();
        }

        SectionResponseRevision revision = revisionRepository
                .findBySectionResponseIdOrderByCreatedAtDesc(response.getId()).stream()
                .filter(r -> version.equals(r.getVersion()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Version introuvable : " + version));
        User author = revision.getCreatedBy() != null ? userRepository.findById(revision.getCreatedBy()).orElse(null) : null;

        return SectionRevisionContentResponse.builder()
                .code(section.getCode())
                .title(section.getTitle())
                .type(section.getType().name())
                .version(revision.getVersion())
                .createdAt(revision.getCreatedAt())
                .createdByName(author != null ? author.getFullName() : null)
                .content(parseJson(revision.getContentJson()))
                .build();
    }

    // ---------------------------------------------------------------------

    private void archiveRevision(SectionResponse existing, User actingUser) {
        SectionResponseRevision revision = SectionResponseRevision.builder()
                .sectionResponse(existing)
                .contentJson(existing.getContentJson())
                .version(existing.getVersion())
                .createdAt(LocalDateTime.now())
                .createdBy(actingUser.getId())
                .build();
        revisionRepository.save(revision);

        List<SectionResponseRevision> all = revisionRepository
                .findBySectionResponseIdOrderByCreatedAtDesc(existing.getId());
        if (all.size() > MAX_REVISIONS) {
            revisionRepository.deleteAll(all.subList(MAX_REVISIONS, all.size()));
        }
    }

    private SectionContentResponse buildResponse(WorkGroup group, SectionDef section, SectionResponse response, GroupSectionStatus status) {
        ObjectNode content = response != null
                ? (ObjectNode) parseJson(response.getContentJson())
                : defaultContentFactory.buildDefault(section.getType());

        content = derivedFieldsService.apply(section.getType(), group.getId(), content);

        return SectionContentResponse.builder()
                .sectionId(section.getId())
                .code(section.getCode())
                .title(section.getTitle())
                .order(section.getOrder())
                .type(section.getType().name())
                .groupId(group.getId())
                .groupName(group.getName())
                .status(status.getStatus().name())
                .locked(LOCKED_STATUSES.contains(status.getStatus()))
                .content(content)
                .version(response != null ? response.getVersion() : 0)
                .updatedAt(response != null ? response.getUpdatedAt() : null)
                .submittedAt(status.getSubmittedAt())
                .validatedAt(status.getValidatedAt())
                .adminComment(status.getAdminComment())
                .lastActivityAt(status.getLastActivityAt())
                .build();
    }

    private SectionStatusSummary toSummary(GroupSectionStatus status) {
        SectionDef section = status.getSection();
        return SectionStatusSummary.builder()
                .sectionId(section.getId())
                .code(section.getCode())
                .title(section.getTitle())
                .order(section.getOrder())
                .status(status.getStatus().name())
                .submittedAt(status.getSubmittedAt())
                .validatedAt(status.getValidatedAt())
                .lastActivityAt(status.getLastActivityAt())
                .adminComment(status.getAdminComment())
                .build();
    }

    private void publishProgress(WorkGroup group, SectionDef section, GroupSectionStatus status) {
        realtimeEventPublisher.publishProgress(SectionProgressEvent.builder()
                .groupId(group.getId())
                .groupName(group.getName())
                .sectionId(section.getId())
                .sectionCode(section.getCode())
                .sectionTitle(section.getTitle())
                .status(status.getStatus().name())
                .groupCompletionPercent(progressService.completionPercent(group.getId()))
                .timestamp(LocalDateTime.now())
                .build());
    }

    private WorkGroup resolveGroup(Long groupId) {
        return workGroupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Groupe introuvable : " + groupId));
    }

    private SectionDef resolveSection(String code) {
        return sectionDefRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Section introuvable : " + code));
    }

    private void requireStatus(GroupSectionStatus status, String message, SectionStatus... allowed) {
        for (SectionStatus s : allowed) {
            if (status.getStatus() == s) {
                return;
            }
        }
        throw new SectionLockedException(message);
    }

    private GroupSectionStatus resolveStatus(WorkGroup group, SectionDef section) {
        return groupSectionStatusRepository.findByGroupIdAndSectionId(group.getId(), section.getId())
                .orElseGet(() -> groupSectionStatusRepository.save(GroupSectionStatus.builder()
                        .group(group)
                        .section(section)
                        .status(SectionStatus.NOT_STARTED)
                        .build()));
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de serialisation JSON", e);
        }
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Erreur de lecture JSON", e);
        }
    }
}
