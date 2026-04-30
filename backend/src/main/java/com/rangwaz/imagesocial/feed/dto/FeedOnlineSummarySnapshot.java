package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;

public record FeedOnlineSummarySnapshot(
        String scope,
        String surface,
        int windowDays,
        LocalDateTime fromTime,
        LocalDateTime toTime,
        FeedOnlineSummary summary,
        LocalDateTime generatedAt
) {
}
