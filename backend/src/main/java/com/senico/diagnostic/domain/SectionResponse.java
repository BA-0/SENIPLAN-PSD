package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Contenu JSON libre d'une section pour un groupe donne.
 * La structure JSON est definie/validee cote service par section (voir validation/*).
 */
@Entity
@Table(name = "section_responses",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "section_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private WorkGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SectionDef section;

    @Column(name = "content_json", columnDefinition = "json", nullable = false)
    private String contentJson;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "updated_by")
    private Long updatedBy;
}
