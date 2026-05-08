package com.rangwaz.imagesocial.topic.dto;

import java.math.BigDecimal;

public record TopicView(
        Long id,
        String name,
        String slug,
        String description,
        String coverUrl,
        String status,
        String riskLevel,
        String topicType,
        Integer postCount,
        Integer followerCount,
        BigDecimal hotScore
) {
}
