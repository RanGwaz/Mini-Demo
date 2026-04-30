package com.rangwaz.imagesocial.feed;

import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.feed.dto.FeedSourceHealthItem;
import com.rangwaz.imagesocial.feed.dto.FeedSourceHealthResponse;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class FeedSourceHealthTrackerService {

    private final RecommendationProperties recommendationProperties;
    private final Map<String, SourceStats> sourceStatsMap = new ConcurrentHashMap<>();
    private final LocalDateTime processStartedAt = LocalDateTime.now();

    public FeedSourceHealthTrackerService(RecommendationProperties recommendationProperties) {
        this.recommendationProperties = recommendationProperties;
    }

    public int resolveBudgetMs(String sourceKey) {
        RecommendationProperties.FeedOps conf = recommendationProperties.feedOps();
        int defaultBudget = conf == null ? 420 : Math.max(1, conf.defaultSourceBudgetMs());
        if (sourceKey == null || sourceKey.isBlank()) {
            return defaultBudget;
        }
        String normalized = sourceKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(".vector.") || normalized.contains("recent-positive")) {
            return conf == null ? 520 : Math.max(1, conf.vectorRecallBudgetMs());
        }
        if (normalized.contains(".social.")) {
            return conf == null ? 260 : Math.max(1, conf.socialRecallBudgetMs());
        }
        return conf == null ? 360 : Math.max(1, conf.dbRecallBudgetMs());
    }

    public void record(String sourceKey, String status, long latencyMs, String message) {
        String normalizedSource = normalizeSource(sourceKey);
        int budgetMs = resolveBudgetMs(normalizedSource);
        boolean overBudget = latencyMs > budgetMs && !"skipped".equalsIgnoreCase(status);
        SourceStats stats = sourceStatsMap.computeIfAbsent(normalizedSource, key -> new SourceStats());
        stats.record(status, latencyMs, budgetMs, overBudget, message);
    }

    public FeedSourceHealthResponse snapshot(String surface, Integer sourceLimit) {
        RecommendationProperties.FeedOps conf = recommendationProperties.feedOps();
        int safeLimit = Math.max(
                1,
                Math.min(
                        32,
                        sourceLimit == null || sourceLimit <= 0
                                ? (conf == null ? 16 : Math.max(1, conf.snapshotTopSourcesLimit()))
                                : sourceLimit
                )
        );
        List<FeedSourceHealthItem> items = sourceStatsMap.entrySet().stream()
                .map(entry -> entry.getValue().toItem(entry.getKey()))
                .sorted(Comparator
                        .comparingLong((FeedSourceHealthItem item) -> item.failedCount() + item.overBudgetCount()).reversed()
                        .thenComparingLong(FeedSourceHealthItem::totalCalls).reversed()
                        .thenComparing(FeedSourceHealthItem::sourceKey))
                .limit(safeLimit)
                .toList();
        return new FeedSourceHealthResponse(
                normalizeSurface(surface),
                processStartedAt,
                sourceStatsMap.size(),
                items,
                LocalDateTime.now()
        );
    }

    private String normalizeSource(String sourceKey) {
        if (sourceKey == null || sourceKey.isBlank()) {
            return "unknown";
        }
        return sourceKey.trim();
    }

    private String normalizeSurface(String surface) {
        if (surface == null || surface.isBlank()) {
            return "home_feed";
        }
        return surface.trim().toLowerCase(Locale.ROOT);
    }

    private static final class SourceStats {
        private long totalCalls;
        private long successCount;
        private long emptyCount;
        private long failedCount;
        private long skippedCount;
        private long overBudgetCount;
        private long totalLatencyMs;
        private long maxLatencyMs;
        private long lastLatencyMs;
        private int latencyBudgetMs;
        private String lastStatus = "unknown";
        private String lastMessage;
        private LocalDateTime lastUpdatedAt = LocalDateTime.now();

        private synchronized void record(String status,
                                         long latencyMs,
                                         int budgetMs,
                                         boolean overBudget,
                                         String message) {
            totalCalls++;
            totalLatencyMs += Math.max(0L, latencyMs);
            maxLatencyMs = Math.max(maxLatencyMs, Math.max(0L, latencyMs));
            lastLatencyMs = Math.max(0L, latencyMs);
            latencyBudgetMs = Math.max(1, budgetMs);
            lastStatus = status == null || status.isBlank() ? "unknown" : status;
            lastMessage = message;
            lastUpdatedAt = LocalDateTime.now();
            if (overBudget) {
                overBudgetCount++;
            }
            if ("success".equalsIgnoreCase(lastStatus)) {
                successCount++;
            } else if ("empty".equalsIgnoreCase(lastStatus)) {
                emptyCount++;
            } else if ("failed".equalsIgnoreCase(lastStatus)) {
                failedCount++;
            } else if ("skipped".equalsIgnoreCase(lastStatus)) {
                skippedCount++;
            }
        }

        private synchronized FeedSourceHealthItem toItem(String sourceKey) {
            double avgLatency = totalCalls <= 0 ? 0.0d : totalLatencyMs / (double) totalCalls;
            return new FeedSourceHealthItem(
                    sourceKey,
                    Math.max(1, latencyBudgetMs),
                    totalCalls,
                    successCount,
                    emptyCount,
                    failedCount,
                    skippedCount,
                    overBudgetCount,
                    avgLatency,
                    maxLatencyMs,
                    lastLatencyMs,
                    lastStatus,
                    lastMessage,
                    lastUpdatedAt
            );
        }
    }
}
