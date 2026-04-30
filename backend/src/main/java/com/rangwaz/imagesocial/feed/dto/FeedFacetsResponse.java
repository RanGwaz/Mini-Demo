package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FeedFacetsResponse(
        List<FeedFacetItem> topics,
        List<FeedFacetItem> subtopics,
        List<String> selectedFacetKeys,
        String taxonomyVersion,
        LocalDateTime generatedAt
) {
}
