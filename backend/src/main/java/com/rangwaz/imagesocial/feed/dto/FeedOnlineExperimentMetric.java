package com.rangwaz.imagesocial.feed.dto;

public record FeedOnlineExperimentMetric(
        String experimentId,
        String bucket,
        long exposureCount,
        long clickThroughCount,
        long detailThroughCount,
        long likeThroughCount,
        long favoriteThroughCount,
        double ctr,
        double detailRate,
        double likeRate,
        double favoriteRate
) {
}

