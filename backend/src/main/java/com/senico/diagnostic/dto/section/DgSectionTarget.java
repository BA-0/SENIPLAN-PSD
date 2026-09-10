package com.senico.diagnostic.dto.section;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Une section designee pour l'arbitrage du DG : sa direction et son code de canevas (S01B...).
 * C'est la maille de l'approbation par lot, ou le DG coche ce qu'il retient dans la liste des
 * soumissions plutot que d'ouvrir chaque section.
 */
public record DgSectionTarget(
        @NotNull(message = "La direction est requise") Long groupId,
        @NotBlank(message = "Le code de section est requis") String sectionCode
) {
}
