package com.senico.diagnostic.dto.group;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Vue d'une autre direction telle qu'un chef de groupe peut la consulter : identite et
 * avancement seulement. Volontairement distinct de {@link WorkGroupDto}, qui expose l'identifiant
 * de connexion du chef de groupe et, a la creation, son mot de passe provisoire.
 */
@Builder
public record PeerGroupDto(
        Long id,
        String name,
        String description,
        String color,
        String leaderFullName,
        Integer completionPercent,
        Integer sectionsSubmitted,
        Integer sectionsValidated,
        LocalDateTime lastActivityAt
) {
}
