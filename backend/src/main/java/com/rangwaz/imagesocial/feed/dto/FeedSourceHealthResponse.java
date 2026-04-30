package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedSourceHealthResponse(
        String surface,
        LocalDateTime processStartedAt,
        int sourceCount,
        List<FeedSourceHealthItem> sources,
        LocalDateTime generatedAt
) {
}
