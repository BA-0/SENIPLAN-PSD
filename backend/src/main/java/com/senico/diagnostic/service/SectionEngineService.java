package com.senico.diagnostic.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.senico.diagnostic.domain.*;
import com.senico.diagnostic.dto.realtime.SectionProgressEvent;
import com.senico.diagnostic.dto.section.AdminReviewRequest;
import com.senico.diagnostic.dto.section.SectionContentResponse;
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
import java.util.List;
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

    private final SectionContentValidator contentValidator;
    private final DefaultSectionContentFactory defaultContentFactory;
    private final DerivedFieldsService derivedFieldsService;
    private final ProgressService progressService;
    private final ActivityLogService activityLogService;
    private final RealtimeEventPublisher realtimeEventPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<SectionStatusSummary> listStatuses(Long groupId) {
        return groupSectionStatusRepository.findByGroupId(groupId).stream()
                .sorted((a, b) -> a.getSection().getOrder().compareTo(b.getSection().getOrder()))
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public List<SectionContentResponse> compare(String sectionCode, List<Long> groupIds) {
        return groupIds.stream().map(groupId -> getContent(groupId, sectionCode)).toList();
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

        SectionResponse existing = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);

        if (existing != null) {
            archiveRevision(existing, actingUser);
            existing.setContentJson(writeJson(rawContent));
            existing.setVersion(existing.getVersion() + 1);
            existing.setUpdatedAt(LocalDateTime.now());
            existing.setUpdatedBy(actingUser.getId());
            sectionResponseRepository.save(existing);
        } else {
            existing = SectionResponse.builder()
                    .group(group)
                    .section(section)
                    .contentJson(writeJson(rawContent))
                    .version(1)
                    .updatedAt(LocalDateTime.now())
                    .updatedBy(actingUser.getId())
                    .build();
            sectionResponseRepository.save(existing);
        }

        if (status.getStatus() == SectionStatus.NOT_STARTED) {
            status.setStatus(SectionStatus.IN_PROGRESS);
        }
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        activityLogService.log(group, actingUser, ActivityLogService.ACTION_SAVE_DRAFT, section);
        publishProgress(group, section, status);

        return buildResponse(group, section, existing, status);
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
        publishProgress(group, section, status);

        return buildResponse(group, section, response, status);
    }

    @Transactional
    public SectionContentResponse adminReview(Long groupId, String sectionCode, AdminReviewRequest request, User adminUser) {
        WorkGroup group = resolveGroup(groupId);
        SectionDef section = resolveSection(sectionCode);
        GroupSectionStatus status = resolveStatus(group, section);

        if (status.getStatus() != SectionStatus.SUBMITTED) {
            throw new SectionLockedException("Seules les sections soumises peuvent etre validees ou renvoyees pour revision");
        }

        switch (request.decision()) {
            case VALIDATE -> {
                status.setStatus(SectionStatus.VALIDATED);
                status.setValidatedAt(LocalDateTime.now());
                status.setAdminComment(request.comment());
                activityLogService.log(group, adminUser, ActivityLogService.ACTION_VALIDATE, section);
            }
            case REQUEST_REVISION -> {
                status.setStatus(SectionStatus.REVISION_REQUESTED);
                status.setValidatedAt(null);
                status.setAdminComment(request.comment());
                activityLogService.log(group, adminUser, ActivityLogService.ACTION_REQUEST_REVISION, section);
            }
        }
        status.setLastActivityAt(LocalDateTime.now());
        groupSectionStatusRepository.save(status);

        publishProgress(group, section, status);

        SectionResponse response = sectionResponseRepository
                .findByGroupIdAndSectionId(groupId, section.getId()).orElse(null);
        return buildResponse(group, section, response, status);
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
