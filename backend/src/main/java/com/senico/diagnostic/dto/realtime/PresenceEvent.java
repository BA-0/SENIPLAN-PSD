package com.senico.diagnostic.dto.realtime;

import lombok.Builder;

@Builder
public record PresenceEvent(
        Long groupId,
        String groupName,
        Integer sectionId,
        String sectionCode,
        boolean typing
) {
}
