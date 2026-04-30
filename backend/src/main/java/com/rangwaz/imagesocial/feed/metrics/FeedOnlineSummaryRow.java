package com.rangwaz.imagesocial.feed.metrics;

import lombok.Data;

@Data
public class FeedOnlineSummaryRow {
    private long exposureCount;
    private long clickCount;
    private long detailViewCount;
    private long likeCount;
    private long favoriteCount;
    private long commentCount;
    private long shareCount;
    private long negativeCount;
    private long requestUv;
    private long exposureUserUv;
    private Double avgDwellMs;
}

