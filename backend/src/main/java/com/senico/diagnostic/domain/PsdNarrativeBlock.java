package com.senico.diagnostic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bloc de texte narratif du "Document final PSD 2027-2031" (Mot du DG, Preambule, etc.),
 * edite par l'admin et injecte a la generation du document (voir export/PsdDocumentStructure).
 */
@Entity
@Table(name = "psd_narrative_blocks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PsdNarrativeBlock {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "block_key", nullable = false, length = 40)
    private NarrativeBlockKey key;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
