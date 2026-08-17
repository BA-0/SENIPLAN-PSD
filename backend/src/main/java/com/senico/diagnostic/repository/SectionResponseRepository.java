package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SectionResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SectionResponseRepository extends JpaRepository<SectionResponse, Long> {
    Optional<SectionResponse> findByGroupIdAndSectionId(Long groupId, Integer sectionId);
    List<SectionResponse> findByGroupId(Long groupId);
    List<SectionResponse> findBySectionId(Integer sectionId);
}
