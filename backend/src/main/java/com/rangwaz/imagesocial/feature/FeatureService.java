package com.rangwaz.imagesocial.feature;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.config.RecommendationProperties;
import com.rangwaz.imagesocial.domain.entity.UserEvent;
import com.rangwaz.imagesocial.domain.mapper.UserEventMapper;
import com.rangwaz.imagesocial.feature.entity.PostFeature;
import com.rangwaz.imagesocial.feature.entity.UserFeature;
import com.rangwaz.imagesocial.feature.mapper.PostFeatureMapper;
import com.rangwaz.imagesocial.feature.mapper.UserFeatureMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.time.ZoneId;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 特征服务：为推荐系统提供用户和帖子的离线/在线特征。
 *
 * <p>特征来源：
 * <ul>
 *   <li>离线特征（MySQL）：user_features / post_features，由 Python 脚本每日计算写入</li>
 *   <li>在线行为序列（Redis）：由 Python 脚本写入历史快照，
 *       由 Java EventConsumers 实时追加最新行为</li>
 * </ul>
 *
 * <p>Key 格式（与 Python 脚本保持一致）：
 * <pre>
 *   user:recent_view:{userId}              -> List<String> 最近曝光帖子 ID
 *   user:recent_click:{userId}             -> List<String> 最近点击帖子 ID
 *   user:recent_detail_view:{userId}       -> List<String> 最近详情浏览帖子 ID
 *   user:recent_like:{userId}              -> List<String> 最近点赞帖子 ID
 *   user:recent_fav:{userId}               -> List<String> 最近收藏帖子 ID
 *   user:recent_comment:{userId}           -> List<String> 最近评论帖子 ID
 *   user:recent_negative_feedback:{userId} -> List<String> 最近不感兴趣帖子 ID
 *   user:recent_hidden_post:{userId}       -> List<String> 最近隐藏帖子 ID
 *   user:recent_share:{userId}             -> List<String> 最近分享帖子 ID
 *   user:recent_follow:{userId}            -> List<String> 最近关注作者 ID
 * </pre>
 */
