package com.senico.diagnostic.service;

import com.senico.diagnostic.domain.ActivityLog;
import com.senico.diagnostic.domain.SectionDef;
import com.senico.diagnostic.domain.User;
import com.senico.diagnostic.domain.WorkGroup;
import com.senico.diagnostic.dto.realtime.ActivityEvent;
import com.senico.diagnostic.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    public static final String ACTION_SAVE_DRAFT = "SAVE_DRAFT";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_VALIDATE = "VALIDATE";
    public static final String ACTION_REQUEST_REVISION = "REQUEST_REVISION";
    public static final String ACTION_RETURN_TO_GROUP = "RETURN_TO_GROUP";
    public static final String ACTION_RESET = "RESET";
    public static final String ACTION_ADMIN_EDIT = "ADMIN_EDIT";
    public static final String ACTION_LOGIN = "LOGIN";

    /** Autosaves on the same group+section within this window update the existing entry instead of creating a new one. */
    private static final Duration DRAFT_COALESCE_WINDOW = Duration.ofMinutes(5);

    private final ActivityLogRepository activityLogRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Transactional
    public void log(WorkGroup group, User user, String action, SectionDef section) {
        if (ACTION_SAVE_DRAFT.equals(action) && group != null && section != null && user != null) {
            ActivityLog recentDraft = activityLogRepository
                    .findFirstByGroupIdAndSectionIdAndUserIdAndActionOrderByTimestampDesc(
                            group.getId(), section.getId(), user.getId(), action)
                    .filter(entry -> entry.getTimestamp().isAfter(LocalDateTime.now().minus(DRAFT_COALESCE_WINDOW)))
                    .orElse(null);
            if (recentDraft != null) {
                recentDraft.setTimestamp(LocalDateTime.now());
                activityLogRepository.save(recentDraft);
                publish(recentDraft);
                return;
            }
        }

        ActivityLog entry = ActivityLog.builder()
                .group(group)
                .user(user)
                .action(action)
                .section(section)
                .timestamp(LocalDateTime.now())
                .build();
        activityLogRepository.save(entry);
        publish(entry);
    }

    private void publish(ActivityLog entry) {
        WorkGroup group = entry.getGroup();
        SectionDef section = entry.getSection();
        realtimeEventPublisher.publishActivity(ActivityEvent.builder()
                .id(entry.getId())
                .groupId(group != null ? group.getId() : null)
                .groupName(group != null ? group.getName() : null)
                .userFullName(entry.getUser() != null ? entry.getUser().getFullName() : null)
                .action(entry.getAction())
                .sectionCode(section != null ? section.getCode() : null)
                .sectionTitle(section != null ? section.getTitle() : null)
                .timestamp(entry.getTimestamp())
                .build());
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> recent(int limit) {
        return activityLogRepository.findAllByOrderByTimestampDesc(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<ActivityLog> recentForGroup(Long groupId, int limit) {
        return activityLogRepository.findByGroupIdOrderByTimestampDesc(groupId, PageRequest.of(0, limit));
    }
}
