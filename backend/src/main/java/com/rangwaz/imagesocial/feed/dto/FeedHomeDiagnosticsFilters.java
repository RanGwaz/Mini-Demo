package com.rangwaz.imagesocial.feed.dto;

import java.util.List;

public record FeedHomeDiagnosticsFilters(
        List<String> topicTerms,
        List<String> styleTerms,
        List<String> tagTerms
) {
}
