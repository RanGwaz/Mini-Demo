package com.rangwaz.imagesocial.event;

import java.time.Instant;
import java.util.Map;

public record PlatformEvent(
        String eventType,
        Long userId,
        String targetType,
        Long targetId,
        Map<String, Object> payload,
        Instant createdAt
) {
}
