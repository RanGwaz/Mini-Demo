package com.rangwaz.imagesocial.taxonomy.dto;

public record PublishTagSuggestion(
        String name,
        String heat,
        Integer postCount,
        String source
) {
}
