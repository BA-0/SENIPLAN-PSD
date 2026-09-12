package com.senico.diagnostic.repository;

import com.senico.diagnostic.domain.SectionResponseRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SectionResponseRevisionRepository extends JpaRepository<SectionResponseRevision, Long> {
    List<SectionResponseRevision> findBySectionResponseIdOrderByCreatedAtDesc(Long sectionResponseId);

    /**
     * Identifiants seuls, du plus recent au plus ancien : de quoi purger l'historique sans en relire
     * le contenu JSON. L'id departage les revisions enregistrees dans la meme seconde.
     */
    @Query("select r.id from SectionResponseRevision r where r.sectionResponse.id = :sectionResponseId "
            + "order by r.createdAt desc, r.id desc")
    List<Long> findIdsBySectionResponseIdNewestFirst(Long sectionResponseId);
}
