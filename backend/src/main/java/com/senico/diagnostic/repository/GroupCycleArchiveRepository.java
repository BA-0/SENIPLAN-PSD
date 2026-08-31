package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.GroupCycleArchive;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupCycleArchiveRepository extends JpaRepository<GroupCycleArchive, Long> {
    List<GroupCycleArchive> findByGroupIdAndCycleNumber(Long groupId, Integer cycleNumber);
    List<GroupCycleArchive> findByGroupIdOrderByCycleNumberDesc(Long groupId);
    Optional<GroupCycleArchive> findByGroupIdAndCycleNumberAndSectionId(Long groupId, Integer cycleNumber, Integer sectionId);
}
