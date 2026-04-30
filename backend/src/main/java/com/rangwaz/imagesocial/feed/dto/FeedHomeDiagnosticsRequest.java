package com.rangwaz.imagesocial.feed.dto;

import java.util.List;

public record FeedHomeDiagnosticsRequest(
        Long userId,
        String surface,
        int page,
        int size,
        String requestId,
        boolean personalized,
        boolean lightPagingMode,
        int recallLimit,
        int recallMultiplier,
        String experimentId,
        String quotaBucket,
        int minSourceQuota,
        int fetchMultiplier,
        int explicitInterestTermCount,
        List<String> explicitInterestSample
) {
}
