package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SectionResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SectionResponseRepository extends JpaRepository<SectionResponse, Long> {
    Optional<SectionResponse> findByGroupIdAndSectionId(Long groupId, Integer sectionId);
    List<SectionResponse> findByGroupId(Long groupId);
    List<SectionResponse> findBySectionId(Integer sectionId);

    /** Projection version seule - evite de charger le contenu JSON complet de chaque reponse. */
    @Query("select r.group.id as groupId, r.section.id as sectionId, r.version as version from SectionResponse r")
    List<VersionProjection> findAllVersions();

    interface VersionProjection {
        Long getGroupId();
        Integer getSectionId();
        Integer getVersion();
    }
}
