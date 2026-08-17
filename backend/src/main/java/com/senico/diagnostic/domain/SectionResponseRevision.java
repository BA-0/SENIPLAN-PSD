package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Historique leger des sauvegardes d'une SectionResponse (audit).
 * On ne conserve que les 20 dernieres revisions par section/groupe (purge en service).
 */
@Entity
@Table(name = "section_response_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionResponseRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_response_id", nullable = false)
    private SectionResponse sectionResponse;

    @Column(name = "content_json", columnDefinition = "json", nullable = false)
    private String contentJson;

    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}
