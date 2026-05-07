package com.rangwaz.imagesocial.post.dto;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record PostView(
        Long id,
        UserSummary author,
        String title,
        String content,
        List<String> tags,
        String channel,
        String channelCode,
        String postType,
        String topicPath,
        List<String> semanticTags,
        List<String> styleTags,
        List<PostAssetView> assets,
        List<PostImageView> images,
        String coverUrl,
        String thumbUrl,
        Map<String, Object> extra,
        Integer likeCount,
        Integer favoriteCount,
        Integer collectCount,
        Integer commentCount,
        Integer shareCount,
        Long viewCount,
        String recommendationReason,
        LocalDateTime createdAt
) {
}
