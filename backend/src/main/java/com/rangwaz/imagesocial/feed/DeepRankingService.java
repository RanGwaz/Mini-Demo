package com.rangwaz.imagesocial.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.feature.BehaviorSequenceEvent;
import com.rangwaz.imagesocial.feature.FeatureService;
import com.rangwaz.imagesocial.feature.PostRealtimeMetrics;
import com.rangwaz.imagesocial.domain.entity.Post;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 深度学习排序服务：调用外部模型推理服务返回候选分数。
 */
@Service
public class DeepRankingService {

    private static final Logger log = LoggerFactory.getLogger(DeepRankingService.class);
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 350;
    private static final int DEFAULT_READ_TIMEOUT_MS = 1200;
    private static final long RANK_DEGRADE_BACKOFF_MILLIS = 15_000L;
    private static final int MAX_IN_FLIGHT_RANK_CALLS = 2;
    private static final int MAX_REMOTE_CANDIDATES = 120;

    private final RecommendationProperties recommendationProperties;
    private final ObjectMapper objectMapper;
    private final FeatureService featureService;
    private final Semaphore inFlightRankRequests = new Semaphore(MAX_IN_FLIGHT_RANK_CALLS);
    private volatile long rankDegradeUntilMillis = 0L;

    public DeepRankingService(RecommendationProperties recommendationProperties,
                              ObjectMapper objectMapper,
                              FeatureService featureService) {
        this.recommendationProperties = recommendationProperties;
        this.objectMapper = objectMapper;
        this.featureService = featureService;
    }

    public Map<Long, Double> score(Long userId, List<Post> candidates) {
        return score(userId, candidates, "home_feed");
    }

    public Map<Long, Double> score(Long userId, List<Post> candidates, String scene) {
        return score(userId, candidates, scene, 1);
    }

    public Map<Long, Double> score(Long userId, List<Post> candidates, String scene, int pageNo) {
        return score(userId, candidates, scene, pageNo, "");
    }

    public Map<Long, Double> score(Long userId, List<Post> candidates, String scene, int pageNo, String experimentId) {
        RecommendationProperties.DeepRank conf = recommendationProperties.deepRank();
        if (conf == null || !conf.enabled()) {
            return Collections.emptyMap();
        }
        if (userId == null || candidates == null || candidates.isEmpty()) {
            return Collections.emptyMap();
        }
        if (conf.endpoint() == null || conf.endpoint().isBlank()) {
            log.warn("deep-rank 已启用但 endpoint 为空，跳过深度排序");
            return Collections.emptyMap();
        }
        if (isRankTemporarilyUnavailable()) {
            return Collections.emptyMap();
        }
        if (!tryAcquireInFlightSlot()) {
            return Collections.emptyMap();
        }

        try {
            RestTemplate restTemplate = buildRestTemplate(conf.timeoutMs());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (conf.authToken() != null && !conf.authToken().isBlank()) {
                headers.setBearerAuth(conf.authToken());
            }

            Map<String, Object> requestBody = new LinkedHashMap<>();
            List<BehaviorSequenceEvent> behaviorSequence = featureService.getRecentBehaviorSequence(userId, 200);
            Map<Long, PostRealtimeMetrics> realtimeMetrics = featureService.getPostRealtimeMetrics(
                    candidates.stream().map(Post::getId).toList()
            );
            requestBody.put("user_id", userId);
            requestBody.put("scene", scene == null || scene.isBlank() ? "home_feed" : scene.trim());
            requestBody.put("page_no", Math.max(1, pageNo));
            requestBody.put("experiment_id", experimentId == null ? "" : experimentId.trim());
            requestBody.put(
                    "behavior_sequence",
                    behaviorSequence.stream().map(BehaviorSequenceEvent::toPayload).toList()
            );
            requestBody.put(
                    "candidates",
                    candidates.stream()
                            .limit(MAX_REMOTE_CANDIDATES)
                            .map(post -> toCandidate(post, realtimeMetrics.get(post.getId())))
                            .toList()
            );
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(conf.endpoint(), request, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("deep-rank 调用失败: status={}", response.getStatusCode());
                return Collections.emptyMap();
            }

            Map<String, Object> payload = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
            Object scoresObj = payload.get("scores");
            if (!(scoresObj instanceof List<?> scoresList)) {
                return Collections.emptyMap();
            }

            return scoresList.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .map(item -> {
                        Long postId = toLong(item.get("post_id"));
                        Double score = toDouble(item.get("score"));
                        if (postId == null || score == null) {
                            return null;
                        }
                        return Map.entry(postId, score);
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left));
        } catch (Exception exception) {
            markRankTemporarilyUnavailable("rank-call", exception);
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn("deep-rank 调用异常，降级为规则排序: {}", message);
            return Collections.emptyMap();
        } finally {
            inFlightRankRequests.release();
        }
    }

