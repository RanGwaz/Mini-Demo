package com.rangwaz.imagesocial.feed.dto;

import java.util.List;

public record FeedFacetItem(
        String facetKey,
        String facetLabel,
        String parentFacetKey,
        Integer level,
        Integer postCount,
        Integer recentPostCount,
        double trendScore,
        boolean subscribed,
        List<String> keywords
) {
}
