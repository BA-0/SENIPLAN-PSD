package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_section_status",
        uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "section_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupSectionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private WorkGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private SectionDef section;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    @Builder.Default
    private SectionStatus status = SectionStatus.NOT_STARTED;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "admin_comment", columnDefinition = "TEXT")
    private String adminComment;

    /**
     * Second niveau de validation : le DG approuve une section deja validee par le comite de
     * pilotage. Nul tant qu'il n'a pas tranche, et remis a nul des que la section quitte
     * VALIDATED, pour qu'une approbation ancienne ne couvre jamais un contenu revu depuis.
     */
    @Column(name = "dg_approved_at")
    private LocalDateTime dgApprovedAt;

    @Column(name = "dg_approved_by")
    private Long dgApprovedBy;

    @Column(name = "dg_comment", columnDefinition = "TEXT")
    private String dgComment;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    /**
     * Vrai si le DG a approuve cette section dans son etat valide actuel. Seule condition qui
     * ouvre l'entree d'une contribution dans les documents de consolidation, de synthese et
     * dans le Plan Strategique de SENICO.
     */
    public boolean isDgApproved() {
        return dgApprovedAt != null && status == SectionStatus.VALIDATED;
    }
}
