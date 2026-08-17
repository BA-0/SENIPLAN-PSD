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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    public static final String ACTION_SAVE_DRAFT = "SAVE_DRAFT";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_VALIDATE = "VALIDATE";
    public static final String ACTION_REQUEST_REVISION = "REQUEST_REVISION";
    public static final String ACTION_LOGIN = "LOGIN";

    private final ActivityLogRepository activityLogRepository;
    private final RealtimeEventPublisher realtimeEventPublisher;

    @Transactional
    public void log(WorkGroup group, User user, String action, SectionDef section) {
        ActivityLog entry = ActivityLog.builder()
                .group(group)
                .user(user)
                .action(action)
                .section(section)
                .timestamp(LocalDateTime.now())
                .build();
        activityLogRepository.save(entry);

        realtimeEventPublisher.publishActivity(ActivityEvent.builder()
                .groupId(group != null ? group.getId() : null)
                .groupName(group != null ? group.getName() : null)
                .userFullName(user != null ? user.getFullName() : null)
                .action(action)
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
