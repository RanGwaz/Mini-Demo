package com.rangwaz.imagesocial.taxonomy.dto;

import java.util.List;

public record PublishSuggestionsResponse(
        List<String> quickTags,
        List<PublishTagSuggestion> trendingTags
) {
}
