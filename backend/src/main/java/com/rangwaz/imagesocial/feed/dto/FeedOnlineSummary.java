package com.rangwaz.imagesocial.feed.dto;

public record FeedOnlineSummary(
        long exposureCount,
        long clickCount,
        long detailViewCount,
        long likeCount,
        long favoriteCount,
        long commentCount,
        long shareCount,
        long negativeCount,
        long requestUv,
        long exposureUserUv,
        double ctr,
        double detailRate,
        double likeRate,
        double favoriteRate,
        double negativeRate,
        double avgDwellMs
) {
}

