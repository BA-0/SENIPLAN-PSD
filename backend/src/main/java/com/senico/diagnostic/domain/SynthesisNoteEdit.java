package com.senico.diagnostic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Note de synthese telle que la Direction Generale l'a corrigee : la liste des blocs du document
 * en JSON (cf. export/ExportBlock). Une seule ligne ; tant qu'elle existe, les exports de la note
 * reprennent ce contenu au lieu du document genere.
 */
@Entity
@Table(name = "synthesis_note_edits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SynthesisNoteEdit {

    /** Identifiant de l'unique ligne. */
    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    /** Document genere au moment de l'enregistrement, en JSON : point de depart de la fusion avec ses mises a jour. */
    @Column(name = "source_content", columnDefinition = "LONGTEXT")
    private String sourceContent;

    /** Empreinte SHA-256 du document genere au moment de l'enregistrement. */
    @Column(name = "source_hash", length = 64)
    private String sourceHash;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
