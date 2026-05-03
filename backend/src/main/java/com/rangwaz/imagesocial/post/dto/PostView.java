package com.rangwaz.imagesocial.post.dto;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import java.time.LocalDateTime;
import java.util.List;

public record PostView(
        Long id,
        UserSummary author,
        String title,
        String content,
        List<String> tags,
        String channel,
        String topicPath,
        List<String> semanticTags,
        List<String> styleTags,
        List<PostAssetView> assets,
        String coverUrl,
        String thumbUrl,
        Integer likeCount,
        Integer favoriteCount,
        Integer commentCount,
        Long viewCount,
        String recommendationReason,
        LocalDateTime createdAt
) {
}
