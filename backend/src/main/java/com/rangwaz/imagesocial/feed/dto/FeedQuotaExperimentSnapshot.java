package com.rangwaz.imagesocial.feed.dto;

import java.time.LocalDateTime;

public record FeedQuotaExperimentSnapshot(
        String experimentName,
        int windowDays,
        boolean guardEnabled,
        boolean forceControlByConfig,
        boolean rollbackTriggered,
        String rollbackReason,
        long controlExposure,
        long treatmentExposure,
        long controlClicks,
        long treatmentClicks,
        long controlNegative,
        long treatmentNegative,
        double controlCtr,
        double treatmentCtr,
        double controlNegativeRate,
        double treatmentNegativeRate,
        double ctrRelativeDrop,
        double negativeRelativeLift,
        LocalDateTime generatedAt
) {
}
