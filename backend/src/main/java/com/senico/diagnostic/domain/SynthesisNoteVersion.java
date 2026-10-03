package com.senico.diagnostic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Une version de la note de synthese telle qu'un poste l'a chargee, retrouvee par son empreinte
 * quand ce poste enregistre ses corrections : c'est le point de depart de la fusion avec celles
 * des autres postes.
 */
@Entity
@Table(name = "synthesis_note_versions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SynthesisNoteVersion {

    /** Empreinte SHA-256 du contenu. */
    @Id
    @Column(length = 64, columnDefinition = "CHAR(64)")
    private String version;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
