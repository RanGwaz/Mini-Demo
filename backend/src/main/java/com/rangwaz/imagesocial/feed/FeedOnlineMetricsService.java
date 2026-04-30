package com.rangwaz.imagesocial.feed;

import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.domain.mapper.UserEventMapper;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineExperimentMetric;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineMetricsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineSummarySnapshot;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineSourceMetric;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineSummary;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineExperimentRow;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineSourceRow;
import com.rangwaz.imagesocial.feed.metrics.FeedOnlineSummaryRow;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class FeedOnlineMetricsService {

    private static final int DEFAULT_WINDOW_DAYS = 3;
    private static final int DEFAULT_MAX_WINDOW_DAYS = 30;
    private static final int DEFAULT_TOP_SOURCES = 8;
    private static final int DEFAULT_TOP_EXPERIMENTS = 6;
    private static final int DEFAULT_ATTRIBUTION_HOURS = 24;

    private final UserEventMapper userEventMapper;
    private final RecommendationProperties recommendationProperties;

    public FeedOnlineMetricsService(UserEventMapper userEventMapper,
                                    RecommendationProperties recommendationProperties) {
        this.userEventMapper = userEventMapper;
        this.recommendationProperties = recommendationProperties;
    }

    public FeedOnlineMetricsResponse fetchOnlineMetrics(Long userId,
                                                        Integer days,
                                                        String surface,
                                                        Integer sourceLimit,
                                                        Integer experimentLimit) {
        RecommendationProperties.OnlineMetrics conf = recommendationProperties.onlineMetrics();
        int maxDays = conf == null ? DEFAULT_MAX_WINDOW_DAYS : Math.max(1, conf.maxWindowDays());
        int defaultDays = conf == null ? DEFAULT_WINDOW_DAYS : Math.max(1, conf.defaultWindowDays());
        int safeDays = Math.max(1, Math.min(maxDays, days == null ? defaultDays : days));
        int safeSourceLimit = Math.max(1, Math.min(
                24,
                sourceLimit == null || sourceLimit <= 0
                        ? (conf == null ? DEFAULT_TOP_SOURCES : Math.max(1, conf.topSourcesLimit()))
                        : sourceLimit
        ));
        int safeExperimentLimit = Math.max(1, Math.min(
                20,
                experimentLimit == null || experimentLimit <= 0
                        ? (conf == null ? DEFAULT_TOP_EXPERIMENTS : Math.max(1, conf.topExperimentsLimit()))
                        : experimentLimit
        ));
        int attributionHours = conf == null
                ? DEFAULT_ATTRIBUTION_HOURS
                : Math.max(1, conf.attributionHours());

        String normalizedSurface = normalizeSurface(surface);
        String surfaceFilter = "all".equals(normalizedSurface) ? null : normalizedSurface;
        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = toTime.minusDays(safeDays);

        FeedOnlineSummaryRow summaryRow = userEventMapper.selectFeedOnlineSummary(fromTime, toTime, surfaceFilter, userId);
        if (summaryRow == null) {
            summaryRow = new FeedOnlineSummaryRow();
        }
        long exposureCount = Math.max(0L, summaryRow.getExposureCount());
        long clickCount = Math.max(0L, summaryRow.getClickCount());
        long detailViewCount = Math.max(0L, summaryRow.getDetailViewCount());
        long likeCount = Math.max(0L, summaryRow.getLikeCount());
        long favoriteCount = Math.max(0L, summaryRow.getFavoriteCount());
        long commentCount = Math.max(0L, summaryRow.getCommentCount());
        long shareCount = Math.max(0L, summaryRow.getShareCount());
        long negativeCount = Math.max(0L, summaryRow.getNegativeCount());
        long requestUv = Math.max(0L, summaryRow.getRequestUv());
        long exposureUserUv = Math.max(0L, summaryRow.getExposureUserUv());
        double avgDwellMs = summaryRow.getAvgDwellMs() == null ? 0.0d : summaryRow.getAvgDwellMs();

        FeedOnlineSummary summary = new FeedOnlineSummary(
                exposureCount,
                clickCount,
                detailViewCount,
                likeCount,
                favoriteCount,
                commentCount,
                shareCount,
                negativeCount,
                requestUv,
                exposureUserUv,
                safeRate(clickCount, exposureCount),
                safeRate(detailViewCount, exposureCount),
                safeRate(likeCount, exposureCount),
                safeRate(favoriteCount, exposureCount),
                safeRate(negativeCount, exposureCount),
                avgDwellMs
        );

        List<FeedOnlineSourceMetric> sourceMetrics = userEventMapper
                .selectFeedOnlineSourceRows(fromTime, toTime, surfaceFilter, userId, attributionHours, safeSourceLimit)
                .stream()
                .map(row -> new FeedOnlineSourceMetric(
                        normalizeSource(row.getRecallSource()),
                        Math.max(0L, row.getExposureCount()),
                        Math.max(0L, row.getClickThroughCount()),
                        Math.max(0L, row.getDetailThroughCount()),
                        Math.max(0L, row.getLikeThroughCount()),
                        Math.max(0L, row.getFavoriteThroughCount()),
                        Math.max(0L, row.getNegativeThroughCount()),
                        safeRate(row.getClickThroughCount(), row.getExposureCount()),
                        safeRate(row.getDetailThroughCount(), row.getExposureCount()),
                        safeRate(row.getLikeThroughCount(), row.getExposureCount()),
                        safeRate(row.getFavoriteThroughCount(), row.getExposureCount()),
                        safeRate(row.getNegativeThroughCount(), row.getExposureCount())
                ))
                .toList();

        List<FeedOnlineExperimentMetric> experimentMetrics = userEventMapper
                .selectFeedOnlineExperimentRows(fromTime, toTime, surfaceFilter, userId, attributionHours, safeExperimentLimit)
                .stream()
                .map(row -> new FeedOnlineExperimentMetric(
                        normalizeExperiment(row.getExperimentId()),
                        parseBucket(row.getExperimentId()),
                        Math.max(0L, row.getExposureCount()),
                        Math.max(0L, row.getClickThroughCount()),
                        Math.max(0L, row.getDetailThroughCount()),
                        Math.max(0L, row.getLikeThroughCount()),
                        Math.max(0L, row.getFavoriteThroughCount()),
                        safeRate(row.getClickThroughCount(), row.getExposureCount()),
                        safeRate(row.getDetailThroughCount(), row.getExposureCount()),
                        safeRate(row.getLikeThroughCount(), row.getExposureCount()),
                        safeRate(row.getFavoriteThroughCount(), row.getExposureCount())
                ))
                .toList();

        return new FeedOnlineMetricsResponse(
                userId == null ? "global" : "mine",
                normalizedSurface,
                safeDays,
                fromTime,
                toTime,
                summary,
                sourceMetrics,
                experimentMetrics,
                LocalDateTime.now()
        );
    }

    public FeedOnlineSummarySnapshot fetchOnlineSummary(Long userId,
                                                        Integer days,
                                                        String surface) {
        Window window = resolveWindow(days, surface);
        FeedOnlineSummaryRow summaryRow = userEventMapper.selectFeedOnlineSummary(
                window.fromTime(),
                window.toTime(),
                window.surfaceFilter(),
                userId
        );
        if (summaryRow == null) {
            summaryRow = new FeedOnlineSummaryRow();
        }
        long exposureCount = Math.max(0L, summaryRow.getExposureCount());
        long clickCount = Math.max(0L, summaryRow.getClickCount());
        long detailViewCount = Math.max(0L, summaryRow.getDetailViewCount());
        long likeCount = Math.max(0L, summaryRow.getLikeCount());
        long favoriteCount = Math.max(0L, summaryRow.getFavoriteCount());
        long commentCount = Math.max(0L, summaryRow.getCommentCount());
        long shareCount = Math.max(0L, summaryRow.getShareCount());
        long negativeCount = Math.max(0L, summaryRow.getNegativeCount());
        long requestUv = Math.max(0L, summaryRow.getRequestUv());
        long exposureUserUv = Math.max(0L, summaryRow.getExposureUserUv());
        double avgDwellMs = summaryRow.getAvgDwellMs() == null ? 0.0d : summaryRow.getAvgDwellMs();

        FeedOnlineSummary summary = new FeedOnlineSummary(
                exposureCount,
                clickCount,
                detailViewCount,
                likeCount,
                favoriteCount,
                commentCount,
                shareCount,
                negativeCount,
                requestUv,
                exposureUserUv,
                safeRate(clickCount, exposureCount),
                safeRate(detailViewCount, exposureCount),
                safeRate(likeCount, exposureCount),
                safeRate(favoriteCount, exposureCount),
                safeRate(negativeCount, exposureCount),
                avgDwellMs
        );

        return new FeedOnlineSummarySnapshot(
                userId == null ? "global" : "mine",
                window.normalizedSurface(),
                window.safeDays(),
                window.fromTime(),
                window.toTime(),
                summary,
                LocalDateTime.now()
        );
    }

    private Window resolveWindow(Integer days, String surface) {
        RecommendationProperties.OnlineMetrics conf = recommendationProperties.onlineMetrics();
        int maxDays = conf == null ? DEFAULT_MAX_WINDOW_DAYS : Math.max(1, conf.maxWindowDays());
        int defaultDays = conf == null ? DEFAULT_WINDOW_DAYS : Math.max(1, conf.defaultWindowDays());
        int safeDays = Math.max(1, Math.min(maxDays, days == null ? defaultDays : days));
        String normalizedSurface = normalizeSurface(surface);
        String surfaceFilter = "all".equals(normalizedSurface) ? null : normalizedSurface;
        LocalDateTime toTime = LocalDateTime.now();
        LocalDateTime fromTime = toTime.minusDays(safeDays);
        return new Window(safeDays, normalizedSurface, surfaceFilter, fromTime, toTime);
    }

    private String normalizeSurface(String surface) {
        if (surface == null || surface.isBlank()) {
            return "home_feed";
        }
        String normalized = surface.trim().toLowerCase(Locale.ROOT);
        if ("*".equals(normalized) || "all".equals(normalized)) {
            return "all";
        }
        return normalized;
    }

    private String normalizeSource(String recallSource) {
        if (recallSource == null || recallSource.isBlank()) {
            return "unknown";
        }
        return recallSource.trim();
    }

    private String normalizeExperiment(String experimentId) {
        if (experimentId == null || experimentId.isBlank()) {
            return "no_exp";
        }
        return experimentId.trim();
    }

    private String parseBucket(String experimentId) {
        if (experimentId == null || experimentId.isBlank()) {
            return "none";
        }
        String normalized = experimentId.trim();
        int idx = normalized.lastIndexOf(':');
        if (idx >= 0 && idx + 1 < normalized.length()) {
            return normalized.substring(idx + 1);
        }
        return "default";
    }

    private double safeRate(long numerator, long denominator) {
        if (denominator <= 0L || numerator <= 0L) {
            return 0.0d;
        }
        return numerator / (double) denominator;
    }

    private record Window(int safeDays,
                          String normalizedSurface,
                          String surfaceFilter,
                          LocalDateTime fromTime,
                          LocalDateTime toTime) {
    }
}
