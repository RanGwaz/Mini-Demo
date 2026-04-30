package com.rangwaz.imagesocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.recommendation")
public record RecommendationProperties(
        DeepRank deepRank,
        OnlineInterest onlineInterest,
        FeedQuota feedQuota,
        FeedOps feedOps,
        OnlineMetrics onlineMetrics
) {
    public RecommendationProperties {
        deepRank = deepRank == null ? DeepRank.defaults() : deepRank;
        onlineInterest = onlineInterest == null ? OnlineInterest.defaults() : onlineInterest;
        feedQuota = feedQuota == null ? FeedQuota.defaults() : feedQuota;
        feedOps = feedOps == null ? FeedOps.defaults() : feedOps;
        onlineMetrics = onlineMetrics == null ? OnlineMetrics.defaults() : onlineMetrics;
    }

    public record DeepRank(
            boolean enabled,
            String endpoint,
            String recallEndpoint,
            Integer timeoutMs,
            String authToken
    ) {
        static DeepRank defaults() {
            return new DeepRank(
                    true,
                    "http://localhost:18080/infer/rank",
                    "http://localhost:18080/infer/recall",
                    1200,
                    ""
            );
        }
    }

    public record OnlineInterest(
            boolean decayEnabled,
            int decayIntervalMinutes,
            double halfLifeHours,
            int expireDays,
            int zsetMaxSize,
            int fetchWindow,
            double minScore
    ) {
        static OnlineInterest defaults() {
            return new OnlineInterest(
                    true,
                    10,
                    18.0d,
                    3,
                    160,
                    56,
                    0.12d
            );
        }
    }

    public record FeedQuota(
            String experimentName,
            boolean abEnabled,
            double treatmentRatio,
            int minSourceQuota,
            int fetchMultiplier,
            boolean forceControl,
            boolean guardEnabled,
            int guardWindowDays,
            int guardMinExposure,
            double guardMaxCtrDrop,
            double guardMaxNegativeLift,
            int guardCacheSeconds,
            HomeQuota anonymous,
            HomeQuota control,
            HomeQuota treatment,
            SimilarQuota similar
    ) {
        static FeedQuota defaults() {
            return new FeedQuota(
                    "feed_quota_home_v1",
                    true,
                    0.50d,
                    18,
                    2,
                    false,
                    true,
                    3,
                    1200,
                    0.12d,
                    0.25d,
                    90,
                    new HomeQuota(0.62d, 0.00d, 0.00d, 0.00d, 0.00d, 0.00d, 0.00d, 0.38d),
                    new HomeQuota(0.10d, 0.10d, 0.16d, 0.18d, 0.18d, 0.15d, 0.07d, 0.06d),
                    new HomeQuota(0.08d, 0.08d, 0.14d, 0.24d, 0.20d, 0.18d, 0.05d, 0.03d),
                    new SimilarQuota(0.58d, 0.27d, 0.15d)
            );
        }
    }

    public record HomeQuota(
            double hot,
            double social,
            double content,
            double online,
            double recentPositive,
            double vector,
            double explicit,
            double explore
    ) {
    }

    public record SimilarQuota(
            double vector,
            double semantic,
            double fallback
    ) {
    }

    public record OnlineMetrics(
            int defaultWindowDays,
            int maxWindowDays,
            int topSourcesLimit,
            int topExperimentsLimit,
            int attributionHours
    ) {
        static OnlineMetrics defaults() {
            return new OnlineMetrics(
                    3,
                    30,
                    8,
                    6,
                    24
            );
        }
    }

    public record FeedOps(
            int defaultSourceBudgetMs,
            int dbRecallBudgetMs,
            int socialRecallBudgetMs,
            int vectorRecallBudgetMs,
            int snapshotTopSourcesLimit
    ) {
        static FeedOps defaults() {
            return new FeedOps(
                    420,
                    360,
                    260,
                    520,
                    16
            );
        }
    }
}
