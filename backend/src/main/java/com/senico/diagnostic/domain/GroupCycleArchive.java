package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Instantane fige d'une section telle qu'elle etait au moment ou un cycle de saisie
 * a ete cloture pour une direction (cf. {@code SectionEngineService#startNewCycle}).
 * Jamais modifie ni supprime : c'est l'archive consultable des anciennes saisies soumises.
 */
@Entity
@Table(name = "group_cycle_archives",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "cycle_number", "section_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupCycleArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private WorkGroup group;

    @Column(name = "cycle_number", nullable = false)
    private Integer cycleNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SectionDef section;

    @Column(name = "content_json", columnDefinition = "json", nullable = false)
    private String contentJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private SectionStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    @Column(name = "archived_at", nullable = false)
    @Builder.Default
    private LocalDateTime archivedAt = LocalDateTime.now();

    @Column(name = "archived_by")
    private Long archivedBy;
}
