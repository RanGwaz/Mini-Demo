package com.rangwaz.imagesocial.feed;

import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.domain.mapper.UserEventMapper;
import com.rangwaz.imagesocial.feed.dto.FeedQuotaExperimentSnapshot;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineExperimentRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class FeedQuotaGuardService {

    private static final int DEFAULT_WINDOW_DAYS = 3;
    private static final int DEFAULT_MIN_EXPOSURE = 1200;
    private static final double DEFAULT_MAX_CTR_DROP = 0.12d;
    private static final double DEFAULT_MAX_NEGATIVE_LIFT = 0.25d;
    private static final int DEFAULT_CACHE_SECONDS = 90;
    private static final int DEFAULT_ATTRIBUTION_HOURS = 24;
    private static final int EXPERIMENT_SCAN_LIMIT = 48;

    private final UserEventMapper userEventMapper;
    private final RecommendationProperties recommendationProperties;

    private volatile CachedSnapshot cachedSnapshot;

    public FeedQuotaGuardService(UserEventMapper userEventMapper,
                                 RecommendationProperties recommendationProperties) {
        this.userEventMapper = userEventMapper;
        this.recommendationProperties = recommendationProperties;
    }

    public boolean shouldForceControl(String experimentName) {
        return snapshot(experimentName).rollbackTriggered();
    }

    public FeedQuotaExperimentSnapshot snapshot(String experimentName) {
        String safeExperimentName = normalizeExperimentName(experimentName);
        CachedSnapshot local = cachedSnapshot;
        if (local != null
                && Objects.equals(local.experimentName(), safeExperimentName)
                && local.expiresAt().isAfter(LocalDateTime.now())) {
            return local.snapshot();
        }

        synchronized (this) {
            CachedSnapshot current = cachedSnapshot;
            if (current != null
                    && Objects.equals(current.experimentName(), safeExperimentName)
                    && current.expiresAt().isAfter(LocalDateTime.now())) {
                return current.snapshot();
            }
            FeedQuotaExperimentSnapshot refreshed = computeSnapshot(safeExperimentName);
            int cacheSeconds = cacheSeconds();
            cachedSnapshot = new CachedSnapshot(
                    safeExperimentName,
                    refreshed,
                    LocalDateTime.now().plusSeconds(Math.max(8, cacheSeconds))
            );
            return refreshed;
        }
    }

    private FeedQuotaExperimentSnapshot computeSnapshot(String experimentName) {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        boolean forceControlByConfig = conf != null && conf.forceControl();
        boolean guardEnabled = conf == null || conf.guardEnabled();
        int windowDays = conf == null || conf.guardWindowDays() <= 0
                ? DEFAULT_WINDOW_DAYS
                : conf.guardWindowDays();
        int minExposure = conf == null || conf.guardMinExposure() <= 0
                ? DEFAULT_MIN_EXPOSURE
                : conf.guardMinExposure();
        double maxCtrDrop = conf == null || conf.guardMaxCtrDrop() <= 0.0d
                ? DEFAULT_MAX_CTR_DROP
                : conf.guardMaxCtrDrop();
        double maxNegativeLift = conf == null || conf.guardMaxNegativeLift() <= 0.0d
                ? DEFAULT_MAX_NEGATIVE_LIFT
                : conf.guardMaxNegativeLift();

        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = toTime.minusDays(Math.max(1, windowDays));
        int attributionHours = recommendationProperties.onlineMetrics() == null
                ? DEFAULT_ATTRIBUTION_HOURS
                : Math.max(1, recommendationProperties.onlineMetrics().attributionHours());

        List<FeedOnlineExperimentRow> rows = userEventMapper.selectFeedOnlineExperimentRows(
                fromTime,
                toTime,
                "home_feed",
                null,
                attributionHours,
                EXPERIMENT_SCAN_LIMIT
        );

        ExperimentBucket control = new ExperimentBucket();
        ExperimentBucket treatment = new ExperimentBucket();
        String prefix = experimentName + ":";

        for (FeedOnlineExperimentRow row : rows) {
            String rawId = row == null ? null : row.getExperimentId();
            if (rawId == null || rawId.isBlank()) {
                continue;
            }
            String normalizedId = rawId.trim().toLowerCase(Locale.ROOT);
            if (!normalizedId.startsWith(prefix.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String bucket = normalizedId.substring(prefix.length());
            if (bucket.contains("treatment")) {
                treatment.add(row);
            } else if (bucket.contains("control")) {
                control.add(row);
            }
        }

        double controlCtr = safeRate(control.clicks, control.exposure);
        double treatmentCtr = safeRate(treatment.clicks, treatment.exposure);
        double controlNegativeRate = safeRate(control.negative, control.exposure);
        double treatmentNegativeRate = safeRate(treatment.negative, treatment.exposure);

        double ctrDrop = controlCtr <= 1e-9
                ? 0.0d
                : Math.max(0.0d, (controlCtr - treatmentCtr) / controlCtr);
        double negativeLift = controlNegativeRate <= 1e-9
                ? (treatmentNegativeRate > 1e-9 ? 1.0d : 0.0d)
                : Math.max(0.0d, (treatmentNegativeRate - controlNegativeRate) / controlNegativeRate);

        boolean rollbackTriggered;
        String rollbackReason;
        if (forceControlByConfig) {
            rollbackTriggered = true;
            rollbackReason = "force_control_config";
        } else if (!guardEnabled) {
            rollbackTriggered = false;
            rollbackReason = "guard_disabled";
        } else if (control.exposure < minExposure || treatment.exposure < minExposure) {
            rollbackTriggered = false;
            rollbackReason = "insufficient_exposure";
        } else if (ctrDrop > maxCtrDrop && negativeLift > maxNegativeLift) {
            rollbackTriggered = true;
            rollbackReason = "ctr_drop_and_negative_lift";
        } else if (ctrDrop > maxCtrDrop) {
            rollbackTriggered = true;
            rollbackReason = "ctr_drop";
        } else if (negativeLift > maxNegativeLift) {
            rollbackTriggered = true;
            rollbackReason = "negative_lift";
        } else {
            rollbackTriggered = false;
            rollbackReason = "healthy";
        }

        return new FeedQuotaExperimentSnapshot(
                experimentName,
                Math.max(1, windowDays),
                guardEnabled,
                forceControlByConfig,
                rollbackTriggered,
                rollbackReason,
                control.exposure,
                treatment.exposure,
                control.clicks,
                treatment.clicks,
                control.negative,
                treatment.negative,
                controlCtr,
                treatmentCtr,
                controlNegativeRate,
                treatmentNegativeRate,
                ctrDrop,
                negativeLift,
                LocalDateTime.now()
        );
    }

    private int cacheSeconds() {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        if (conf == null || conf.guardCacheSeconds() <= 0) {
            return DEFAULT_CACHE_SECONDS;
        }
        return conf.guardCacheSeconds();
    }

    private double safeRate(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return 0.0d;
        }
        return numerator / (double) denominator;
    }

    private String normalizeExperimentName(String experimentName) {
        if (experimentName == null || experimentName.isBlank()) {
            RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
            if (conf != null && conf.experimentName() != null && !conf.experimentName().isBlank()) {
                return conf.experimentName().trim();
            }
            return "feed_quota_home_v1";
        }
        return experimentName.trim();
    }

    private record CachedSnapshot(String experimentName,
                                  FeedQuotaExperimentSnapshot snapshot,
                                  LocalDateTime expiresAt) {
    }

    private static final class ExperimentBucket {
        long exposure;
        long clicks;
        long negative;

        void add(FeedOnlineExperimentRow row) {
            if (row == null) {
                return;
            }
            exposure += Math.max(0L, row.getExposureCount());
            clicks += Math.max(0L, row.getClickThroughCount());
            negative += Math.max(0L, row.getNegativeThroughCount());
        }
    }
}
