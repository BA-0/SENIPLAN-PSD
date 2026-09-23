package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.GroupCycleArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GroupCycleArchiveRepository extends JpaRepository<GroupCycleArchive, Long> {
    List<GroupCycleArchive> findByGroupIdAndCycleNumber(Long groupId, Integer cycleNumber);
    List<GroupCycleArchive> findByGroupIdOrderByCycleNumberDesc(Long groupId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GroupCycleArchive x where x.group.id = :groupId")
    int deleteAllByGroupId(Long groupId);

    Optional<GroupCycleArchive> findByGroupIdAndCycleNumberAndSectionId(Long groupId, Integer cycleNumber, Integer sectionId);
}