    private RestTemplate buildRestTemplate(Integer timeoutMs) {
        int timeout = timeoutMs == null || timeoutMs <= 0 ? DEFAULT_READ_TIMEOUT_MS : timeoutMs;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeout, DEFAULT_CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private boolean tryAcquireInFlightSlot() {
        try {
            return inFlightRankRequests.tryAcquire(15, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isRankTemporarilyUnavailable() {
        return System.currentTimeMillis() < rankDegradeUntilMillis;
    }

    private void markRankTemporarilyUnavailable(String operation, Exception exception) {
        long nowMillis = System.currentTimeMillis();
        long nextRecoverMillis = nowMillis + RANK_DEGRADE_BACKOFF_MILLIS;
        long previous = rankDegradeUntilMillis;
        rankDegradeUntilMillis = Math.max(previous, nextRecoverMillis);
        if (nowMillis >= previous) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn(
                    "deep-rank temporarily degraded during {}, backoff={}ms, reason={}",
                    operation,
                    RANK_DEGRADE_BACKOFF_MILLIS,
                    message
            );
        }
    }

    private Map<String, Object> toCandidate(Post post, PostRealtimeMetrics realtimeMetrics) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("post_id", post.getId());
        candidate.put("author_id", post.getAuthorId());
        candidate.put("title", post.getTitle() == null ? "" : post.getTitle());
        candidate.put("content", post.getContent() == null ? "" : post.getContent());
        candidate.put("cover_url", post.getCoverUrl() == null ? "" : post.getCoverUrl());
        candidate.put("thumb_url", post.getThumbUrl() == null ? "" : post.getThumbUrl());
        candidate.put("topic_cluster_key", post.getTopicClusterKey() == null ? "" : post.getTopicClusterKey());
        candidate.put("subtopic_cluster_key", post.getSubtopicClusterKey() == null ? "" : post.getSubtopicClusterKey());
        candidate.put("hot_score", post.getHotScore() == null ? BigDecimal.ZERO : post.getHotScore());
        candidate.put("like_count", post.getLikeCount() == null ? 0 : post.getLikeCount());
        candidate.put("favorite_count", post.getFavoriteCount() == null ? 0 : post.getFavoriteCount());
        candidate.put("comment_count", post.getCommentCount() == null ? 0 : post.getCommentCount());
        candidate.put("view_count", post.getViewCount() == null ? 0 : post.getViewCount());
        candidate.put("tags", post.getTags() == null ? "" : post.getTags());
        candidate.put("topic_path", post.getTopicPath() == null ? "" : post.getTopicPath());
        candidate.put("semantic_tags", post.getSemanticTags() == null ? "" : post.getSemanticTags());
        candidate.put("style_tags", post.getStyleTags() == null ? "" : post.getStyleTags());
        candidate.put("quality_score", post.getQualityScore() == null ? BigDecimal.ZERO : post.getQualityScore());
        candidate.put("aesthetic_score", post.getAestheticScore() == null ? BigDecimal.ZERO : post.getAestheticScore());
        candidate.put("safety_score", post.getSafetyScore() == null ? BigDecimal.ONE : post.getSafetyScore());
        candidate.put("created_at", post.getCreatedAt() == null ? LocalDateTime.MIN.toString() : post.getCreatedAt().toString());
        candidate.put(
                "realtime_metrics",
                realtimeMetrics == null ? PostRealtimeMetrics.empty().toPayload() : realtimeMetrics.toPayload()
        );
        return candidate;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignore) {
            return null;
        }
    }
}
