package com.senico.diagnostic.dto.dashboard;

import lombok.Builder;

@Builder
public record MatrixCellDto(
        Long groupId,
        String groupName,
        String color,
        Integer sectionId,
        String sectionCode,
        String status
) {
}
