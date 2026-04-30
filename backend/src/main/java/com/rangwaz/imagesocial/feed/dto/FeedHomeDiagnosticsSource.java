package com.rangwaz.imagesocial.feed.dto;

public record FeedHomeDiagnosticsSource(
        String sourceKey,
        String reasonLabel,
        int scoreContribution,
        int requestedFetchSize,
        int uniqueQuota,
        String status,
        int recalledCount,
        int uniqueAdded,
        int mergedSizeBefore,
        int mergedSizeAfter,
        int latencyBudgetMs,
        long latencyMs,
        boolean overBudget,
        String message
) {
}
