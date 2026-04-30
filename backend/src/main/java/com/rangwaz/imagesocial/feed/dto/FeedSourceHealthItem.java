package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;

public record FeedSourceHealthItem(
        String sourceKey,
        int latencyBudgetMs,
        long totalCalls,
        long successCount,
        long emptyCount,
        long failedCount,
        long skippedCount,
        long overBudgetCount,
        double avgLatencyMs,
        long maxLatencyMs,
        long lastLatencyMs,
        String lastStatus,
        String lastMessage,
        LocalDateTime lastUpdatedAt
) {
}
