package com.rangwaz.imagesocial.feed.dto;

public record FeedOnlineSourceMetric(
        String recallSource,
        long exposureCount,
        long clickThroughCount,
        long detailThroughCount,
        long likeThroughCount,
        long favoriteThroughCount,
        long negativeThroughCount,
        double ctr,
        double detailRate,
        double likeRate,
        double favoriteRate,
        double negativeRate
) {
}

