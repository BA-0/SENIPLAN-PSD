package com.senico.diagnostic.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Referentiel statique des 17 sections du canevas de diagnostic strategique.
 */
@Entity
@Table(name = "sections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SectionDef {

    @Id
    private Integer id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "display_order", nullable = false)
    private Integer order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SectionType type;
}
