package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);
    List<ActivityLog> findByGroupIdOrderByTimestampDesc(Long groupId, Pageable pageable);
    Optional<ActivityLog> findFirstByGroupIdAndSectionIdAndUserIdAndActionOrderByTimestampDesc(
            Long groupId, Integer sectionId, Long userId, String action);
}
