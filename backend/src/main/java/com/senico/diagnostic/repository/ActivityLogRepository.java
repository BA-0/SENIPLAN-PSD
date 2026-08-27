package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.ActivityLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {
    List<ActivityLog> findAllByOrderByTimestampDesc(Pageable pageable);
    List<ActivityLog> findByGroupIdOrderByTimestampDesc(Long groupId, Pageable pageable);
    Optional<ActivityLog> findFirstByGroupIdAndSectionIdAndUserIdAndActionOrderByTimestampDesc(
            Long groupId, Integer sectionId, Long userId, String action);

    /** Charge les entrees avec groupe, utilisateur et section en une seule requete (evite le N+1). */
    @Query("select a from ActivityLog a " +
            "left join fetch a.group " +
            "left join fetch a.user " +
            "left join fetch a.section " +
            "order by a.timestamp desc")
    List<ActivityLog> findAllWithDetailsOrderByTimestampDesc(Pageable pageable);

    /** Idem, filtre par groupe. */
    @Query("select a from ActivityLog a " +
            "left join fetch a.group " +
            "left join fetch a.user " +
            "left join fetch a.section " +
            "where a.group.id = :groupId " +
            "order by a.timestamp desc")
    List<ActivityLog> findByGroupIdWithDetailsOrderByTimestampDesc(Long groupId, Pageable pageable);
}
