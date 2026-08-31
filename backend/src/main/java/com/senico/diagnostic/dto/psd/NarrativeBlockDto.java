package com.senico.diagnostic.dto.psd;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record NarrativeBlockDto(
        String key,
        String label,
        String content,
        LocalDateTime updatedAt,
        String updatedBy
) {
}
