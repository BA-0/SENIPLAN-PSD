package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "work_groups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Couleur hexadecimale (ex. #2D7A45) associee a la direction/departement, utilisee pour la distinguer dans les vues et exports consolides. */
    @Column(length = 9)
    private String color;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_user_id")
    private User leader;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Numero du cycle de saisie en cours pour cette direction (incremente a chaque "nouveau cycle"). */
    @Column(name = "current_cycle", nullable = false)
    @Builder.Default
    private Integer currentCycle = 1;
}
