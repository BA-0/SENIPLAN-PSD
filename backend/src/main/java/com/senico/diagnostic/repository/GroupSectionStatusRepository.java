package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.GroupSectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupSectionStatusRepository extends JpaRepository<GroupSectionStatus, Long> {
    List<GroupSectionStatus> findByGroupId(Long groupId);
    Optional<GroupSectionStatus> findByGroupIdAndSectionId(Long groupId, Integer sectionId);
    List<GroupSectionStatus> findBySectionId(Integer sectionId);

    /** Charge tous les statuts avec groupe, chef de groupe et section en une seule requete (evite le N+1). */
    @Query("select s from GroupSectionStatus s " +
            "join fetch s.group g " +
            "left join fetch g.leader " +
            "join fetch s.section")
    List<GroupSectionStatus> findAllWithGroupAndSection();
}
