package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedOnlineMetricsResponse(
        String scope,
        String surface,
        int windowDays,
        LocalDateTime fromTime,
        LocalDateTime toTime,
        FeedOnlineSummary summary,
        List<FeedOnlineSourceMetric> sourceMetrics,
        List<FeedOnlineExperimentMetric> experimentMetrics,
        LocalDateTime generatedAt
) {
}

