package com.senico.diagnostic.dto.realtime;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Evenement pousse en direct au dashboard admin a chaque autosave/soumission/validation.
 */
@Builder
public record SectionProgressEvent(
        Long groupId,
        String groupName,
        Integer sectionId,
        String sectionCode,
        String sectionTitle,
        String status,
        Integer groupCompletionPercent,
        LocalDateTime timestamp
) {
}
