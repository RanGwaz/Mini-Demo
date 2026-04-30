package com.rangwaz.imagesocial.feed.dto;

public record FeedHomeDiagnosticsStage(
        int mergedAfterPrimarySources,
        int mergedAfterFallback,
        int explicitBoostedItems,
        int mergedAfterExplicitBoost,
        int mergedBeforeSafetyFilter,
        int mergedAfterSafetyFilter,
        int rankedBeforeSeenSuppression,
        int shortTermSuppressedCount,
        int strongSuppressedCount,
        int softSuppressedCount,
        int rankedAfterSeenSuppression,
        int rankedAfterDiversity,
        int rankedAfterSemanticFilter,
        int pageWindowStart,
        int pageWindowEnd,
        int pageItemCount
) {
}
