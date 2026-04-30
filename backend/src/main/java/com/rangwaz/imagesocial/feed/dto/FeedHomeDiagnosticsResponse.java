package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedHomeDiagnosticsResponse(
        FeedHomeDiagnosticsRequest request,
        FeedHomeDiagnosticsFilters filters,
        FeedHomeDiagnosticsStage stages,
        List<FeedHomeDiagnosticsSource> sources,
        List<FeedHomeDiagnosticsReasonMetric> reasonMix,
        List<FeedHomeDiagnosticsItem> pageItems,
        long finalRankedTotal,
        LocalDateTime generatedAt
) {
}
