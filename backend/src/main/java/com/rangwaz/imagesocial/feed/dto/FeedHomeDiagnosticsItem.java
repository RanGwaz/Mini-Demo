package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;

public record FeedHomeDiagnosticsItem(
        int rankPosition,
        Long postId,
        Long authorId,
        String title,
        String topicPath,
        String reason,
        int score,
        double hotScore,
        LocalDateTime createdAt
) {
}
