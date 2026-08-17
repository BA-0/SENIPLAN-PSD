package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.GroupSectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupSectionStatusRepository extends JpaRepository<GroupSectionStatus, Long> {
    List<GroupSectionStatus> findByGroupId(Long groupId);
    Optional<GroupSectionStatus> findByGroupIdAndSectionId(Long groupId, Integer sectionId);
    List<GroupSectionStatus> findBySectionId(Integer sectionId);
}
