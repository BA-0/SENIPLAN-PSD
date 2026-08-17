package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SectionResponseRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SectionResponseRevisionRepository extends JpaRepository<SectionResponseRevision, Long> {
    List<SectionResponseRevision> findBySectionResponseIdOrderByCreatedAtDesc(Long sectionResponseId);
}
