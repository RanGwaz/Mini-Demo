package com.rangwaz.imagesocial.feed.service.impl;

import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.feed.DeepRankingService;
import com.rangwaz.imagesocial.feed.FallbackTopicFacetRow;
import com.rangwaz.imagesocial.feed.FeedFilterService;
import com.rangwaz.imagesocial.feed.FeedQuotaGuardService;
import com.rangwaz.imagesocial.feed.FeedObservabilityService;
import com.rangwaz.imagesocial.feed.FeedRecallService;
import com.rangwaz.imagesocial.feed.FeedSourceHealthTrackerService;
import com.rangwaz.imagesocial.feed.RankedPost;
import com.rangwaz.imagesocial.feed.TopicFacetRow;
import com.rangwaz.imagesocial.feed.VectorRecallService;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsFilters;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsItem;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsReasonMetric;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsRequest;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedHomeSnapshotResponse;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsSource;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsStage;
import com.rangwaz.imagesocial.feed.service.FeedService;
import com.rangwaz.imagesocial.feature.FeatureService;
import com.rangwaz.imagesocial.feature.PostRealtimeMetrics;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.user.UserInterestService;
import com.rangwaz.imagesocial.user.UserService;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FeedServiceImpl implements FeedService {
    private static final Logger log = LoggerFactory.getLogger(FeedServiceImpl.class);
    private static final Pattern TERM_SPLITTER = Pattern.compile("[,|/\\\\>\\s_-]+");

    private static final int MAX_FEED_WINDOW = 1000;
    private static final int MAX_DEEP_RANK_CANDIDATES_FIRST_PAGE = 96;
    private static final int MAX_DEEP_RANK_CANDIDATES_LATER_PAGE = 60;
    private static final int MAX_DEEP_RANK_PAGE = 4;
    private static final int MAX_SYNC_EXPOSURE_EVENTS_PER_PAGE = 4;
    private static final int MIN_RECALL_CANDIDATES = 120;
    private static final int MAX_RECALL_CANDIDATES = 360;
    private static final int MIN_SIMILAR_RECALL_CANDIDATES = 120;
    private static final int MAX_SIMILAR_RECALL_CANDIDATES = 320;
    private static final int SHORT_TERM_EXPOSURE_LIMIT = 96;
    private static final int STRONG_SEEN_LIMIT = 48;
    private static final int SOFT_SEEN_LIMIT = 180;
    private static final int MIN_FRESH_FEED_WINDOW = 96;
    private static final int MAX_AUTHOR_DUPLICATES_IN_CANDIDATES = 6;
    private static final int HOME_AUTHOR_CAP = 2;
    private static final int HOME_TOPIC_CAP = 3;
    private static final int SIMILAR_AUTHOR_CAP = 3;
    private static final int SIMILAR_TOPIC_CAP = 4;
    private static final int HOME_FRESHNESS_INTERVAL = 7;
    private static final int SIMILAR_FRESHNESS_INTERVAL = 6;
    private static final int HOME_CAP_WINDOW = 72;
    private static final int SIMILAR_CAP_WINDOW = 120;
    private static final int FRESH_PICK_SEARCH_WINDOW = 48;
    private static final int SIMILARITY_CONTEXT_WINDOW = 6;
    private static final int EXPLICIT_INTEREST_MATCH_BONUS = 16;
    private static final int RECENT_SIGNAL_MATCH_BONUS = 18;
    private static final int HOME_MIN_SOURCE_QUOTA = 18;
    private static final int HOME_FETCH_MULTIPLIER = 2;
    private static final int RECENT_SIGNAL_QUOTA_BONUS = 20;
    private static final int VECTOR_SIGNAL_QUOTA_BONUS = 12;
    private static final int ONLINE_SIGNAL_QUOTA_BONUS = 8;
    private static final int MAX_RECENT_SIGNAL_MATCHES = 4;
    private static final double MMR_LAMBDA_DEFAULT = 0.52d;
    private static final double MMR_LAMBDA_EXPLORE = 0.35d;
    private static final double MMR_LAMBDA_SIMILAR = 0.68d;
    private static final String DEFAULT_HOME_QUOTA_EXPERIMENT = "feed_quota_home_v1";
    private static final String AB_BUCKET_SALT = "feed-quota-home-ab";
    private static final double HOME_HOT_RATIO = 0.10d;
    private static final double HOME_SOCIAL_RATIO = 0.10d;
    private static final double HOME_CONTENT_RATIO = 0.16d;
    private static final double HOME_ONLINE_RATIO = 0.18d;
    private static final double HOME_RECENT_POS_RATIO = 0.18d;
    private static final double HOME_VECTOR_RATIO = 0.15d;
    private static final double HOME_EXPLICIT_RATIO = 0.07d;
    private static final double HOME_EXPLORE_RATIO = 0.06d;
    private static final double ANON_HOT_RATIO = 0.62d;
    private static final double ANON_EXPLORE_RATIO = 0.38d;
    private static final double SIMILAR_VECTOR_RATIO = 0.58d;
    private static final double SIMILAR_SEMANTIC_RATIO = 0.27d;
    private static final double SIMILAR_FALLBACK_RATIO = 0.15d;

    private final FeedRecallService recallService;
    private final FeedFilterService filterService;
    private final PostService postService;
    private final UserService userService;
    private final EventService eventService;
    private final DeepRankingService deepRankingService;
    private final VectorRecallService vectorRecallService;
    private final FeatureService featureService;
    private final PostMapper postMapper;
    private final UserInterestService userInterestService;
    private final RecommendationProperties recommendationProperties;
    private final FeedQuotaGuardService feedQuotaGuardService;
    private final FeedObservabilityService feedObservabilityService;
    private final FeedSourceHealthTrackerService feedSourceHealthTrackerService;

    @Autowired
    public FeedServiceImpl(FeedRecallService recallService,
                           FeedFilterService filterService,
                           PostService postService,
                           UserService userService,
                           EventService eventService,
                           DeepRankingService deepRankingService,
                           VectorRecallService vectorRecallService,
                           FeatureService featureService,
                           PostMapper postMapper,
                           UserInterestService userInterestService,
                           RecommendationProperties recommendationProperties,
                           FeedQuotaGuardService feedQuotaGuardService,
                           FeedObservabilityService feedObservabilityService,
                           FeedSourceHealthTrackerService feedSourceHealthTrackerService) {
        this.recallService = recallService;
        this.filterService = filterService;
        this.postService = postService;
        this.userService = userService;
        this.eventService = eventService;
        this.deepRankingService = deepRankingService;
        this.vectorRecallService = vectorRecallService;
        this.featureService = featureService;
        this.postMapper = postMapper;
        this.userInterestService = userInterestService;
        this.recommendationProperties = recommendationProperties;
        this.feedQuotaGuardService = feedQuotaGuardService;
        this.feedObservabilityService = feedObservabilityService;
        this.feedSourceHealthTrackerService = feedSourceHealthTrackerService;
    }

    public PageResponse<PostView> homeFeed(Long currentUserId,
                                           int page,
                                           int size,
                                           String seed,
                                           String topicFilter,
                                           String styleFilter,
                                           String tagFilter) {
        return computeHomeFeed(currentUserId, page, size, seed, topicFilter, styleFilter, tagFilter, true, false)
                .pageResponse();
    }

    public FeedHomeDiagnosticsResponse homeFeedDiagnostics(Long currentUserId,
                                                           int page,
                                                           int size,
                                                           String seed,
                                                           String topicFilter,
                                                           String styleFilter,
                                                           String tagFilter) {
        return computeHomeFeed(currentUserId, page, size, seed, topicFilter, styleFilter, tagFilter, false, true)
                .diagnostics();
    }

    public FeedHomeSnapshotResponse homeFeedSnapshot(Long currentUserId,
                                                     int page,
                                                     int size,
                                                     String seed,
                                                     String topicFilter,
                                                     String styleFilter,
                                                     String tagFilter) {
        HomeFeedComputation computation = computeHomeFeed(
                currentUserId,
                page,
                size,
                seed,
                topicFilter,
                styleFilter,
                tagFilter,
                false,
                true
        );
        return new FeedHomeSnapshotResponse(computation.pageResponse(), computation.diagnostics());
    }

    private HomeFeedComputation computeHomeFeed(Long currentUserId,
                                                int page,
                                                int size,
                                                String seed,
                                                String topicFilter,
                                                String styleFilter,
                                                String tagFilter,
                                                boolean publishExposure,
                                                boolean includeDiagnostics) {
        long traceStartedAt = System.nanoTime();
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int recallMultiplier = safePage <= 1 ? 3 : 2;
        int recallLimit = Math.min(
                Math.max(safePage * safeSize * recallMultiplier, MIN_RECALL_CANDIDATES),
                MAX_RECALL_CANDIDATES
        );
        boolean lightPagingMode = safePage > 4;
        String effectiveSeed = normalizeSeed(seed);
        FeedSemanticFilter semanticFilter = buildSemanticFilter(topicFilter, styleFilter, tagFilter);
        Set<String> explicitInterestTerms = loadExplicitInterestTerms(currentUserId);
        HomeQuotaExperiment quotaExperiment = resolveHomeQuotaExperiment(currentUserId);
        HomeQuotaRatios quotaRatios = quotaExperiment.ratios();
        int minSourceQuota = homeMinSourceQuota();
        int fetchMultiplier = homeFetchMultiplier();
        HomeFeedDiagnosticsCollector diagnostics = includeDiagnostics
                ? new HomeFeedDiagnosticsCollector(
                currentUserId,
                safePage,
                safeSize,
                effectiveSeed,
                currentUserId != null,
                lightPagingMode,
                recallLimit,
                recallMultiplier,
                quotaExperiment,
                minSourceQuota,
                fetchMultiplier,
                feedSourceHealthTrackerService,
                explicitInterestTerms,
                semanticFilter
        )
                : null;

        Map<Long, RankedPost> merged = new LinkedHashMap<>();
        if (currentUserId == null) {
            int hotQuota = Math.max(minSourceQuota, quotaOf(recallLimit, quotaRatios.hot()));
            int exploreQuota = Math.max(minSourceQuota, quotaOf(recallLimit, quotaRatios.explore()));
            int hotFetchSize = Math.max(hotQuota * fetchMultiplier, hotQuota);
            int exploreFetchSize = Math.max(exploreQuota * fetchMultiplier, exploreQuota);
            recallAndMergeWithQuota(
                    merged,
                    diagnostics,
                    "home.hot.anonymous",
                    "Hot now",
                    60,
                    hotFetchSize,
                    hotQuota,
                    () -> recallService.recallHot(hotFetchSize, effectiveSeed)
            );
            recallAndMergeWithQuota(
                    merged,
                    diagnostics,
                    "home.explore.anonymous",
                    "Explore something new",
                    50,
                    exploreFetchSize,
                    exploreQuota,
                    () -> recallService.recallExplore(null, exploreFetchSize, effectiveSeed)
            );
        } else {
            List<Long> strongPositiveSignals = featureService.getUserStrongPositiveSignals(currentUserId);
            boolean hasStrongRecentSignals = !strongPositiveSignals.isEmpty();
            Set<String> recentSignalTerms = hasStrongRecentSignals
                    ? loadRecentSignalTerms(strongPositiveSignals)
                    : Set.of();
            List<String> onlineInterestTerms = featureService.getOnlineInterestTerms(currentUserId, 16);
            boolean hasOnlineInterestTerms = !onlineInterestTerms.isEmpty();
            boolean hasExplicitInterestTerms = !explicitInterestTerms.isEmpty();
            int fallbackSourceQuota = Math.max(4, minSourceQuota / 3);
            int hotQuota = Math.max(fallbackSourceQuota, quotaOf(recallLimit, quotaRatios.hot()));
            int socialQuota = Math.max(minSourceQuota, quotaOf(recallLimit, quotaRatios.social()));
            int contentQuota = Math.max(minSourceQuota, quotaOf(recallLimit, quotaRatios.content()));
            int onlineQuota = Math.max(
                    minSourceQuota + ((hasStrongRecentSignals || hasOnlineInterestTerms) ? ONLINE_SIGNAL_QUOTA_BONUS : 0),
                    quotaOf(recallLimit, quotaRatios.online())
            );
            int recentPositiveQuota = Math.max(
                    minSourceQuota + (hasStrongRecentSignals ? RECENT_SIGNAL_QUOTA_BONUS : 0),
                    quotaOf(recallLimit, quotaRatios.recentPositive())
            );
            int i2iQuota = Math.max(
                    minSourceQuota + (hasStrongRecentSignals ? RECENT_SIGNAL_QUOTA_BONUS / 2 : 0),
                    quotaOf(recallLimit, quotaRatios.recentPositive() * 0.80d)
            );
            int vectorQuota = Math.max(
                    minSourceQuota + (hasStrongRecentSignals ? VECTOR_SIGNAL_QUOTA_BONUS : 0),
                    quotaOf(recallLimit, quotaRatios.vector())
            );
            int explicitQuota = Math.max(minSourceQuota, quotaOf(recallLimit, quotaRatios.explicit()));
            int exploreQuota = Math.max(fallbackSourceQuota, quotaOf(recallLimit, quotaRatios.explore()));

            int hotFetchSize = Math.max(hotQuota * fetchMultiplier, hotQuota);
            int socialFetchSize = Math.max(socialQuota * fetchMultiplier, socialQuota);
            int contentFetchSize = Math.max(contentQuota * fetchMultiplier, contentQuota);
            int onlineFetchSize = Math.max(onlineQuota * fetchMultiplier, onlineQuota);
            int recentPositiveFetchSize = Math.max(recentPositiveQuota * fetchMultiplier, recentPositiveQuota);
            int i2iFetchSize = Math.max(i2iQuota * fetchMultiplier, i2iQuota);
            int vectorFetchSize = Math.max(vectorQuota * fetchMultiplier, vectorQuota);
            int explicitFetchSize = Math.max(explicitQuota * fetchMultiplier, explicitQuota);
            int exploreFetchSize = Math.max(exploreQuota * fetchMultiplier, exploreQuota);

            recallAndMergeWithQuota(
                    merged,
                    diagnostics,
                    "home.hot.personalized",
                    "Hot now",
                    60,
                    hotFetchSize,
                    hotQuota,
                    () -> recallService.recallHot(hotFetchSize, effectiveSeed)
            );
            recallAndMergeWithQuota(
                    merged,
                    diagnostics,
                    "home.social.personalized",
                    "From creators you follow",
                    90,
                    socialFetchSize,
                    socialQuota,
                    () -> recallService.recallSocial(currentUserId, socialFetchSize)
            );
            if (!lightPagingMode && hasStrongRecentSignals) {
                recallAndMergeWithQuota(
                        merged,
                        diagnostics,
                        "home.recent-positive.personalized",
                        "More like what you just engaged with",
                        118,
                        recentPositiveFetchSize,
                        recentPositiveQuota,
                        () -> recallService.recallByRecentPositiveFeedback(currentUserId, recentPositiveFetchSize)
                );
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.recent-positive.personalized",
                        "More like what you just engaged with",
                        118,
                        recentPositiveFetchSize,
                        recentPositiveQuota,
                        lightPagingMode ? "light_paging_mode" : "no_recent_positive_signals"
                );
            }
            if (!lightPagingMode && hasStrongRecentSignals) {
                recallAndMergeWithQuota(
                        merged,
                        diagnostics,
                        "home.i2i.personalized",
                        "People with similar taste also liked",
                        116,
                        i2iFetchSize,
                        i2iQuota,
                        () -> recallService.recallByI2I(currentUserId, i2iFetchSize)
                );
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.i2i.personalized",
                        "People with similar taste also liked",
                        116,
                        i2iFetchSize,
                        i2iQuota,
                        lightPagingMode ? "light_paging_mode" : "no_recent_positive_signals"
                );
            }
            if (!lightPagingMode) {
                recallAndMergeWithQuota(
                        merged,
                        diagnostics,
                        "home.vector.personalized",
                        hasStrongRecentSignals ? "Aligned with your current taste" : "Matches your visual taste",
                        hasStrongRecentSignals ? 108 : 96,
                        vectorFetchSize,
                        vectorQuota,
                        () -> recallService.recallByVector(currentUserId, vectorFetchSize)
                );
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.vector.personalized",
                        "Matches your visual taste",
                        96,
                        vectorFetchSize,
                        vectorQuota,
                        "light_paging_mode"
                );
            }
            if (!lightPagingMode) {
                recallAndMergeWithQuota(
                        merged,
                        diagnostics,
                        "home.content.personalized",
                        "Matches your interests",
                        80,
                        contentFetchSize,
                        contentQuota,
                        () -> recallService.recallByContent(currentUserId, contentFetchSize)
                );
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.content.personalized",
                        "Matches your interests",
                        80,
                        contentFetchSize,
                        contentQuota,
                        "light_paging_mode"
                );
            }
            if (!lightPagingMode) {
                boolean shouldRunOnlineRecall = hasOnlineInterestTerms
                        && (hasStrongRecentSignals || needsMorePersonalizedRecall(merged, recallLimit, safePage, safeSize));
                if (shouldRunOnlineRecall) {
                    recallAndMergeWithQuota(
                            merged,
                            diagnostics,
                            "home.online.personalized",
                            "Fits your latest actions",
                            hasStrongRecentSignals ? 110 : 102,
                            onlineFetchSize,
                            onlineQuota,
                            () -> recallService.recallByOnlineProfile(currentUserId, onlineFetchSize)
                    );
                } else {
                    recordSkippedSource(
                            diagnostics,
                            "home.online.personalized",
                            "Fits your latest actions",
                            hasStrongRecentSignals ? 110 : 102,
                            onlineFetchSize,
                            onlineQuota,
                            hasOnlineInterestTerms ? "enough_primary_candidates" : "no_online_interest_terms"
                    );
                }
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.online.personalized",
                        "Fits your latest actions",
                        98,
                        onlineFetchSize,
                        onlineQuota,
                        "light_paging_mode"
                );
            }
            if (!lightPagingMode) {
                boolean shouldRunExplicitRecall = hasExplicitInterestTerms
                        && (hasStrongRecentSignals || needsMorePersonalizedRecall(merged, recallLimit, safePage, safeSize));
                if (shouldRunExplicitRecall) {
                    recallAndMergeWithQuota(
                            merged,
                            diagnostics,
                            "home.explicit.personalized",
                            "From your subscribed topics",
                            105,
                            explicitFetchSize,
                            explicitQuota,
                            () -> recallService.recallByExplicitInterests(currentUserId, explicitFetchSize)
                    );
                } else {
                    recordSkippedSource(
                            diagnostics,
                            "home.explicit.personalized",
                            "From your subscribed topics",
                            105,
                            explicitFetchSize,
                            explicitQuota,
                            hasExplicitInterestTerms ? "enough_primary_candidates" : "no_explicit_interest_terms"
                    );
                }
            } else {
                recordSkippedSource(
                        diagnostics,
                        "home.explicit.personalized",
                        "From your subscribed topics",
                        105,
                        explicitFetchSize,
                        explicitQuota,
                        "light_paging_mode"
                );
            }
            recallAndMergeWithQuota(
                    merged,
                    diagnostics,
                    "home.explore.personalized",
                    "Explore something new",
                    50,
                    exploreFetchSize,
                    exploreQuota,
                    () -> recallService.recallExplore(currentUserId, exploreFetchSize, effectiveSeed)
            );
            int recentSignalBoostedItems = boostByRecentSignals(merged, recentSignalTerms);
            if (diagnostics != null && recentSignalBoostedItems > 0) {
                log.debug("home recent signal boost applied, boostedItems={}, userId={}", recentSignalBoostedItems, currentUserId);
            }
        }
        if (diagnostics != null) {
            diagnostics.recordMergedAfterPrimarySources(merged.size());
        }
        boolean needFallback = merged.size() < Math.min(recallLimit, safePage * safeSize * 2);
        if (needFallback) {
            recallAndMerge(
                    merged,
                    diagnostics,
                    "home.fallback.hot",
                    "Hot now",
                    30,
                    recallLimit,
                    () -> recallService.recallHot(recallLimit, effectiveSeed)
            );
            recallAndMerge(
                    merged,
                    diagnostics,
                    "home.fallback.explore",
                    "Explore something new",
                    26,
                    recallLimit,
                    () -> recallService.recallExplore(currentUserId, recallLimit, effectiveSeed)
            );
        } else {
            recordSkippedSource(diagnostics, "home.fallback.hot", "Hot now", 30, recallLimit, 0, "not_needed");
            recordSkippedSource(diagnostics, "home.fallback.explore", "Explore something new", 26, recallLimit, 0, "not_needed");
        }
        if (diagnostics != null) {
            diagnostics.recordMergedAfterFallback(merged.size());
        }
        int explicitBoostedItems = boostByExplicitInterests(merged, explicitInterestTerms);
        if (diagnostics != null) {
            diagnostics.recordExplicitBoost(explicitBoostedItems, merged.size());
        }
        int mergedBeforeSafety = merged.size();
        filterService.applySafetyFilters(merged, currentUserId);
        if (diagnostics != null) {
            diagnostics.recordSafety(mergedBeforeSafety, merged.size());
        }

        Map<Long, Double> deepScores = loadDeepScores(currentUserId, merged, safePage, "home_feed", quotaExperiment.experimentId());
        Map<Long, PostRealtimeMetrics> realtimeMetrics = loadRealtimeMetrics(merged);

        List<RankedPost> ranked = merged.values().stream()
                .sorted(Comparator
                        .comparingDouble((RankedPost rp) -> blendedHomeRankScore(
                                rp,
                                deepScores.get(rp.post().getId()),
                                realtimeMetrics.get(rp.post().getId()),
                                effectiveSeed
                        )).reversed()
                        .thenComparing(Comparator.comparingInt(RankedPost::score).reversed())
                        .thenComparing(Comparator.comparingDouble((RankedPost rp) -> normalizedHomeDeepScore(deepScores.get(rp.post().getId()))).reversed())
                        .thenComparing(Comparator.comparing(RankedPost::hotScore).reversed())
                        .thenComparing(Comparator.comparing(RankedPost::createdAt).reversed()))
                .limit(MAX_FEED_WINDOW)
                .toList();
        if (diagnostics != null) {
            diagnostics.recordRankedBeforeSeenSuppression(ranked.size());
        }
        RecentSeenSuppressionResult recentSeenResult = applyRecentSeenSuppressionWithStats(ranked, currentUserId, safePage, safeSize);
        ranked = recentSeenResult.ranked();
        if (diagnostics != null) {
            diagnostics.recordRecentSeen(recentSeenResult);
        }
        ranked = rerankWithDiversity(ranked, effectiveSeed, "home", HOME_AUTHOR_CAP, HOME_TOPIC_CAP, HOME_FRESHNESS_INTERVAL, HOME_CAP_WINDOW);
        if (diagnostics != null) {
            diagnostics.recordAfterDiversity(ranked.size());
        }
        ranked = applySemanticFilter(ranked, semanticFilter);
        if (diagnostics != null) {
            diagnostics.recordAfterSemanticFilter(ranked.size());
        }
        ranked = applyHomeRefreshRotation(ranked, effectiveSeed, safeSize);

        long total = ranked.size();
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        List<RankedPost> pageItems = List.of();
        if (fromIndex < ranked.size()) {
            int toIndex = Math.min(fromIndex + safeSize, ranked.size());
            pageItems = ranked.subList(fromIndex, toIndex);
        }
        Map<Long, com.rangwaz.imagesocial.auth.dto.UserSummary> authorMap = userService.summaryMapByIds(
                pageItems.stream().map(item -> item.post().getAuthorId()).distinct().toList()
        );
        Map<Long, String> reasonMap = pageItems.stream().collect(java.util.stream.Collectors.toMap(
                item -> item.post().getId(),
                RankedPost::reason,
                (left, right) -> left,
                LinkedHashMap::new
        ));
        List<PostView> result = postService.toViews(
                pageItems.stream().map(RankedPost::post).toList(),
                authorMap,
                post -> reasonMap.getOrDefault(post.getId(), "Recommended for you")
        );

        if (publishExposure && currentUserId != null) {
            featureService.recordFeedExposures(
                    currentUserId,
                    pageItems.stream().map(item -> item.post().getId()).toList()
            );
            int exposureLimit = Math.min(pageItems.size(), MAX_SYNC_EXPOSURE_EVENTS_PER_PAGE);
            for (int index = 0; index < exposureLimit; index++) {
                RankedPost rankedPost = pageItems.get(index);
                eventService.publish(
                        "FEED_EXPOSURE",
                        currentUserId,
                        "POST",
                        rankedPost.post().getId(),
                        Map.of(
                                "surface", "home_feed",
                                "pageNo", safePage,
                                "rankPosition", fromIndex + index + 1,
                                "reason", rankedPost.reason(),
                                "requestId", effectiveSeed,
                                "experimentId", quotaExperiment.experimentId(),
                                "quotaBucket", quotaExperiment.bucket(),
                                "realtimeAlreadyRecorded", true
                        )
                );
            }
        }
        if (publishExposure) {
            Map<String, Object> traceFilters = new LinkedHashMap<>();
            if (topicFilter != null && !topicFilter.isBlank()) {
                traceFilters.put("topic", topicFilter);
            }
            if (styleFilter != null && !styleFilter.isBlank()) {
                traceFilters.put("style", styleFilter);
            }
            if (tagFilter != null && !tagFilter.isBlank()) {
                traceFilters.put("tag", tagFilter);
            }
            feedObservabilityService.recordHomeFeed(new FeedObservabilityService.HomeFeedTrace(
                    effectiveSeed,
                    currentUserId,
                    "home_feed",
                    safePage,
                    safeSize,
                    seed,
                    traceFilters,
                    quotaExperiment.experimentId(),
                    quotaExperiment.bucket(),
                    total,
                    fromIndex,
                    pageItems,
                    Duration.ofNanos(System.nanoTime() - traceStartedAt),
                    false
            ));
        }
        FeedHomeDiagnosticsResponse diagnosticsResponse = diagnostics == null
                ? null
                : diagnostics.build(pageItems, fromIndex, total);
        return new HomeFeedComputation(
                new PageResponse<>(result, total, safePage, safeSize),
                diagnosticsResponse
        );
    }

    public PageResponse<PostView> similarPosts(Long currentUserId,
                                               Long postId,
                                               int page,
                                               int size,
                                               String topicFilter,
                                               String styleFilter,
                                               String tagFilter) {
        Post currentPost = postService.requirePost(postId);
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int recallLimit = Math.min(
                Math.max(safePage * safeSize * 3, MIN_SIMILAR_RECALL_CANDIDATES),
                MAX_SIMILAR_RECALL_CANDIDATES
        );
        String effectiveSeed = normalizeSeed("similar-" + postId + "-" + safePage);
        FeedSemanticFilter semanticFilter = buildSemanticFilter(topicFilter, styleFilter, tagFilter);
        SimilarQuotaRatios similarQuotaRatios = resolveSimilarQuotaRatios();
        LinkedHashSet<Long> excludeIds = collectSimilarExcludeIds(currentUserId, postId);

        int vectorQuota = Math.max(24, quotaOf(recallLimit, similarQuotaRatios.vector()));
        int i2iQuota = Math.max(24, quotaOf(recallLimit, 0.32d));
        int semanticQuota = Math.max(24, quotaOf(recallLimit, similarQuotaRatios.semantic()));
        int fallbackQuota = Math.max(24, quotaOf(recallLimit, similarQuotaRatios.fallback()));

        Map<Long, RankedPost> merged = new LinkedHashMap<>();
        List<Post> i2iPosts = safeSimilarI2IRecall(
                currentUserId,
                postId,
                excludeIds,
                Math.max(i2iQuota * 2, safePage * safeSize)
        );
        mergeWithQuota(merged, i2iPosts, "Often enjoyed together", 112, i2iQuota);

        List<Long> recallIds = vectorRecallService.recallSimilarPostIds(postId, recallLimit, excludeIds.stream().toList());
        List<Post> recalledPosts = orderedPublicPosts(recallIds, postId);
        mergeWithQuota(merged, recalledPosts, "More like this", 120, vectorQuota);

        if (merged.size() < safePage * safeSize * 2) {
            List<String> semanticTerms = collectSemanticTerms(currentPost);
            if (!semanticTerms.isEmpty()) {
                List<Post> semanticFallbackPosts = recallService.recallBySemanticTerms(
                        currentUserId,
                        semanticTerms,
                        Math.max(180, recallLimit),
                        excludeIds,
                        effectiveSeed
                );
                mergeWithQuota(merged, semanticFallbackPosts, "Close in topic or style", 78, semanticQuota);
            }
        }

        if (merged.size() < safePage * safeSize * 3) {
            List<Post> exploreFallbackPosts = recallService.recallExplore(
                    currentUserId,
                    Math.max(180, recallLimit),
                    effectiveSeed
            );
            List<Post> contextualFallbackPosts = rankContextualFallbackPosts(
                    currentPost,
                    exploreFallbackPosts,
                    excludeIds,
                    Math.max(fallbackQuota * 2, safePage * safeSize),
                    effectiveSeed
            );
            mergeWithQuota(merged, contextualFallbackPosts, "Fresh finds near this style", 36, fallbackQuota);
        }

        filterService.applySafetyFilters(merged, currentUserId);
        Map<Long, Double> deepScores = loadDeepScores(currentUserId, merged, safePage, "similar_feed");
        Map<Long, PostRealtimeMetrics> realtimeMetrics = loadRealtimeMetrics(merged);

        List<RankedPost> ranked = merged.values().stream()
                .sorted(Comparator
                        .comparingDouble((RankedPost rp) -> blendedSimilarRankScore(
                                currentPost,
                                rp,
                                deepScores.get(rp.post().getId()),
                                realtimeMetrics.get(rp.post().getId())
                        )).reversed()
                        .thenComparing(Comparator.comparingInt(RankedPost::score).reversed())
                        .thenComparing(Comparator.comparingDouble((RankedPost rp) -> normalizedHomeDeepScore(deepScores.get(rp.post().getId()))).reversed())
                        .thenComparing(Comparator.comparing(RankedPost::hotScore).reversed())
                        .thenComparing(Comparator.comparing(RankedPost::createdAt).reversed()))
                .limit(MAX_FEED_WINDOW)
                .toList();
        ranked = rerankWithDiversity(ranked, effectiveSeed, "similar", SIMILAR_AUTHOR_CAP, SIMILAR_TOPIC_CAP, SIMILAR_FRESHNESS_INTERVAL, SIMILAR_CAP_WINDOW);
        ranked = applySemanticFilter(ranked, semanticFilter);

        long total = ranked.size();
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        if (fromIndex >= ranked.size()) {
            return new PageResponse<>(List.of(), total, safePage, safeSize);
        }

        int toIndex = Math.min(fromIndex + safeSize, ranked.size());
        List<RankedPost> pageItems = ranked.subList(fromIndex, toIndex);
        Map<Long, String> reasonMap = new HashMap<>();
        for (RankedPost rankedPost : pageItems) {
            reasonMap.put(rankedPost.post().getId(), rankedPost.reason());
        }
        Map<Long, com.rangwaz.imagesocial.auth.dto.UserSummary> authorMap = userService.summaryMapByIds(
                pageItems.stream().map(item -> item.post().getAuthorId()).distinct().toList()
        );
        List<PostView> result = postService.toViews(
                pageItems.stream().map(RankedPost::post).toList(),
                authorMap,
                post -> reasonMap.getOrDefault(post.getId(), "Related to this post")
        );
        return new PageResponse<>(result, total, safePage, safeSize);
    }

    private LinkedHashSet<Long> collectSimilarExcludeIds(Long userId, Long postId) {
        LinkedHashSet<Long> excludeIds = new LinkedHashSet<>();
        if (postId != null) {
            excludeIds.add(postId);
        }
        if (userId == null) {
            return excludeIds;
        }
        RecentSeenBuckets buckets = collectRecentSeenBuckets(userId);
        excludeIds.addAll(buckets.shortTermExposureIds());
        excludeIds.addAll(buckets.strongSeenIds());
        return excludeIds;
    }

    private List<Post> rankContextualFallbackPosts(Post reference,
                                                   List<Post> candidates,
                                                   Set<Long> excludeIds,
                                                   int limit,
                                                   String seed) {
        if (reference == null || candidates == null || candidates.isEmpty() || limit <= 0) {
            return List.of();
        }
        Set<String> referenceTerms = new LinkedHashSet<>(collectSemanticTerms(reference));
        boolean requireSemanticAnchor = !referenceTerms.isEmpty();
        Set<Long> safeExcludeIds = excludeIds == null ? Set.of() : excludeIds;
        List<Post> contextual = candidates.stream()
                .filter(post -> isPublicApproved(post) && !safeExcludeIds.contains(post.getId()))
                .filter(post -> !requireSemanticAnchor || contextualSimilarityScore(reference, post) >= 0.08d)
                .sorted(Comparator
                        .comparingDouble((Post post) -> contextualFallbackScore(reference, post, seed)).reversed()
                        .thenComparing(Comparator.comparing(Post::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()))
                .limit(limit)
                .toList();

        int minimumUsefulFallback = Math.min(limit, 8);
        if (contextual.size() >= minimumUsefulFallback) {
            return contextual;
        }

        LinkedHashMap<Long, Post> merged = new LinkedHashMap<>();
        for (Post post : contextual) {
            merged.put(post.getId(), post);
        }
        candidates.stream()
                .filter(post -> isPublicApproved(post) && !safeExcludeIds.contains(post.getId()))
                .sorted(Comparator
                        .comparingDouble((Post post) -> fallbackQualityScore(post, seed)).reversed()
                        .thenComparing(Comparator.comparing(Post::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed()))
                .forEach(post -> {
                    if (merged.size() < limit) {
                        merged.putIfAbsent(post.getId(), post);
                    }
                });
        return merged.values().stream().limit(limit).toList();
    }

    private List<RankedPost> applyRecentSeenSuppression(List<RankedPost> ranked, Long userId, int page, int size) {
        return applyRecentSeenSuppressionWithStats(ranked, userId, page, size).ranked();
    }

    private RecentSeenSuppressionResult applyRecentSeenSuppressionWithStats(List<RankedPost> ranked,
                                                                            Long userId,
                                                                            int page,
                                                                            int size) {
        if (userId == null || ranked.isEmpty()) {
            return new RecentSeenSuppressionResult(ranked, 0, 0, 0);
        }

        RecentSeenBuckets buckets = collectRecentSeenBuckets(userId);
        if (buckets.isEmpty()) {
            return new RecentSeenSuppressionResult(ranked, 0, 0, 0);
        }

        List<RankedPost> fresh = new ArrayList<>(ranked.size());
        List<RankedPost> softSuppressed = new ArrayList<>();
        List<RankedPost> strongSuppressed = new ArrayList<>();
        int shortTermSuppressedCount = 0;
        int strongSuppressedCount = 0;
        int softSuppressedCount = 0;
        for (RankedPost item : ranked) {
            Long itemPostId = item.post().getId();
            if (buckets.shortTermExposureIds().contains(itemPostId)) {
                shortTermSuppressedCount++;
                strongSuppressed.add(item);
                continue;
            }
            if (buckets.strongSeenIds().contains(itemPostId)) {
                strongSuppressedCount++;
                strongSuppressed.add(item);
                continue;
            }
            if (buckets.softSeenIds().contains(itemPostId)) {
                softSuppressedCount++;
                softSuppressed.add(item);
                continue;
            }
            fresh.add(item);
        }

        int targetWindow = Math.min(MAX_FEED_WINDOW, Math.max(MIN_FRESH_FEED_WINDOW, page * size * 3));
        List<RankedPost> reordered = new ArrayList<>(ranked.size());
        reordered.addAll(fresh);
        appendUntilSize(reordered, softSuppressed, targetWindow);
        appendUntilSize(reordered, strongSuppressed, targetWindow);
        appendRemaining(reordered, softSuppressed);
        appendRemaining(reordered, strongSuppressed);
        return new RecentSeenSuppressionResult(
                reordered,
                shortTermSuppressedCount,
                strongSuppressedCount,
                softSuppressedCount
        );
    }

    private RecentSeenBuckets collectRecentSeenBuckets(Long userId) {
        List<Long> recentExposureIds = featureService.getUserRecentExposureIds(userId, SHORT_TERM_EXPOSURE_LIMIT);
        List<Long> recentViews = featureService.getUserRecentViews(userId);
        List<Long> recentClicks = featureService.getUserRecentClicks(userId);
        List<Long> recentDetailViews = featureService.getUserRecentDetailViews(userId);

        Set<Long> shortTermExposureIds = new LinkedHashSet<>();
        shortTermExposureIds.addAll(recentExposureIds.stream().limit(SHORT_TERM_EXPOSURE_LIMIT).toList());
        shortTermExposureIds.addAll(recentViews.stream()
                .limit(Math.max(0, SHORT_TERM_EXPOSURE_LIMIT - shortTermExposureIds.size()))
                .toList());

        Set<Long> strongSeenIds = new LinkedHashSet<>();
        strongSeenIds.addAll(recentClicks.stream().limit(STRONG_SEEN_LIMIT).toList());
        strongSeenIds.addAll(recentDetailViews.stream().limit(STRONG_SEEN_LIMIT).toList());

        Set<Long> softSeenIds = new LinkedHashSet<>();
        softSeenIds.addAll(recentViews.stream().limit(SOFT_SEEN_LIMIT).toList());
        softSeenIds.addAll(recentClicks.stream().limit(SOFT_SEEN_LIMIT).toList());
        softSeenIds.addAll(recentDetailViews.stream().limit(SOFT_SEEN_LIMIT).toList());
        softSeenIds.removeAll(shortTermExposureIds);
        softSeenIds.removeAll(strongSeenIds);

        return new RecentSeenBuckets(shortTermExposureIds, strongSeenIds, softSeenIds);
    }

    private boolean needsMorePersonalizedRecall(Map<Long, RankedPost> merged,
                                                int recallLimit,
                                                int page,
                                                int size) {
        int target = Math.min(recallLimit, Math.max(MIN_RECALL_CANDIDATES, page * size * 3));
        return merged.size() < target;
    }

    private double blendedHomeRankScore(RankedPost candidate,
                                        Double deepScore,
                                        PostRealtimeMetrics realtimeMetrics,
                                        String seed) {
        double score = candidate.score() * 1.55d;
        score += normalizedHomeDeepScore(deepScore) * 52.0d;
        score += Math.min(candidate.hotScore().doubleValue(), 80.0d) * 0.08d;
        score += freshnessBoost(candidate.post()) * 16.0d;
        score += realtimeMomentumScore(realtimeMetrics);
        score += stableNoise(seed, "home-refresh-rotation", candidate.post().getId()) * 32.0d;
        return score;
    }

    private List<RankedPost> applyHomeRefreshRotation(List<RankedPost> ranked, String seed, int pageSize) {
        if (ranked.size() <= pageSize) {
            return ranked;
        }
        int windowSize = Math.min(ranked.size(), Math.max(pageSize * 8, 80));
        Map<Long, Integer> originalIndexByPostId = new HashMap<>();
        for (int index = 0; index < windowSize; index++) {
            originalIndexByPostId.put(ranked.get(index).post().getId(), index);
        }

        List<RankedPost> rotatedHead = new ArrayList<>(ranked.subList(0, windowSize));
        rotatedHead.sort(Comparator
                .comparingDouble((RankedPost candidate) -> {
                    int originalIndex = originalIndexByPostId.getOrDefault(candidate.post().getId(), 0);
                    double refreshScore = stableNoise(seed, "home-refresh-window", candidate.post().getId()) * windowSize;
                    double qualityAnchor = -originalIndex * 0.32d;
                    return refreshScore + qualityAnchor;
                })
                .reversed());

        List<RankedPost> result = new ArrayList<>(ranked.size());
        result.addAll(rotatedHead);
        if (windowSize < ranked.size()) {
            result.addAll(ranked.subList(windowSize, ranked.size()));
        }
        return result;
    }

    private double blendedSimilarRankScore(Post reference,
                                           RankedPost candidate,
                                           Double deepScore,
                                           PostRealtimeMetrics realtimeMetrics) {
        double score = candidate.score() * 1.35d;
        score += contextualSimilarityScore(reference, candidate.post()) * 72.0d;
        score += normalizedHomeDeepScore(deepScore) * 44.0d;
        score += Math.min(candidate.hotScore().doubleValue(), 80.0d) * 0.14d;
        score += freshnessBoost(candidate.post()) * 12.0d;
        score += Math.min(safeDecimal(candidate.post().getQualityScore()), 1.5d) * 8.0d;
        score += Math.min(safeDecimal(candidate.post().getAestheticScore()), 1.5d) * 7.0d;
        score += realtimeMomentumScore(realtimeMetrics) * 0.68d;
        return score;
    }

    private double realtimeMomentumScore(PostRealtimeMetrics metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return 0.0d;
        }
        double oneHourConfidence = confidence(metrics.exposure1h(), 80.0d);
        double dayConfidence = confidence(metrics.exposure24h(), 420.0d);
        double shortTerm = (
                metrics.ctr1h() * 24.0d
                        + metrics.detailRate1h() * 14.0d
                        + metrics.positiveRate1h() * 30.0d
        ) * oneHourConfidence;
        double dayTerm = (
                metrics.ctr24h() * 14.0d
                        + metrics.detailRate24h() * 8.0d
                        + metrics.positiveRate24h() * 18.0d
        ) * dayConfidence;
        double negativePenalty = metrics.negativeRate24h() * 32.0d * dayConfidence;
        return Math.max(-14.0d, Math.min(18.0d, shortTerm + dayTerm - negativePenalty));
    }

    private double confidence(long count, double saturation) {
        if (count <= 0L || saturation <= 1.0d) {
            return 0.0d;
        }
        return Math.min(1.0d, Math.log1p(count) / Math.log1p(saturation));
    }

    private double contextualFallbackScore(Post reference, Post candidate, String seed) {
        double score = contextualSimilarityScore(reference, candidate) * 100.0d;
        score += Math.min(safeDecimal(candidate.getHotScore()), 90.0d) * 0.10d;
        score += Math.min(safeDecimal(candidate.getQualityScore()), 1.5d) * 10.0d;
        score += Math.min(safeDecimal(candidate.getAestheticScore()), 1.5d) * 8.0d;
        score += freshnessBoost(candidate) * 10.0d;
        score += stableNoise(seed, "similar-contextual-fallback", candidate.getId()) * 2.0d;
        return score;
    }

    private double fallbackQualityScore(Post candidate, String seed) {
        double score = Math.min(safeDecimal(candidate.getHotScore()), 90.0d) * 0.10d;
        score += Math.min(safeDecimal(candidate.getQualityScore()), 1.5d) * 10.0d;
        score += Math.min(safeDecimal(candidate.getAestheticScore()), 1.5d) * 8.0d;
        score += freshnessBoost(candidate) * 12.0d;
        score += stableNoise(seed, "similar-quality-fallback", candidate.getId()) * 6.0d;
        return score;
    }

    private double contextualSimilarityScore(Post reference, Post candidate) {
        if (reference == null || candidate == null || Objects.equals(reference.getId(), candidate.getId())) {
            return 0.0d;
        }
        Set<String> referenceTerms = new LinkedHashSet<>(collectSemanticTerms(reference));
        Set<String> candidateTerms = new LinkedHashSet<>(collectSemanticTerms(candidate));
        double score = Math.min(0.62d, jaccard(referenceTerms, candidateTerms) * 2.4d);
        score += Math.min(0.28d, sharedTermCount(referenceTerms, candidateTerms) * 0.045d);
        if (sameNonBlank(reference.getSubtopicClusterKey(), candidate.getSubtopicClusterKey())) {
            score += 0.32d;
        }
        if (sameNonBlank(reference.getTopicClusterKey(), candidate.getTopicClusterKey())) {
            score += 0.24d;
        }
        if (Objects.equals(primaryTopicKey(reference), primaryTopicKey(candidate))) {
            score += 0.12d;
        }
        if (Objects.equals(reference.getAuthorId(), candidate.getAuthorId())) {
            score += 0.05d;
        }
        return Math.min(1.35d, score);
    }

    private double normalizedHomeDeepScore(Double deepScore) {
        if (deepScore == null) {
            return 0.0d;
        }
        if (Double.isNaN(deepScore) || Double.isInfinite(deepScore)) {
            return 0.0d;
        }
        return Math.max(-0.2d, Math.min(1.5d, deepScore));
    }

    private Map<Long, Double> loadDeepScores(Long userId, Map<Long, RankedPost> merged, int page) {
        return loadDeepScores(userId, merged, page, "home_feed");
    }

    private Map<Long, Double> loadDeepScores(Long userId, Map<Long, RankedPost> merged, int page, String scene) {
        return loadDeepScores(userId, merged, page, scene, "");
    }

    private Map<Long, Double> loadDeepScores(Long userId,
                                             Map<Long, RankedPost> merged,
                                             int page,
                                             String scene,
                                             String experimentId) {
        if (userId == null || merged.isEmpty() || page > MAX_DEEP_RANK_PAGE) {
            return Map.of();
        }
        int candidateLimit = page <= 1
                ? MAX_DEEP_RANK_CANDIDATES_FIRST_PAGE
                : MAX_DEEP_RANK_CANDIDATES_LATER_PAGE;
        List<Post> candidates = merged.values().stream()
                .sorted(Comparator
                        .comparingInt(RankedPost::score).reversed()
                        .thenComparing(Comparator.comparing(RankedPost::hotScore).reversed())
                        .thenComparing(Comparator.comparing(RankedPost::createdAt).reversed()))
                .limit(candidateLimit)
                .map(RankedPost::post)
                .toList();
        return deepRankingService.score(userId, candidates, scene, page, experimentId);
    }

    private Map<Long, PostRealtimeMetrics> loadRealtimeMetrics(Map<Long, RankedPost> merged) {
        if (merged == null || merged.isEmpty()) {
            return Map.of();
        }
        return featureService.getPostRealtimeMetrics(merged.keySet());
    }

    private List<Post> safeSimilarI2IRecall(Long currentUserId, Long postId, Set<Long> excludeIds, int limit) {
        try {
            return postMapper.selectI2INeighborPosts(
                    currentUserId == null ? -1L : currentUserId,
                    List.of(postId),
                    excludeIds == null ? List.of() : excludeIds.stream().toList(),
                    Math.max(1, limit)
            );
        } catch (Exception exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("similar i2i recall failed, degraded to vector/semantic fallback: {}", message);
            return List.of();
        }
    }

    private RecallOutcome safeRecall(String source, Supplier<List<Post>> supplier) {
        long startNanos = System.nanoTime();
        try {
            List<Post> recalled = supplier.get();
            List<Post> posts = recalled == null ? List.of() : recalled;
            return new RecallOutcome(
                    posts,
                    posts.isEmpty() ? "empty" : "success",
                    null,
                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
            );
        } catch (Exception exception) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("feed recall source '{}' failed, degraded to other sources: {}", source, message);
            return new RecallOutcome(
                    List.of(),
                    "failed",
                    message,
                    Duration.ofNanos(System.nanoTime() - startNanos).toMillis()
            );
        }
    }

    private MergeStats recallAndMergeWithQuota(Map<Long, RankedPost> merged,
                                               HomeFeedDiagnosticsCollector diagnostics,
                                               String sourceKey,
                                               String reason,
                                               int score,
                                               int requestedFetchSize,
                                               int uniqueQuota,
                                               Supplier<List<Post>> supplier) {
        RecallOutcome outcome = safeRecall(sourceKey, supplier);
        MergeStats mergeStats = mergeWithQuota(merged, outcome.posts(), reason, score, uniqueQuota);
        feedSourceHealthTrackerService.record(sourceKey, outcome.status(), outcome.latencyMs(), outcome.message());
        if (diagnostics != null) {
            diagnostics.recordSource(sourceKey, reason, score, requestedFetchSize, uniqueQuota, outcome, mergeStats);
        }
        return mergeStats;
    }

    private MergeStats recallAndMerge(Map<Long, RankedPost> merged,
                                      HomeFeedDiagnosticsCollector diagnostics,
                                      String sourceKey,
                                      String reason,
                                      int score,
                                      int requestedFetchSize,
                                      Supplier<List<Post>> supplier) {
        RecallOutcome outcome = safeRecall(sourceKey, supplier);
        MergeStats mergeStats = merge(merged, outcome.posts(), reason, score);
        feedSourceHealthTrackerService.record(sourceKey, outcome.status(), outcome.latencyMs(), outcome.message());
        if (diagnostics != null) {
            diagnostics.recordSource(sourceKey, reason, score, requestedFetchSize, 0, outcome, mergeStats);
        }
        return mergeStats;
    }

    private void recordSkippedSource(HomeFeedDiagnosticsCollector diagnostics,
                                     String sourceKey,
                                     String reason,
                                     int score,
                                     int requestedFetchSize,
                                     int uniqueQuota,
                                     String message) {
        if (diagnostics == null) {
            feedSourceHealthTrackerService.record(sourceKey, "skipped", 0L, message);
            return;
        }
        feedSourceHealthTrackerService.record(sourceKey, "skipped", 0L, message);
        diagnostics.recordSkippedSource(sourceKey, reason, score, requestedFetchSize, uniqueQuota, message);
    }

    private void appendUntilSize(List<RankedPost> target, List<RankedPost> source, int expectedSize) {
        if (target.size() >= expectedSize || source.isEmpty()) {
            return;
        }
        int appendSize = Math.min(expectedSize - target.size(), source.size());
        target.addAll(source.subList(0, appendSize));
    }

    private void appendRemaining(List<RankedPost> target, List<RankedPost> source) {
        if (source.isEmpty()) {
            return;
        }
        for (RankedPost item : source) {
            if (target.contains(item)) {
                continue;
            }
            target.add(item);
        }
    }

    private List<Post> orderedPublicPosts(List<Long> ids, Long excludePostId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<Post> fetched = postMapper.selectByIds(ids);
        if (fetched.isEmpty()) {
            return List.of();
        }

        Map<Long, Post> byId = fetched.stream().collect(java.util.stream.Collectors.toMap(Post::getId, post -> post));
        Map<Long, Integer> authorCounts = new HashMap<>();
        List<Post> ordered = new ArrayList<>();
        for (Long id : ids) {
            Post post = byId.get(id);
            if (post == null || post.getId().equals(excludePostId)) {
                continue;
            }
            if (!"PUBLIC".equals(post.getVisibility()) || !"APPROVED".equals(post.getAuditStatus())) {
                continue;
            }
            int authorCount = authorCounts.getOrDefault(post.getAuthorId(), 0);
            if (authorCount >= MAX_AUTHOR_DUPLICATES_IN_CANDIDATES) {
                continue;
            }
            authorCounts.put(post.getAuthorId(), authorCount + 1);
            ordered.add(post);
        }
        return ordered;
    }

    private HomeQuotaExperiment resolveHomeQuotaExperiment(Long userId) {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        if (userId == null) {
            HomeQuotaRatios anonymousRatios = conf == null || conf.anonymous() == null
                    ? new HomeQuotaRatios(ANON_HOT_RATIO, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, ANON_EXPLORE_RATIO)
                    : normalizeHomeQuotaRatios(conf.anonymous());
            return new HomeQuotaExperiment(
                    anonymousRatios,
                    "feed_quota_anon",
                    "anon"
            );
        }

        String experimentName = conf == null || conf.experimentName() == null || conf.experimentName().isBlank()
                ? DEFAULT_HOME_QUOTA_EXPERIMENT
                : conf.experimentName().trim();

        boolean forceControl = conf != null && conf.forceControl();
        if (forceControl) {
            return new HomeQuotaExperiment(
                    normalizeHomeQuotaRatios(conf == null ? null : conf.control()),
                    experimentName + ":rollback_control",
                    "rollback_control"
            );
        }

        if (conf == null || !conf.abEnabled()) {
            return new HomeQuotaExperiment(
                    normalizeHomeQuotaRatios(conf == null ? null : conf.control()),
                    experimentName + ":control",
                    "control"
            );
        }

        int bucket = Math.floorMod(Objects.hash(AB_BUCKET_SALT, userId), 10_000);
        double treatmentRatio = Math.max(0.0d, Math.min(1.0d, conf.treatmentRatio()));
        boolean treatment = bucket < (int) Math.round(treatmentRatio * 10_000.0d);
        String bucketName = treatment ? "treatment" : "control";
        return new HomeQuotaExperiment(
                treatment
                        ? normalizeHomeQuotaRatios(conf.treatment())
                        : normalizeHomeQuotaRatios(conf.control()),
                experimentName + ":" + bucketName,
                bucketName
        );
    }

    private SimilarQuotaRatios resolveSimilarQuotaRatios() {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        RecommendationProperties.SimilarQuota raw = conf == null ? null : conf.similar();
        double vector = raw == null ? SIMILAR_VECTOR_RATIO : Math.max(0.0d, raw.vector());
        double semantic = raw == null ? SIMILAR_SEMANTIC_RATIO : Math.max(0.0d, raw.semantic());
        double fallback = raw == null ? SIMILAR_FALLBACK_RATIO : Math.max(0.0d, raw.fallback());
        double total = vector + semantic + fallback;
        if (total <= 1e-9) {
            return new SimilarQuotaRatios(SIMILAR_VECTOR_RATIO, SIMILAR_SEMANTIC_RATIO, SIMILAR_FALLBACK_RATIO);
        }
        return new SimilarQuotaRatios(vector / total, semantic / total, fallback / total);
    }

    private HomeQuotaRatios normalizeHomeQuotaRatios(RecommendationProperties.HomeQuota raw) {
        double hot = raw == null ? HOME_HOT_RATIO : Math.max(0.0d, raw.hot());
        double social = raw == null ? HOME_SOCIAL_RATIO : Math.max(0.0d, raw.social());
        double content = raw == null ? HOME_CONTENT_RATIO : Math.max(0.0d, raw.content());
        double online = raw == null ? HOME_ONLINE_RATIO : Math.max(0.0d, raw.online());
        double recentPositive = raw == null ? HOME_RECENT_POS_RATIO : Math.max(0.0d, raw.recentPositive());
        double vector = raw == null ? HOME_VECTOR_RATIO : Math.max(0.0d, raw.vector());
        double explicit = raw == null ? HOME_EXPLICIT_RATIO : Math.max(0.0d, raw.explicit());
        double explore = raw == null ? HOME_EXPLORE_RATIO : Math.max(0.0d, raw.explore());

        if (raw != null && isAnonymousQuota(raw)) {
            hot = Math.max(0.0d, raw.hot());
            social = 0.0d;
            content = 0.0d;
            online = 0.0d;
            recentPositive = 0.0d;
            vector = 0.0d;
            explicit = 0.0d;
            explore = Math.max(0.0d, raw.explore());
        }

        double total = hot + social + content + online + recentPositive + vector + explicit + explore;
        if (total <= 1e-9) {
            return new HomeQuotaRatios(
                    HOME_HOT_RATIO,
                    HOME_SOCIAL_RATIO,
                    HOME_CONTENT_RATIO,
                    HOME_ONLINE_RATIO,
                    HOME_RECENT_POS_RATIO,
                    HOME_VECTOR_RATIO,
                    HOME_EXPLICIT_RATIO,
                    HOME_EXPLORE_RATIO
            );
        }
        return new HomeQuotaRatios(
                hot / total,
                social / total,
                content / total,
                online / total,
                recentPositive / total,
                vector / total,
                explicit / total,
                explore / total
        );
    }

    private boolean isAnonymousQuota(RecommendationProperties.HomeQuota raw) {
        if (raw == null) {
            return false;
        }
        return raw.social() <= 0.0d
                && raw.content() <= 0.0d
                && raw.online() <= 0.0d
                && raw.recentPositive() <= 0.0d
                && raw.vector() <= 0.0d
                && raw.explicit() <= 0.0d;
    }

    private int homeMinSourceQuota() {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        if (conf == null || conf.minSourceQuota() <= 0) {
            return HOME_MIN_SOURCE_QUOTA;
        }
        return Math.max(1, conf.minSourceQuota());
    }

    private int homeFetchMultiplier() {
        RecommendationProperties.FeedQuota conf = recommendationProperties.feedQuota();
        if (conf == null || conf.fetchMultiplier() <= 0) {
            return HOME_FETCH_MULTIPLIER;
        }
        return Math.max(1, conf.fetchMultiplier());
    }

    private int quotaOf(int recallLimit, double ratio) {
        if (recallLimit <= 0 || ratio <= 0.0d) {
            return 0;
        }
        return Math.max(1, (int) Math.round(recallLimit * ratio));
    }

    private MergeStats mergeWithQuota(Map<Long, RankedPost> merged,
                                      List<Post> posts,
                                      String reason,
                                      int score,
                                      int uniqueQuota) {
        int mergedBefore = merged.size();
        if (posts == null || posts.isEmpty() || uniqueQuota <= 0) {
            return new MergeStats(0, mergedBefore, merged.size());
        }
        int added = 0;
        for (Post post : posts) {
            boolean isNew = !merged.containsKey(post.getId());
            if (isNew && added >= uniqueQuota) {
                continue;
            }
            if (isNew) {
                added++;
            }
            merged.compute(post.getId(), (key, existing) -> {
                if (existing == null) {
                    return new RankedPost(post, reason, score);
                }
                int nextScore = existing.score() + score;
                String nextReason = existing.reason().equals(reason)
                        ? existing.reason()
                        : existing.reason() + " / " + reason;
                return new RankedPost(post, nextReason, nextScore);
            });
        }
        return new MergeStats(added, mergedBefore, merged.size());
    }

    private MergeStats merge(Map<Long, RankedPost> merged, List<Post> posts, String reason, int score) {
        int mergedBefore = merged.size();
        int added = 0;
        for (Post post : posts) {
            if (!merged.containsKey(post.getId())) {
                added++;
            }
            merged.compute(post.getId(), (key, existing) -> {
                if (existing == null) {
                    return new RankedPost(post, reason, score);
                }
                int nextScore = existing.score() + score;
                String nextReason = existing.reason().equals(reason)
                        ? existing.reason()
                        : existing.reason() + " / " + reason;
                return new RankedPost(post, nextReason, nextScore);
            });
        }
        return new MergeStats(added, mergedBefore, merged.size());
    }

    private List<RankedPost> rerankWithDiversity(List<RankedPost> ranked,
                                                 String seed,
                                                 String scene,
                                                 int authorCap,
                                                 int topicCap,
                                                 int freshnessInterval,
                                                 int capWindow) {
        if (ranked.size() <= 1) {
            return ranked;
        }

        Map<Long, Integer> originalIndex = new HashMap<>();
        Map<Long, PostSemanticSignature> signatures = new HashMap<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedPost item = ranked.get(index);
            originalIndex.put(item.post().getId(), index);
            signatures.put(item.post().getId(), buildSignature(item.post()));
        }
        double lambda = "similar".equals(scene)
                ? MMR_LAMBDA_SIMILAR
                : ("home".equals(scene) ? MMR_LAMBDA_DEFAULT : MMR_LAMBDA_EXPLORE);
        return MmrReranker.rerank(
                ranked,
                signatures,
                originalIndex,
                ranked.size(),
                lambda,
                authorCap,
                topicCap,
                capWindow,
                seed,
                scene
        );
    }

    private RankedPost pickBestCandidate(List<RankedPost> remaining,
                                         List<RankedPost> selected,
                                         Map<Long, Integer> originalIndex,
                                         Map<Long, Integer> authorCounts,
                                         Map<String, Integer> topicCounts,
                                         Map<Long, PostSemanticSignature> signatures,
                                         String seed,
                                         String scene,
                                         int authorCap,
                                         int topicCap,
                                         int position,
                                         int capWindow) {

        RankedPost best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (RankedPost candidate : remaining) {
            if (position < capWindow && exceedsCap(candidate, authorCounts, topicCounts, signatures, authorCap, topicCap)) {
                continue;
            }
            double rerankScore = rerankScore(candidate, selected, originalIndex, authorCounts, topicCounts, signatures, seed, scene);
            if (rerankScore > bestScore) {
                bestScore = rerankScore;
                best = candidate;
            }
        }

        if (best != null) {
            return best;
        }

        for (RankedPost candidate : remaining) {
            double rerankScore = rerankScore(candidate, selected, originalIndex, authorCounts, topicCounts, signatures, seed, scene);
            if (rerankScore > bestScore) {
                bestScore = rerankScore;
                best = candidate;
            }
        }
        return best;
    }

    private RankedPost pickFreshCandidate(List<RankedPost> remaining,
                                          Map<Long, Integer> authorCounts,
                                          Map<String, Integer> topicCounts,
                                          Map<Long, PostSemanticSignature> signatures,
                                          int authorCap,
                                          int topicCap,
                                          int position,
                                          int capWindow) {
        RankedPost freshest = null;
        for (int index = 0; index < Math.min(FRESH_PICK_SEARCH_WINDOW, remaining.size()); index++) {
            RankedPost candidate = remaining.get(index);
            if (freshest != null && compareCreatedAt(candidate.createdAt(), freshest.createdAt()) <= 0) {
                continue;
            }
            if (position < capWindow && exceedsCap(candidate, authorCounts, topicCounts, signatures, authorCap, topicCap)) {
                continue;
            }
            if (freshnessBoost(candidate.post()) < 0.22d) {
                continue;
            }
            freshest = candidate;
        }
        return freshest;
    }

    private boolean exceedsCap(RankedPost candidate,
                               Map<Long, Integer> authorCounts,
                               Map<String, Integer> topicCounts,
                               Map<Long, PostSemanticSignature> signatures,
                               int authorCap,
                               int topicCap) {
        PostSemanticSignature signature = signatures.get(candidate.post().getId());
        int authorCount = authorCounts.getOrDefault(candidate.post().getAuthorId(), 0);
        int topicCount = topicCounts.getOrDefault(signature.topicKey(), 0);
        return authorCount >= authorCap || topicCount >= topicCap;
    }

    private double rerankScore(RankedPost candidate,
                               List<RankedPost> selected,
                               Map<Long, Integer> originalIndex,
                               Map<Long, Integer> authorCounts,
                               Map<String, Integer> topicCounts,
                               Map<Long, PostSemanticSignature> signatures,
                               String seed,
                               String scene) {
        int index = originalIndex.getOrDefault(candidate.post().getId(), MAX_FEED_WINDOW);
        PostSemanticSignature signature = signatures.get(candidate.post().getId());
        double score = 640.0d - index * 3.5d;
        score += candidate.score() * 1.35d;
        score += Math.min(candidate.hotScore().doubleValue(), 60.0d) * 0.35d;
        score += freshnessBoost(candidate.post()) * 22.0d;
        score += Math.min(safeDecimal(candidate.post().getQualityScore()), 1.5d) * 11.0d;
        score += Math.min(safeDecimal(candidate.post().getAestheticScore()), 1.5d) * 10.0d;
        double safetyScore = candidate.post().getSafetyScore() == null ? 1.0d : safeDecimal(candidate.post().getSafetyScore());
        score += Math.min(safetyScore, 1.0d) * 6.0d;
        score -= authorCounts.getOrDefault(candidate.post().getAuthorId(), 0) * 42.0d;
        score -= topicCounts.getOrDefault(signature.topicKey(), 0) * 28.0d;
        score -= nearDuplicatePenalty(candidate, selected, signatures);
        score += stableHash(seed, scene + "-rerank", candidate.post().getId()) / 10_000.0d;
        return score;
    }

    private double nearDuplicatePenalty(RankedPost candidate,
                                        List<RankedPost> selected,
                                        Map<Long, PostSemanticSignature> signatures) {
        if (selected.isEmpty()) {
            return 0.0d;
        }

        PostSemanticSignature current = signatures.get(candidate.post().getId());
        double maxPenalty = 0.0d;
        int start = Math.max(0, selected.size() - SIMILARITY_CONTEXT_WINDOW);
        for (int index = start; index < selected.size(); index++) {
            RankedPost previous = selected.get(index);
            PostSemanticSignature reference = signatures.get(previous.post().getId());
            double overlapPenalty = jaccard(current.terms(), reference.terms()) * 46.0d;
            if (Objects.equals(current.topicKey(), reference.topicKey())) {
                overlapPenalty += 12.0d;
            }
            if (Objects.equals(candidate.post().getAuthorId(), previous.post().getAuthorId())) {
                overlapPenalty += 24.0d;
            }
            if (overlapPenalty > maxPenalty) {
                maxPenalty = overlapPenalty;
            }
        }
        return maxPenalty;
    }

    private PostSemanticSignature buildSignature(Post post) {
        Set<String> terms = new LinkedHashSet<>(collectSemanticTerms(post));
        if (terms.isEmpty() && post.getId() != null) {
            terms.add("post-" + post.getId());
        }
        return new PostSemanticSignature(
                post.getAuthorId(),
                primaryTopicKey(post),
                terms
        );
    }

    private List<String> collectSemanticTerms(Post post) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        appendTerms(terms, post.getTags());
        appendTerms(terms, post.getSemanticTags());
        appendTerms(terms, post.getStyleTags());
        appendTerms(terms, post.getTopicPath());
        appendTerms(terms, post.getTitle());
        return terms.stream().limit(24).toList();
    }

    private void appendTerms(Set<String> target, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String token : TERM_SPLITTER.split(raw.toLowerCase())) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.length() < 2) {
                continue;
            }
            if (normalized.chars().allMatch(Character::isDigit)) {
                continue;
            }
            target.add(normalized);
        }
    }

    private String primaryTopicKey(Post post) {
        String[] sources = {post.getTopicPath(), post.getSemanticTags(), post.getTags()};
        for (String source : sources) {
            if (source == null || source.isBlank()) {
                continue;
            }
            for (String token : TERM_SPLITTER.split(source.toLowerCase())) {
                String normalized = token == null ? "" : token.trim();
                if (normalized.length() >= 2) {
                    return normalized;
                }
            }
        }
        return "topic:unknown";
    }

    private double freshnessBoost(Post post) {
        LocalDateTime createdAt = post.getCreatedAt();
        if (createdAt == null) {
            return 0.0d;
        }
        long ageHours = Math.max(0L, Duration.between(createdAt, LocalDateTime.now()).toHours());
        return 1.0d / (1.0d + ageHours / 24.0d);
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0d;
        }
        int intersection = 0;
        for (String token : left) {
            if (right.contains(token)) {
                intersection++;
            }
        }
        int union = left.size() + right.size() - intersection;
        return union <= 0 ? 0.0d : intersection / (double) union;
    }

    private int sharedTermCount(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String token : left) {
            if (right.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private boolean sameNonBlank(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        String normalizedLeft = left.trim();
        String normalizedRight = right.trim();
        return !normalizedLeft.isBlank() && normalizedLeft.equalsIgnoreCase(normalizedRight);
    }

    private boolean isPublicApproved(Post post) {
        return post != null
                && "PUBLIC".equals(post.getVisibility())
                && "APPROVED".equals(post.getAuditStatus());
    }

    private int compareCreatedAt(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private double safeDecimal(BigDecimal value) {
        return value == null ? 0.0d : value.doubleValue();
    }

    private double stableNoise(String seed, String scene, Long postId) {
        return stableHash(seed, scene, postId) / (double) Integer.MAX_VALUE;
    }

    private static int stableHash(String seed, String scene, Long postId) {
        return Math.floorMod(Objects.hash(scene, seed == null ? "" : seed.trim(), postId), Integer.MAX_VALUE);
    }

    private String normalizeSeed(String seed) {
        if (seed == null || seed.isBlank()) {
            return "feed-" + (System.currentTimeMillis() / 30_000L);
        }
        return seed.trim();
    }

    private List<RankedPost> applySemanticFilter(List<RankedPost> ranked, FeedSemanticFilter filter) {
        if (filter == null || filter.isEmpty() || ranked.isEmpty()) {
            return ranked;
        }
        return ranked.stream()
                .filter(item -> matchesSemanticFilter(item.post(), filter))
                .toList();
    }

    private boolean matchesSemanticFilter(Post post, FeedSemanticFilter filter) {
        return matchesTerms(
                filter.topicTerms(),
                post.getTopicPath(),
                post.getSemanticTags(),
                post.getTags(),
                post.getTitle(),
                post.getContent())
                && matchesTerms(
                filter.styleTerms(),
                post.getStyleTags(),
                post.getSemanticTags(),
                post.getTags(),
                post.getTitle(),
                post.getContent())
                && matchesTerms(
                filter.tagTerms(),
                post.getTags(),
                post.getSemanticTags(),
                post.getTopicPath(),
                post.getTitle(),
                post.getContent());
    }

    private boolean matchesTerms(List<String> terms, String... fields) {
        if (terms == null || terms.isEmpty()) {
            return true;
        }
        String joined = Arrays.stream(fields)
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase().trim())
                .filter(value -> !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" "));
        if (joined.isBlank()) {
            return false;
        }
        for (String term : terms) {
            if (joined.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private FeedSemanticFilter buildSemanticFilter(String topic, String style, String tag) {
        return new FeedSemanticFilter(
                normalizeFilterTerms(topic),
                normalizeFilterTerms(style),
                normalizeFilterTerms(tag)
        );
    }

    private List<String> normalizeFilterTerms(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(TERM_SPLITTER.split(raw.toLowerCase()))
                .map(token -> token == null ? "" : token.trim())
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private Set<String> loadExplicitInterestTerms(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<String> terms = new LinkedHashSet<>();
        for (String rawKey : userInterestService.listActiveFacetKeys(userId)) {
            if (rawKey == null || rawKey.isBlank()) {
                continue;
            }
            String normalizedKey = rawKey.trim().toLowerCase();
            terms.add(normalizedKey);
            for (String token : TERM_SPLITTER.split(normalizedKey)) {
                String cleaned = token == null ? "" : token.trim();
                if (cleaned.length() >= 2) {
                    terms.add(cleaned);
                }
            }
            if (terms.size() >= 80) {
                break;
            }
        }
        return terms;
    }

    private Set<String> loadRecentSignalTerms(List<Long> strongPositiveSignals) {
        if (strongPositiveSignals == null || strongPositiveSignals.isEmpty()) {
            return Set.of();
        }
        List<Post> signalPosts = orderedPublicPosts(strongPositiveSignals, null);
        if (signalPosts.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Post post : signalPosts) {
            appendTerms(terms, post.getTopicPath());
            appendTerms(terms, post.getSemanticTags());
            appendTerms(terms, post.getStyleTags());
            appendTerms(terms, post.getTags());
            appendTerms(terms, post.getTopicClusterKey());
            appendTerms(terms, post.getSubtopicClusterKey());
            appendTerms(terms, post.getTitle());
            if (terms.size() >= 80) {
                break;
            }
        }
        return terms;
    }

    private int boostByRecentSignals(Map<Long, RankedPost> merged, Set<String> recentSignalTerms) {
        if (merged.isEmpty() || recentSignalTerms == null || recentSignalTerms.isEmpty()) {
            return 0;
        }
        int boostedItems = 0;
        for (Map.Entry<Long, RankedPost> entry : new ArrayList<>(merged.entrySet())) {
            RankedPost item = entry.getValue();
            int matches = semanticMatchCount(item.post(), recentSignalTerms);
            if (matches <= 0) {
                continue;
            }
            int boost = Math.min(RECENT_SIGNAL_MATCH_BONUS * matches, RECENT_SIGNAL_MATCH_BONUS * MAX_RECENT_SIGNAL_MATCHES);
            String reason = item.reason().contains("Current taste")
                    ? item.reason()
                    : item.reason() + " / Current taste";
            merged.put(entry.getKey(), new RankedPost(item.post(), reason, item.score() + boost));
            boostedItems++;
        }
        return boostedItems;
    }

    private int boostByExplicitInterests(Map<Long, RankedPost> merged, Set<String> explicitInterestTerms) {
        if (merged.isEmpty() || explicitInterestTerms == null || explicitInterestTerms.isEmpty()) {
            return 0;
        }
        int boostedItems = 0;
        for (Map.Entry<Long, RankedPost> entry : new ArrayList<>(merged.entrySet())) {
            RankedPost item = entry.getValue();
            int matches = semanticMatchCount(item.post(), explicitInterestTerms);
            if (matches <= 0) {
                continue;
            }
            int boost = Math.min(EXPLICIT_INTEREST_MATCH_BONUS * matches, EXPLICIT_INTEREST_MATCH_BONUS * 3);
            String reason = item.reason().contains("Subscribed topic")
                    ? item.reason()
                    : item.reason() + " / Subscribed topic";
            merged.put(entry.getKey(), new RankedPost(item.post(), reason, item.score() + boost));
            boostedItems++;
        }
        return boostedItems;
    }

    private int semanticMatchCount(Post post, Set<String> targetTerms) {
        if (targetTerms == null || targetTerms.isEmpty()) {
            return 0;
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        appendTerms(tokens, post.getTopicPath());
        appendTerms(tokens, post.getSemanticTags());
        appendTerms(tokens, post.getStyleTags());
        appendTerms(tokens, post.getTags());
        appendTerms(tokens, post.getTopicClusterKey());
        appendTerms(tokens, post.getSubtopicClusterKey());
        appendTerms(tokens, post.getTitle());
        int matches = 0;
        for (String token : tokens) {
            if (targetTerms.contains(token)) {
                matches++;
            }
        }
        return matches;
    }

    private record HomeQuotaRatios(double hot,
                                   double social,
                                   double content,
                                   double online,
                                   double recentPositive,
                                   double vector,
                                   double explicit,
                                   double explore) {
    }

    private record SimilarQuotaRatios(double vector, double semantic, double fallback) {
    }

    private record HomeQuotaExperiment(HomeQuotaRatios ratios, String experimentId, String bucket) {
    }

    private record RecentSeenBuckets(Set<Long> shortTermExposureIds, Set<Long> strongSeenIds, Set<Long> softSeenIds) {
        boolean isEmpty() {
            return shortTermExposureIds.isEmpty() && strongSeenIds.isEmpty() && softSeenIds.isEmpty();
        }
    }

    private record FeedSemanticFilter(List<String> topicTerms, List<String> styleTerms, List<String> tagTerms) {
        boolean isEmpty() {
            return (topicTerms == null || topicTerms.isEmpty())
                    && (styleTerms == null || styleTerms.isEmpty())
                    && (tagTerms == null || tagTerms.isEmpty());
        }
    }

    private record PostSemanticSignature(Long authorId, String topicKey, Set<String> terms) {
    }

    private static final class MmrReranker {
        private static final int SIM_CONTEXT_WINDOW = 16;

        static List<RankedPost> rerank(
                List<RankedPost> candidates,
                Map<Long, PostSemanticSignature> signatures,
                Map<Long, Integer> originalIndex,
                int targetSize,
                double lambda,
                int authorCap,
                int topicCap,
                int capWindow,
                String seed,
                String scene) {
            if (candidates.isEmpty()) {
                return List.of();
            }
            double safeLambda = Math.max(0.0d, Math.min(1.0d, lambda));
            int maxScore = candidates.stream().mapToInt(RankedPost::score).max().orElse(1);
            List<RankedPost> selected = new ArrayList<>(targetSize);
            List<RankedPost> remaining = new ArrayList<>(candidates);
            Map<Long, Integer> authorCounts = new HashMap<>();
            Map<String, Integer> topicCounts = new HashMap<>();

            while (selected.size() < targetSize && !remaining.isEmpty()) {
                RankedPost best = null;
                double bestMmrScore = Double.NEGATIVE_INFINITY;
                boolean enforceHardCaps = selected.size() < capWindow;
                for (RankedPost candidate : remaining) {
                    PostSemanticSignature signature = signatures.get(candidate.post().getId());
                    String topicKey = signature == null ? "" : signature.topicKey();
                    int authorCount = authorCounts.getOrDefault(candidate.post().getAuthorId(), 0);
                    int topicCount = topicCounts.getOrDefault(topicKey, 0);
                    if (enforceHardCaps && (authorCount >= authorCap || topicCount >= topicCap)) {
                        continue;
                    }

                    double mmrScore = scoreCandidate(
                            candidate,
                            selected,
                            signatures,
                            originalIndex,
                            maxScore,
                            safeLambda,
                            seed,
                            scene,
                            false,
                            authorCount,
                            topicCount,
                            authorCap,
                            topicCap
                    );
                    if (mmrScore > bestMmrScore) {
                        bestMmrScore = mmrScore;
                        best = candidate;
                    }
                }
                if (best == null) {
                    for (RankedPost candidate : remaining) {
                        PostSemanticSignature signature = signatures.get(candidate.post().getId());
                        String topicKey = signature == null ? "" : signature.topicKey();
                        int authorCount = authorCounts.getOrDefault(candidate.post().getAuthorId(), 0);
                        int topicCount = topicCounts.getOrDefault(topicKey, 0);
                        double mmrScore = scoreCandidate(
                                candidate,
                                selected,
                                signatures,
                                originalIndex,
                                maxScore,
                                safeLambda,
                                seed,
                                scene,
                                true,
                                authorCount,
                                topicCount,
                                authorCap,
                                topicCap
                        );
                        if (mmrScore > bestMmrScore) {
                            bestMmrScore = mmrScore;
                            best = candidate;
                        }
                    }
                }
                if (best == null) {
                    selected.addAll(remaining.subList(0, Math.min(targetSize - selected.size(), remaining.size())));
                    break;
                }
                selected.add(best);
                remaining.remove(best);
                authorCounts.merge(best.post().getAuthorId(), 1, Integer::sum);
                PostSemanticSignature signature = signatures.get(best.post().getId());
                if (signature != null) {
                    topicCounts.merge(signature.topicKey(), 1, Integer::sum);
                }
            }
            return selected;
        }

        private static double scoreCandidate(RankedPost candidate,
                                             List<RankedPost> selected,
                                             Map<Long, PostSemanticSignature> signatures,
                                             Map<Long, Integer> originalIndex,
                                             int maxScore,
                                             double safeLambda,
                                             String seed,
                                             String scene,
                                             boolean relaxed,
                                             int authorCount,
                                             int topicCount,
                                             int authorCap,
                                             int topicCap) {
            double relevance = candidate.score() / (double) Math.max(1, maxScore);
            Integer index = originalIndex.get(candidate.post().getId());
            if (index != null) {
                relevance += 1.0d / (1.0d + index);
            }
            double maxSimilarity = 0.0d;
            int start = Math.max(0, selected.size() - SIM_CONTEXT_WINDOW);
            for (int i = start; i < selected.size(); i++) {
                maxSimilarity = Math.max(maxSimilarity, semanticSim(candidate, selected.get(i), signatures));
            }
            double mmrScore = safeLambda * relevance - (1.0d - safeLambda) * maxSimilarity;
            if (relaxed) {
                int authorOverflow = Math.max(0, authorCount - Math.max(0, authorCap - 1));
                int topicOverflow = Math.max(0, topicCount - Math.max(0, topicCap - 1));
                mmrScore -= authorOverflow * 0.08d + topicOverflow * 0.05d;
            }
            mmrScore += stableHash(seed, scene + "-mmr", candidate.post().getId()) / 5_000_000.0d;
            return mmrScore;
        }

        private static double semanticSim(RankedPost leftPost,
                                          RankedPost rightPost,
                                          Map<Long, PostSemanticSignature> signatures) {
            PostSemanticSignature left = signatures.get(leftPost.post().getId());
            PostSemanticSignature right = signatures.get(rightPost.post().getId());
            if (left == null || right == null) {
                return 0.0d;
            }
            double authorPenalty = Objects.equals(leftPost.post().getAuthorId(), rightPost.post().getAuthorId()) ? 0.35d : 0.0d;
            double topicPenalty = Objects.equals(left.topicKey(), right.topicKey()) ? 0.15d : 0.0d;
            double jaccard = jaccard(left.terms(), right.terms());
            return Math.min(1.0d, jaccard + authorPenalty + topicPenalty);
        }

        private static double jaccard(Set<String> left, Set<String> right) {
            if (left.isEmpty() || right.isEmpty()) {
                return 0.0d;
            }
            int intersection = 0;
            for (String token : left) {
                if (right.contains(token)) {
                    intersection++;
                }
            }
            int union = left.size() + right.size() - intersection;
            return union <= 0 ? 0.0d : intersection / (double) union;
        }
    }

    private record HomeFeedComputation(PageResponse<PostView> pageResponse,
                                       FeedHomeDiagnosticsResponse diagnostics) {
    }

    private record RecallOutcome(List<Post> posts, String status, String message, long latencyMs) {
    }

    private record MergeStats(int uniqueAdded, int mergedBefore, int mergedAfter) {
    }

    private record RecentSeenSuppressionResult(List<RankedPost> ranked,
                                               int shortTermSuppressedCount,
                                               int strongSuppressedCount,
                                               int softSuppressedCount) {
    }

    private static final class HomeFeedDiagnosticsCollector {
        private final FeedSourceHealthTrackerService feedSourceHealthTrackerService;
        private final FeedHomeDiagnosticsRequest request;
        private final FeedHomeDiagnosticsFilters filters;
        private final List<FeedHomeDiagnosticsSource> sources = new ArrayList<>();
        private int mergedAfterPrimarySources;
        private int mergedAfterFallback;
        private int explicitBoostedItems;
        private int mergedAfterExplicitBoost;
        private int mergedBeforeSafetyFilter;
        private int mergedAfterSafetyFilter;
        private int rankedBeforeSeenSuppression;
        private int shortTermSuppressedCount;
        private int strongSuppressedCount;
        private int softSuppressedCount;
        private int rankedAfterSeenSuppression;
        private int rankedAfterDiversity;
        private int rankedAfterSemanticFilter;

        private HomeFeedDiagnosticsCollector(Long userId,
                                             int page,
                                             int size,
                                             String requestId,
                                             boolean personalized,
                                             boolean lightPagingMode,
                                             int recallLimit,
                                             int recallMultiplier,
                                             HomeQuotaExperiment quotaExperiment,
                                             int minSourceQuota,
                                             int fetchMultiplier,
                                             FeedSourceHealthTrackerService feedSourceHealthTrackerService,
                                             Set<String> explicitInterestTerms,
                                             FeedSemanticFilter semanticFilter) {
            this.feedSourceHealthTrackerService = feedSourceHealthTrackerService;
            List<String> explicitInterestSample = explicitInterestTerms == null
                    ? List.of()
                    : explicitInterestTerms.stream().limit(12).toList();
            this.request = new FeedHomeDiagnosticsRequest(
                    userId,
                    "home_feed",
                    page,
                    size,
                    requestId,
                    personalized,
                    lightPagingMode,
                    recallLimit,
                    recallMultiplier,
                    quotaExperiment.experimentId(),
                    quotaExperiment.bucket(),
                    minSourceQuota,
                    fetchMultiplier,
                    explicitInterestTerms == null ? 0 : explicitInterestTerms.size(),
                    explicitInterestSample
            );
            this.filters = new FeedHomeDiagnosticsFilters(
                    semanticFilter == null ? List.of() : semanticFilter.topicTerms(),
                    semanticFilter == null ? List.of() : semanticFilter.styleTerms(),
                    semanticFilter == null ? List.of() : semanticFilter.tagTerms()
            );
        }

        private void recordSource(String sourceKey,
                                  String reason,
                                  int score,
                                  int requestedFetchSize,
                                  int uniqueQuota,
                                  RecallOutcome outcome,
                                  MergeStats mergeStats) {
            int budgetMs = feedSourceHealthTrackerService.resolveBudgetMs(sourceKey);
            sources.add(new FeedHomeDiagnosticsSource(
                    sourceKey,
                    reason,
                    score,
                    requestedFetchSize,
                    uniqueQuota,
                    outcome.status(),
                    outcome.posts().size(),
                    mergeStats.uniqueAdded(),
                    mergeStats.mergedBefore(),
                    mergeStats.mergedAfter(),
                    budgetMs,
                    outcome.latencyMs(),
                    outcome.latencyMs() > budgetMs && !"skipped".equalsIgnoreCase(outcome.status()),
                    outcome.message()
            ));
        }

        private void recordSkippedSource(String sourceKey,
                                         String reason,
                                         int score,
                                         int requestedFetchSize,
                                         int uniqueQuota,
                                         String message) {
            int budgetMs = feedSourceHealthTrackerService.resolveBudgetMs(sourceKey);
            sources.add(new FeedHomeDiagnosticsSource(
                    sourceKey,
                    reason,
                    score,
                    requestedFetchSize,
                    uniqueQuota,
                    "skipped",
                    0,
                    0,
                    -1,
                    -1,
                    budgetMs,
                    0,
                    false,
                    message
            ));
        }

        private void recordMergedAfterPrimarySources(int count) {
            this.mergedAfterPrimarySources = count;
        }

        private void recordMergedAfterFallback(int count) {
            this.mergedAfterFallback = count;
        }

        private void recordExplicitBoost(int boostedItems, int mergedSize) {
            this.explicitBoostedItems = boostedItems;
            this.mergedAfterExplicitBoost = mergedSize;
        }

        private void recordSafety(int before, int after) {
            this.mergedBeforeSafetyFilter = before;
            this.mergedAfterSafetyFilter = after;
        }

        private void recordRankedBeforeSeenSuppression(int count) {
            this.rankedBeforeSeenSuppression = count;
        }

        private void recordRecentSeen(RecentSeenSuppressionResult result) {
            this.shortTermSuppressedCount = result.shortTermSuppressedCount();
            this.strongSuppressedCount = result.strongSuppressedCount();
            this.softSuppressedCount = result.softSuppressedCount();
            this.rankedAfterSeenSuppression = result.ranked().size();
        }

        private void recordAfterDiversity(int count) {
            this.rankedAfterDiversity = count;
        }

        private void recordAfterSemanticFilter(int count) {
            this.rankedAfterSemanticFilter = count;
        }

        private FeedHomeDiagnosticsResponse build(List<RankedPost> pageItems, int fromIndex, long finalRankedTotal) {
            List<FeedHomeDiagnosticsItem> itemSnapshots = new ArrayList<>(pageItems.size());
            Map<String, Integer> reasonCounts = new LinkedHashMap<>();
            for (int index = 0; index < pageItems.size(); index++) {
                RankedPost item = pageItems.get(index);
                itemSnapshots.add(new FeedHomeDiagnosticsItem(
                        fromIndex + index + 1,
                        item.post().getId(),
                        item.post().getAuthorId(),
                        item.post().getTitle(),
                        item.post().getTopicPath(),
                        item.reason(),
                        item.score(),
                        item.hotScore().doubleValue(),
                        item.createdAt()
                ));
                reasonCounts.merge(item.reason(), 1, Integer::sum);
            }
            List<FeedHomeDiagnosticsReasonMetric> reasonMix = reasonCounts.entrySet().stream()
                    .map(entry -> new FeedHomeDiagnosticsReasonMetric(entry.getKey(), entry.getValue()))
                    .toList();
            int pageWindowStart = pageItems.isEmpty() ? 0 : fromIndex + 1;
            int pageWindowEnd = pageItems.isEmpty() ? 0 : fromIndex + pageItems.size();
            FeedHomeDiagnosticsStage stage = new FeedHomeDiagnosticsStage(
                    mergedAfterPrimarySources,
                    mergedAfterFallback,
                    explicitBoostedItems,
                    mergedAfterExplicitBoost,
                    mergedBeforeSafetyFilter,
                    mergedAfterSafetyFilter,
                    rankedBeforeSeenSuppression,
                    shortTermSuppressedCount,
                    strongSuppressedCount,
                    softSuppressedCount,
                    rankedAfterSeenSuppression,
                    rankedAfterDiversity,
                    rankedAfterSemanticFilter,
                    pageWindowStart,
                    pageWindowEnd,
                    pageItems.size()
            );
            return new FeedHomeDiagnosticsResponse(
                    request,
                    filters,
                    stage,
                    List.copyOf(sources),
                    reasonMix,
                    itemSnapshots,
                    finalRankedTotal,
                    LocalDateTime.now()
            );
        }
    }
}