@Service
public class FeatureService {
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);

    private static final String KEY_RECENT_VIEW = "user:recent_view:";
    private static final String KEY_RECENT_CLICK = "user:recent_click:";
    private static final String KEY_RECENT_DETAIL_VIEW = "user:recent_detail_view:";
    private static final String KEY_RECENT_LIKE = "user:recent_like:";
    private static final String KEY_RECENT_FAV = "user:recent_fav:";
    private static final String KEY_RECENT_COMMENT = "user:recent_comment:";
    private static final String KEY_RECENT_NEGATIVE_FEEDBACK = "user:recent_negative_feedback:";
    private static final String KEY_RECENT_HIDDEN_POST = "user:recent_hidden_post:";
    private static final String KEY_RECENT_SHARE = "user:recent_share:";
    private static final String KEY_RECENT_FOLLOW = "user:recent_follow:";
    private static final String KEY_RECENT_EXPOSURE = "user:recent_exposure:";
    private static final String KEY_POST_METRICS_PREFIX = "post:metrics:";
    private static final String KEY_BEHAVIOR_SEQUENCE = "user:behavior_sequence:";
    private static final String KEY_ONLINE_TOPIC_INTEREST = "user:online_interest:topic:";
    private static final String KEY_ONLINE_STYLE_INTEREST = "user:online_interest:style:";
    private static final String KEY_ONLINE_TAG_INTEREST = "user:online_interest:tag:";
    private static final String KEY_ONLINE_INTEREST_META = "user:online_interest:meta:";
    private static final String KEY_REALTIME_INTEREST_PREFIX = "user:%d:interests:%s";
    private static final String FIELD_TOPIC_LAST_DECAY_AT = "topic_last_decay_at";
    private static final String FIELD_STYLE_LAST_DECAY_AT = "style_last_decay_at";
    private static final String FIELD_TAG_LAST_DECAY_AT = "tag_last_decay_at";
    private static final int SEQ_MAX_LEN = 20;
    private static final int BEHAVIOR_SEQUENCE_MAX_LEN = 200;
    private static final long SEQ_EXPIRE_SEC = 86400L * 2;
    private static final int DEFAULT_ONLINE_INTEREST_EXPIRE_DAYS = 3;
    private static final int DEFAULT_ONLINE_INTEREST_ZSET_MAX = 160;
    private static final int DEFAULT_ONLINE_INTEREST_FETCH_WINDOW = 56;
    private static final double DEFAULT_ONLINE_INTEREST_MIN_SCORE = 0.12d;
    private static final long REDIS_DEGRADE_BACKOFF_MILLIS = 30_000L;
    private static final int RECENT_EXPOSURE_MAX = 3_000;
    private static final long RECENT_EXPOSURE_EXPIRE_SEC = 86400L * 3;
    private static final long POST_METRICS_1H_EXPIRE_SEC = 7200L;
    private static final long POST_METRICS_24H_EXPIRE_SEC = 86400L * 2;
    private static final long POST_METRICS_7D_EXPIRE_SEC = 86400L * 8;
    private static final Pattern TERM_SPLITTER = Pattern.compile("[,|/\\\\>\\s_-]+");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Set<String> BEHAVIOR_EVENT_TYPES = Set.of(
            "FEED_EXPOSURE",
            "POST_CLICK",
            "POST_DETAIL_VIEW",
            "POST_LIKE",
            "POST_FAVORITE",
            "POST_COMMENT",
            "POST_SHARE",
            "NOT_INTERESTED",
            "POST_NEGATIVE_FEEDBACK",
            "POST_HIDE"
    );

    private final UserFeatureMapper userFeatureMapper;
    private final PostFeatureMapper postFeatureMapper;
    private final UserEventMapper userEventMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties recommendationProperties;
    private volatile long redisDegradeUntilMillis = 0L;

    public FeatureService(UserFeatureMapper userFeatureMapper,
                          PostFeatureMapper postFeatureMapper,
                          UserEventMapper userEventMapper,
                          RedisTemplate<String, String> redisTemplate,
                          ObjectMapper objectMapper,
                          RecommendationProperties recommendationProperties) {
        this.userFeatureMapper = userFeatureMapper;
        this.postFeatureMapper = postFeatureMapper;
        this.userEventMapper = userEventMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.recommendationProperties = recommendationProperties;
    }

    public UserFeature getUserFeature(Long userId) {
        return userFeatureMapper.selectById(userId);
    }

    public PostFeature getPostFeature(Long postId) {
        return postFeatureMapper.selectById(postId);
    }

    public List<PostFeature> getPostFeatures(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyList();
        }
        return postFeatureMapper.selectBatchIds(postIds);
    }

    public List<Long> getUserRecentViews(Long userId) {
        return readLongList(KEY_RECENT_VIEW + userId);
    }

    public List<Long> getUserRecentExposureIds(Long userId, int limit) {
        if (userId == null || limit <= 0 || isRedisTemporarilyUnavailable()) {
            return List.of();
        }
        try {
            Set<String> raw = redisTemplate.opsForZSet().reverseRange(KEY_RECENT_EXPOSURE + userId, 0, Math.max(0, limit - 1));
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.stream()
                    .map(value -> {
                        try {
                            return Long.parseLong(value);
                        } catch (Exception ignored) {
                            return null;
                        }
                    })
                    .filter(value -> value != null && value > 0L)
                    .toList();
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("getUserRecentExposureIds", exception);
            return List.of();
        }
    }

    public List<Long> getUserRecentClicks(Long userId) {
        return readLongList(KEY_RECENT_CLICK + userId);
    }

    public List<Long> getUserRecentDetailViews(Long userId) {
        return readLongList(KEY_RECENT_DETAIL_VIEW + userId);
    }

    public List<Long> getUserRecentLikes(Long userId) {
        return readLongList(KEY_RECENT_LIKE + userId);
    }

    public List<Long> getUserRecentFavorites(Long userId) {
        return readLongList(KEY_RECENT_FAV + userId);
    }

    public List<Long> getUserRecentComments(Long userId) {
        return readLongList(KEY_RECENT_COMMENT + userId);
    }

    public List<Long> getUserRecentNegativeFeedbacks(Long userId) {
        return readLongList(KEY_RECENT_NEGATIVE_FEEDBACK + userId);
    }

    public List<Long> getUserRecentHiddenPosts(Long userId) {
        return readLongList(KEY_RECENT_HIDDEN_POST + userId);
    }

    public List<Long> getUserRecentShares(Long userId) {
        return readLongList(KEY_RECENT_SHARE + userId);
    }

    public List<Long> getUserRecentFollows(Long userId) {
        return readLongList(KEY_RECENT_FOLLOW + userId);
    }

    public void recordFeedExposures(Long userId, Collection<Long> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty() || isRedisTemporarilyUnavailable()) {
            return;
        }
        List<Long> normalized = postIds.stream()
                .filter(value -> value != null && value > 0L)
                .distinct()
                .limit(200)
                .toList();
        if (normalized.isEmpty()) {
            return;
        }
        long nowMillis = System.currentTimeMillis();
        String exposureKey = KEY_RECENT_EXPOSURE + userId;
        try {
            int offset = 0;
            for (Long postId : normalized) {
                String postIdValue = String.valueOf(postId);
                redisTemplate.opsForZSet().add(exposureKey, postIdValue, nowMillis + offset++);
                appendToSequence(KEY_RECENT_VIEW, userId, postId);
                incrementPostRealtimeMetric("FEED_EXPOSURE", postId);
            }
            Long size = redisTemplate.opsForZSet().size(exposureKey);
            if (size != null && size > RECENT_EXPOSURE_MAX) {
                redisTemplate.opsForZSet().removeRange(exposureKey, 0, size - RECENT_EXPOSURE_MAX - 1);
            }
            redisTemplate.expire(exposureKey, RECENT_EXPOSURE_EXPIRE_SEC, TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("recordFeedExposures", exception);
        }
    }

    public Map<Long, PostRealtimeMetrics> getPostRealtimeMetrics(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty() || isRedisTemporarilyUnavailable()) {
            return Map.of();
        }
        List<Long> normalized = postIds.stream()
                .filter(value -> value != null && value > 0L)
                .distinct()
                .limit(240)
                .toList();
        if (normalized.isEmpty()) {
            return Map.of();
        }
        Map<Long, PostRealtimeMetrics> result = new HashMap<>();
        try {
            for (Long postId : normalized) {
                Map<String, Long> oneHour = readPostMetricWindow(postId, "1h");
                Map<String, Long> twentyFourHour = readPostMetricWindow(postId, "24h");
                PostRealtimeMetrics metrics = new PostRealtimeMetrics(
                        metric(oneHour, "exposure"),
                        metric(oneHour, "click"),
                        metric(oneHour, "detail_view"),
                        positiveMetric(oneHour),
                        metric(oneHour, "negative") + metric(oneHour, "hide"),
                        metric(twentyFourHour, "exposure"),
                        metric(twentyFourHour, "click"),
                        metric(twentyFourHour, "detail_view"),
                        positiveMetric(twentyFourHour),
                        metric(twentyFourHour, "negative") + metric(twentyFourHour, "hide")
                );
                if (!metrics.isEmpty()) {
                    result.put(postId, metrics);
                }
            }
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("getPostRealtimeMetrics", exception);
            return Map.of();
        }
        return result;
    }

    public List<Long> getUserStrongPositiveSignals(Long userId) {
        java.util.LinkedHashSet<Long> merged = new java.util.LinkedHashSet<>();
        merged.addAll(getUserRecentComments(userId));
        merged.addAll(getUserRecentFavorites(userId));
        merged.addAll(getUserRecentLikes(userId));
        merged.addAll(getUserRecentDetailViews(userId));
        merged.addAll(getUserRecentClicks(userId));
        merged.addAll(getUserRecentShares(userId));
        return merged.stream().limit(SEQ_MAX_LEN).toList();
    }

    public List<Long> getUserNegativeSignals(Long userId) {
        java.util.LinkedHashSet<Long> merged = new java.util.LinkedHashSet<>();
        merged.addAll(getUserRecentHiddenPosts(userId));
        merged.addAll(getUserRecentNegativeFeedbacks(userId));
        return merged.stream().limit(SEQ_MAX_LEN).toList();
    }

    public List<BehaviorSequenceEvent> getRecentBehaviorSequence(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, BEHAVIOR_SEQUENCE_MAX_LEN));
        List<BehaviorSequenceEvent> redisEvents = readBehaviorSequenceFromRedis(userId, safeLimit);
        if (redisEvents.size() >= safeLimit) {
            return redisEvents;
        }
        List<BehaviorSequenceEvent> dbEvents = readBehaviorSequenceFromDb(userId, safeLimit);
        if (!dbEvents.isEmpty()) {
            writeBehaviorSequenceToRedis(userId, dbEvents);
            return dbEvents;
        }
        return redisEvents;
    }

    public List<String> getOnlineInterestTerms(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<String> realtimeTerms = readRealtimeInterestTerms(userId, limit, "1h");
        if (!realtimeTerms.isEmpty()) {
            return realtimeTerms;
        }
        realtimeTerms = readRealtimeInterestTerms(userId, limit, "6h");
        if (!realtimeTerms.isEmpty()) {
            return realtimeTerms;
        }
        return readRealtimeInterestTerms(userId, limit, "24h");
    }

    public void ingestRealtimeEvent(String eventType,
                                    Long userId,
                                    String targetType,
                                    Long targetId,
                                    Map<String, Object> payload) {
        if (eventType == null || eventType.isBlank() || userId == null) {
            return;
        }
        String normalizedEventType = eventType.trim().toUpperCase();

        if ("POST".equalsIgnoreCase(targetType) && targetId != null) {
            incrementPostRealtimeMetric(normalizedEventType, targetId);
            switch (normalizedEventType) {
                case "FEED_EXPOSURE" -> appendToSequence(KEY_RECENT_VIEW, userId, targetId);
                case "POST_CLICK" -> appendToSequence(KEY_RECENT_CLICK, userId, targetId);
                case "POST_DETAIL_VIEW" -> appendToSequence(KEY_RECENT_DETAIL_VIEW, userId, targetId);
                case "POST_LIKE" -> appendToSequence(KEY_RECENT_LIKE, userId, targetId);
                case "POST_FAVORITE" -> appendToSequence(KEY_RECENT_FAV, userId, targetId);
                case "POST_COMMENT" -> appendToSequence(KEY_RECENT_COMMENT, userId, targetId);
                case "POST_SHARE" -> appendToSequence(KEY_RECENT_SHARE, userId, targetId);
                case "NOT_INTERESTED", "POST_NEGATIVE_FEEDBACK" -> appendToSequence(KEY_RECENT_NEGATIVE_FEEDBACK, userId, targetId);
                case "POST_HIDE" -> appendToSequence(KEY_RECENT_HIDDEN_POST, userId, targetId);
                default -> {
                    // Ignore other post event types for sequence ingestion.
                }
            }
            appendBehaviorEvent(normalizedEventType, userId, targetId, payload);
            double interestWeight = interestWeight(normalizedEventType);
            if (interestWeight != 0.0d) {
                maybeDecayOnlineInterests(userId);
                updateOnlineInterestByPost(userId, targetId, interestWeight);
            }
            return;
        }

        if ("USER".equalsIgnoreCase(targetType) && targetId != null) {
            if ("USER_FOLLOW".equals(normalizedEventType)) {
                appendToSequence(KEY_RECENT_FOLLOW, userId, targetId);
            }
        }
    }

    private void appendBehaviorEvent(String eventType,
                                     Long userId,
                                     Long targetId,
                                     Map<String, Object> payload) {
        if (eventType == null || eventType.isBlank() || userId == null || targetId == null) {
            return;
        }
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        BehaviorSequenceEvent event = new BehaviorSequenceEvent(
                targetId,
                eventType.trim().toUpperCase(Locale.ROOT),
                toEpochMillis(pickValue(payload, "event_ts", "eventTs", "created_at_ts", "createdAtTs"), System.currentTimeMillis()),
                toLongValue(pickValue(payload, "dwell_ms", "dwellMs", "stay_ms", "stayMs"), 0L),
                toIntValue(pickValue(payload, "rank_position", "rankPosition", "position"), 0),
                toStringValue(pickValue(payload, "surface", "scene")),
                toIntValue(pickValue(payload, "page_no", "pageNo"), 0),
                toStringValue(pickValue(payload, "device_type", "deviceType")),
                toStringValue(pickValue(payload, "recall_source", "recallSource", "reason"))
        );
        String key = KEY_BEHAVIOR_SEQUENCE + userId;
        try {
            redisTemplate.opsForList().leftPush(key, objectMapper.writeValueAsString(event.toPayload()));
            redisTemplate.opsForList().trim(key, 0, BEHAVIOR_SEQUENCE_MAX_LEN - 1);
            redisTemplate.expire(key, SEQ_EXPIRE_SEC, TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("appendBehaviorEvent", exception);
        }
    }

    private List<BehaviorSequenceEvent> readBehaviorSequenceFromRedis(Long userId, int limit) {
        if (isRedisTemporarilyUnavailable()) {
            return List.of();
        }
        String key = KEY_BEHAVIOR_SEQUENCE + userId;
        try {
            List<String> rows = redisTemplate.opsForList().range(key, 0, Math.max(0, limit - 1));
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<BehaviorSequenceEvent> events = new ArrayList<>(rows.size());
            for (String row : rows) {
                if (row == null || row.isBlank()) {
                    continue;
                }
                try {
                    Map<String, Object> payload = objectMapper.readValue(row, MAP_TYPE);
                    BehaviorSequenceEvent event = parseBehaviorEventPayload(payload);
                    if (event != null) {
                        events.add(event);
                    }
                } catch (Exception ignored) {
                    // Ignore malformed event payload.
                }
            }
            return events;
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("readBehaviorSequenceFromRedis", exception);
            return List.of();
        }
    }

    public void appendToSequence(String keyPrefix, Long userId, Long postId) {
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        String key = keyPrefix + userId;
        try {
            String postIdValue = String.valueOf(postId);
            redisTemplate.opsForList().remove(key, 0, postIdValue);
            redisTemplate.opsForList().leftPush(key, postIdValue);
            redisTemplate.opsForList().trim(key, 0, SEQ_MAX_LEN - 1);
            redisTemplate.expire(key, SEQ_EXPIRE_SEC, TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("appendToSequence", exception);
        }
    }

    private void incrementPostRealtimeMetric(String eventType, Long postId) {
        if (eventType == null || eventType.isBlank() || postId == null || postId <= 0L || isRedisTemporarilyUnavailable()) {
            return;
        }
        String field = metricField(eventType.trim().toUpperCase(Locale.ROOT));
        if (field == null) {
            return;
        }
        try {
            incrementPostMetricWindow("1h", postId, field, POST_METRICS_1H_EXPIRE_SEC);
            incrementPostMetricWindow("24h", postId, field, POST_METRICS_24H_EXPIRE_SEC);
            incrementPostMetricWindow("7d", postId, field, POST_METRICS_7D_EXPIRE_SEC);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("incrementPostRealtimeMetric", exception);
        }
    }

    private void incrementPostMetricWindow(String window, Long postId, String field, long ttlSeconds) {
        String key = KEY_POST_METRICS_PREFIX + window + ":" + postId;
        redisTemplate.opsForHash().increment(key, field, 1L);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    private Map<String, Long> readPostMetricWindow(Long postId, String window) {
        if (postId == null || postId <= 0L || window == null || window.isBlank()) {
            return Map.of();
        }
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(KEY_POST_METRICS_PREFIX + window + ":" + postId);
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : raw.entrySet()) {
            String field = toStringValue(entry.getKey());
            if (field.isBlank()) {
                continue;
            }
            result.put(field, Math.max(0L, toLongValue(entry.getValue(), 0L)));
        }
        return result;
    }

    private long metric(Map<String, Long> metrics, String field) {
        if (metrics == null || metrics.isEmpty() || field == null) {
            return 0L;
        }
        return Math.max(0L, metrics.getOrDefault(field, 0L));
    }

    private long positiveMetric(Map<String, Long> metrics) {
        return metric(metrics, "like")
                + metric(metrics, "favorite")
                + metric(metrics, "comment")
                + metric(metrics, "share");
    }

    private String metricField(String eventType) {
        return switch (eventType) {
            case "FEED_EXPOSURE" -> "exposure";
            case "POST_CLICK" -> "click";
            case "POST_DETAIL_VIEW" -> "detail_view";
            case "POST_LIKE" -> "like";
            case "POST_FAVORITE" -> "favorite";
            case "POST_COMMENT" -> "comment";
            case "POST_SHARE" -> "share";
            case "NOT_INTERESTED", "POST_NEGATIVE_FEEDBACK" -> "negative";
            case "POST_HIDE" -> "hide";
            default -> null;
        };
    }

    private List<BehaviorSequenceEvent> readBehaviorSequenceFromDb(Long userId, int limit) {
        try {
            List<UserEvent> rows = userEventMapper.selectList(
                    new LambdaQueryWrapper<UserEvent>()
                            .eq(UserEvent::getUserId, userId)
                            .eq(UserEvent::getTargetType, "POST")
                            .in(UserEvent::getEventType, BEHAVIOR_EVENT_TYPES)
                            .isNotNull(UserEvent::getTargetId)
                            .orderByDesc(UserEvent::getCreatedAt)
                            .last("LIMIT " + limit)
            );
            if (rows == null || rows.isEmpty()) {
                return List.of();
            }
            List<BehaviorSequenceEvent> result = new ArrayList<>(rows.size());
            for (UserEvent row : rows) {
                if (row == null || row.getTargetId() == null || row.getEventType() == null || row.getCreatedAt() == null) {
                    continue;
                }
                long eventTs = row.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                result.add(
                        new BehaviorSequenceEvent(
                                row.getTargetId(),
                                row.getEventType().trim().toUpperCase(Locale.ROOT),
                                eventTs,
                                row.getDwellMs() == null ? 0L : row.getDwellMs(),
                                row.getRankPosition() == null ? 0 : row.getRankPosition(),
                                row.getSurface(),
                                row.getPageNo() == null ? 0 : row.getPageNo(),
                                row.getDeviceType(),
                                row.getRecallSource()
                        )
                );
            }
            return result;
        } catch (Exception exception) {
            log.warn("load behavior sequence from db failed for user {}: {}", userId, exception.getMessage());
            return List.of();
        }
    }

    private void writeBehaviorSequenceToRedis(Long userId, List<BehaviorSequenceEvent> events) {
        if (userId == null || events == null || events.isEmpty()) {
            return;
        }
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        String key = KEY_BEHAVIOR_SEQUENCE + userId;
        try {
            List<String> payloads = events.stream()
                    .limit(BEHAVIOR_SEQUENCE_MAX_LEN)
                    .map(BehaviorSequenceEvent::toPayload)
                    .map(payload -> {
                        try {
                            return objectMapper.writeValueAsString(payload);
                        } catch (Exception ignored) {
                            return null;
                        }
                    })
                    .filter(value -> value != null && !value.isBlank())
                    .toList();
            if (payloads.isEmpty()) {
                return;
            }
            redisTemplate.delete(key);
            redisTemplate.opsForList().rightPushAll(key, payloads);
            redisTemplate.opsForList().trim(key, 0, BEHAVIOR_SEQUENCE_MAX_LEN - 1);
            redisTemplate.expire(key, SEQ_EXPIRE_SEC, TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("writeBehaviorSequenceToRedis", exception);
        }
    }

    private BehaviorSequenceEvent parseBehaviorEventPayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        Long targetId = toLongValue(payload.get("target_id"), 0L);
        String eventType = toStringValue(payload.get("event_type")).toUpperCase(Locale.ROOT);
        Long eventTs = toLongValue(payload.get("event_ts"), 0L);
        if (targetId <= 0L || eventType.isBlank() || eventTs <= 0L) {
            return null;
        }
        return new BehaviorSequenceEvent(
                targetId,
                eventType,
                eventTs,
                toLongValue(payload.get("dwell_ms"), 0L),
                toIntValue(payload.get("rank_position"), 0),
                toStringValue(payload.get("surface")),
                toIntValue(payload.get("page_no"), 0),
                toStringValue(payload.get("device_type")),
                toStringValue(payload.get("recall_source"))
        );
    }

    private Object pickValue(Map<String, Object> payload, String... keys) {
        if (payload == null || payload.isEmpty() || keys == null || keys.length == 0) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = payload.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private long toEpochMillis(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            long result = number.longValue();
            return result > 0 ? result : defaultValue;
        }
        try {
            long result = Long.parseLong(String.valueOf(value).trim());
            return result > 0 ? result : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private long toLongValue(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double parseDouble(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int toIntValue(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String toStringValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? "" : text;
    }

    public static String keyRecentView() { return KEY_RECENT_VIEW; }

    public static String keyRecentClick() { return KEY_RECENT_CLICK; }

    public static String keyRecentDetailView() { return KEY_RECENT_DETAIL_VIEW; }

    public static String keyRecentLike() { return KEY_RECENT_LIKE; }

    public static String keyRecentFav() { return KEY_RECENT_FAV; }

    public static String keyRecentComment() { return KEY_RECENT_COMMENT; }

    public static String keyRecentNegativeFeedback() { return KEY_RECENT_NEGATIVE_FEEDBACK; }

    public static String keyRecentHiddenPost() { return KEY_RECENT_HIDDEN_POST; }

    public static String keyRecentShare() { return KEY_RECENT_SHARE; }

    public static String keyRecentFollow() { return KEY_RECENT_FOLLOW; }

    private void mergeInterestTerms(String key, double sourceWeight, Map<String, Double> mergedScores) {
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples;
        try {
            tuples = redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, onlineInterestFetchWindow() - 1);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("mergeInterestTerms", exception);
            return;
        }
        if (tuples == null || tuples.isEmpty()) {
            return;
        }
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            String value = tuple.getValue();
            Double score = tuple.getScore();
            if (value == null || value.isBlank() || score == null || score <= 0.0d) {
                continue;
            }
            mergedScores.merge(value, score * sourceWeight, Double::sum);
        }
    }

    private List<String> readRealtimeInterestTerms(Long userId, int limit, String window) {
        if (isRedisTemporarilyUnavailable()) {
            return List.of();
        }
        try {
            String key = String.format(KEY_REALTIME_INTEREST_PREFIX, userId, window);
            Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            return raw.entrySet().stream()
                    .map(entry -> Map.entry(String.valueOf(entry.getKey()), parseDouble(entry.getValue(), 0.0d)))
                    .filter(entry -> !entry.getKey().isBlank() && entry.getValue() > 0.0d)
                    .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .toList();
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("readRealtimeInterestTerms", exception);
            return List.of();
        }
    }

    private void updateOnlineInterestByPost(Long userId, Long postId, double eventWeight) {
        PostFeature postFeature = postFeatureMapper.selectById(postId);
        if (postFeature == null) {
            return;
        }

        Set<String> topicTerms = new LinkedHashSet<>();
        appendTerms(topicTerms, postFeature.getTopicPath(), 6);
        appendTerms(topicTerms, postFeature.getSemanticTags(), 4);

        Set<String> styleTerms = new LinkedHashSet<>();
        appendTerms(styleTerms, postFeature.getStyleTags(), 8);
        appendTerms(styleTerms, postFeature.getSemanticTags(), 4);

        Set<String> tagTerms = new LinkedHashSet<>();
        appendTerms(tagTerms, postFeature.getTags(), 12);
        appendTerms(tagTerms, postFeature.getSemanticTags(), 8);
        appendTerms(tagTerms, postFeature.getTopicPath(), 4);

        Set<String> mergedTerms = new LinkedHashSet<>();
        mergedTerms.addAll(tagTerms);
        mergedTerms.addAll(topicTerms);
        mergedTerms.addAll(styleTerms);
        applyRealtimeInterestDelta(userId, mergedTerms, eventWeight);
    }

    private void applyRealtimeInterestDelta(Long userId, Set<String> terms, double eventWeight) {
        if (terms == null || terms.isEmpty() || eventWeight <= 0.0d || isRedisTemporarilyUnavailable()) {
            return;
        }
        try {
            applyRealtimeInterestWindow(userId, "1h", terms, eventWeight, 3600L);
            applyRealtimeInterestWindow(userId, "6h", terms, eventWeight, 21600L);
            applyRealtimeInterestWindow(userId, "24h", terms, eventWeight, 86400L);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("applyRealtimeInterestDelta", exception);
        }
    }

    private void applyRealtimeInterestWindow(Long userId, String window, Set<String> terms, double eventWeight, long ttlSeconds) {
        String key = String.format(KEY_REALTIME_INTEREST_PREFIX, userId, window);
        int count = 0;
        for (String term : terms) {
            if (term == null || term.isBlank()) {
                continue;
            }
            redisTemplate.opsForHash().increment(key, term, eventWeight);
            count++;
            if (count >= 12) {
                break;
            }
        }
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
    }

    private void applyInterestDelta(String key, Set<String> terms, double delta) {
        if (terms == null || terms.isEmpty() || delta == 0.0d) {
            return;
        }
        if (isRedisTemporarilyUnavailable()) {
            return;
        }

        try {
            double minScore = onlineInterestMinScore();
            for (String term : terms) {
                if (term == null || term.isBlank()) {
                    continue;
                }
                Double next = redisTemplate.opsForZSet().incrementScore(key, term, delta);
                if (next != null && next <= minScore) {
                    redisTemplate.opsForZSet().remove(key, term);
                }
            }

            Long size = redisTemplate.opsForZSet().size(key);
            int maxSize = onlineInterestZsetMax();
            if (size != null && size > maxSize) {
                long removeCount = size - maxSize;
                redisTemplate.opsForZSet().removeRange(key, 0, removeCount - 1);
            }
            redisTemplate.expire(key, onlineInterestExpireSec(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("applyInterestDelta", exception);
        }
    }

    private void appendTerms(Set<String> target, String raw, int maxAdds) {
        if (target == null || raw == null || raw.isBlank() || maxAdds <= 0) {
            return;
        }
        int added = 0;
        for (String token : TERM_SPLITTER.split(raw.toLowerCase())) {
            String normalized = normalizeTerm(token);
            if (normalized.length() < 2 || normalized.chars().allMatch(Character::isDigit)) {
                continue;
            }
            if (target.add(normalized)) {
                added++;
            }
            if (added >= maxAdds) {
                break;
            }
        }
    }

    private String normalizeTerm(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    private double interestWeight(String eventType) {
        return switch (eventType) {
            case "POST_FAVORITE" -> 5.0d;
            case "POST_LIKE" -> 3.2d;
            case "POST_COMMENT" -> 3.8d;
            case "POST_SHARE" -> 3.0d;
            case "POST_DETAIL_VIEW" -> 1.6d;
            case "POST_CLICK" -> 1.2d;
            // Exposure is high-frequency and should not synchronously trigger per-item profile updates.
            case "FEED_EXPOSURE" -> 0.0d;
            case "POST_UNFAVORITE" -> -3.2d;
            case "POST_UNLIKE" -> -2.0d;
            case "NOT_INTERESTED", "POST_NEGATIVE_FEEDBACK", "POST_HIDE" -> -5.0d;
            default -> 0.0d;
        };
    }

    private void maybeDecayOnlineInterests(Long userId) {
        if (userId == null) {
            return;
        }
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        RecommendationProperties.OnlineInterest conf = recommendationProperties.onlineInterest();
        if (conf == null || !conf.decayEnabled()) {
            return;
        }

        long nowMillis = System.currentTimeMillis();
        String metaKey = KEY_ONLINE_INTEREST_META + userId;
        try {
            maybeDecayInterestKey(
                    KEY_ONLINE_TOPIC_INTEREST + userId,
                    metaKey,
                    FIELD_TOPIC_LAST_DECAY_AT,
                    nowMillis,
                    conf
            );
            maybeDecayInterestKey(
                    KEY_ONLINE_STYLE_INTEREST + userId,
                    metaKey,
                    FIELD_STYLE_LAST_DECAY_AT,
                    nowMillis,
                    conf
            );
            maybeDecayInterestKey(
                    KEY_ONLINE_TAG_INTEREST + userId,
                    metaKey,
                    FIELD_TAG_LAST_DECAY_AT,
                    nowMillis,
                    conf
            );
            redisTemplate.expire(metaKey, onlineInterestExpireSec(), TimeUnit.SECONDS);
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("maybeDecayOnlineInterests", exception);
        }
    }

    private void maybeDecayInterestKey(String key,
                                       String metaKey,
                                       String decayField,
                                       long nowMillis,
                                       RecommendationProperties.OnlineInterest conf) {
        if (isRedisTemporarilyUnavailable()) {
            return;
        }
        long intervalMillis = Math.max(1L, (long) conf.decayIntervalMinutes()) * 60_000L;
        long lastDecayAt = parseEpochMillis(redisTemplate.opsForHash().get(metaKey, decayField), nowMillis);
        if (nowMillis - lastDecayAt < intervalMillis) {
            return;
        }

        double elapsedHours = Math.max(0.0d, (nowMillis - lastDecayAt) / 3_600_000.0d);
        double halfLifeHours = Math.max(0.1d, conf.halfLifeHours());
        double decayFactor = Math.pow(0.5d, elapsedHours / halfLifeHours);

        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        if (tuples != null && !tuples.isEmpty() && decayFactor < 0.9999d) {
            double minScore = onlineInterestMinScore();
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                String term = tuple.getValue();
                Double score = tuple.getScore();
                if (term == null || term.isBlank() || score == null) {
                    continue;
                }
                double decayedScore = score * decayFactor;
                if (decayedScore <= minScore) {
                    redisTemplate.opsForZSet().remove(key, term);
                } else {
                    redisTemplate.opsForZSet().add(key, term, decayedScore);
                }
            }
        }
        redisTemplate.opsForHash().put(metaKey, decayField, String.valueOf(nowMillis));
    }

    private long parseEpochMillis(Object raw, long fallbackMillis) {
        if (raw == null) {
            return fallbackMillis;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (Exception ignored) {
            return fallbackMillis;
        }
    }

    private int onlineInterestFetchWindow() {
        RecommendationProperties.OnlineInterest conf = recommendationProperties.onlineInterest();
        if (conf == null || conf.fetchWindow() <= 0) {
            return DEFAULT_ONLINE_INTEREST_FETCH_WINDOW;
        }
        return Math.max(8, conf.fetchWindow());
    }

    private int onlineInterestZsetMax() {
        RecommendationProperties.OnlineInterest conf = recommendationProperties.onlineInterest();
        if (conf == null || conf.zsetMaxSize() <= 0) {
            return DEFAULT_ONLINE_INTEREST_ZSET_MAX;
        }
        return Math.max(24, conf.zsetMaxSize());
    }

    private long onlineInterestExpireSec() {
        RecommendationProperties.OnlineInterest conf = recommendationProperties.onlineInterest();
        int expireDays = conf == null || conf.expireDays() <= 0
                ? DEFAULT_ONLINE_INTEREST_EXPIRE_DAYS
                : conf.expireDays();
        return Math.max(1L, expireDays) * 86400L;
    }

    private double onlineInterestMinScore() {
        RecommendationProperties.OnlineInterest conf = recommendationProperties.onlineInterest();
        if (conf == null || conf.minScore() <= 0.0d) {
            return DEFAULT_ONLINE_INTEREST_MIN_SCORE;
        }
        return conf.minScore();
    }

    private List<Long> readLongList(String key) {
        if (isRedisTemporarilyUnavailable()) {
            return Collections.emptyList();
        }
        try {
            List<String> raw = redisTemplate.opsForList().range(key, 0, SEQ_MAX_LEN - 1);
            if (raw == null || raw.isEmpty()) {
                return Collections.emptyList();
            }
            return raw.stream()
                    .map(s -> {
                        try {
                            return Long.parseLong(s);
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    })
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
        } catch (Exception exception) {
            markRedisTemporarilyUnavailable("readLongList", exception);
            return Collections.emptyList();
        }
    }

    private boolean isRedisTemporarilyUnavailable() {
        return System.currentTimeMillis() < redisDegradeUntilMillis;
    }

    private void markRedisTemporarilyUnavailable(String operation, Exception exception) {
        long nowMillis = System.currentTimeMillis();
        long nextRecoverMillis = nowMillis + REDIS_DEGRADE_BACKOFF_MILLIS;
        long previous = redisDegradeUntilMillis;
        redisDegradeUntilMillis = Math.max(previous, nextRecoverMillis);
        if (nowMillis >= previous) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn(
                    "redis unavailable during {}, degraded mode for {}ms: {}",
                    operation,
                    REDIS_DEGRADE_BACKOFF_MILLIS,
                    message
            );
        }
    }
}
