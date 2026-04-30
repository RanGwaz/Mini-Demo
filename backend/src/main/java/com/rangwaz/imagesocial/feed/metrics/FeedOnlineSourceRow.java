package com.rangwaz.imagesocial.feed.metrics;

import lombok.Data;

@Data
public class FeedOnlineSourceRow {
    private String recallSource;
    private long exposureCount;
    private long clickThroughCount;
    private long detailThroughCount;
    private long likeThroughCount;
    private long favoriteThroughCount;
    private long negativeThroughCount;
}

