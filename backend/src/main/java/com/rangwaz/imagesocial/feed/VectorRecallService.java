package com.rangwaz.imagesocial.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.config.RecommendationProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class VectorRecallService {

    private static final Logger log = LoggerFactory.getLogger(VectorRecallService.class);
    private static final int DEFAULT_RECALL_TIMEOUT_MS = 450;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 180;
    private static final int MAX_IN_FLIGHT_RECALL_CALLS = 2;
    private static final long RECALL_DEGRADE_BACKOFF_MILLIS = 12_000L;

    private final RecommendationProperties recommendationProperties;
    private final ObjectMapper objectMapper;
    private final Semaphore inFlightRecallRequests = new Semaphore(MAX_IN_FLIGHT_RECALL_CALLS);
    private volatile long recallDegradeUntilMillis = 0L;

    public VectorRecallService(RecommendationProperties recommendationProperties,
                               ObjectMapper objectMapper) {
        this.recommendationProperties = recommendationProperties;
        this.objectMapper = objectMapper;
    }

    public List<Long> recallPostIds(Long userId, int limit, List<Long> excludePostIds) {
        RecommendationProperties.DeepRank conf = recommendationProperties.deepRank();
        if (conf == null || !conf.enabled()) {
            return Collections.emptyList();
        }
        if (conf.recallEndpoint() == null || conf.recallEndpoint().isBlank()) {
            return Collections.emptyList();
        }
        if (isRecallTemporarilyUnavailable()) {
            return Collections.emptyList();
        }
        if (!tryAcquireInFlightSlot()) {
            return Collections.emptyList();
        }

        try {
            RestTemplate restTemplate = buildRestTemplate(conf.timeoutMs());
            HttpHeaders headers = buildHeaders(conf);

            Map<String, Object> body = Map.of(
                    "user_id", userId,
                    "limit", limit,
                    "exclude_post_ids", excludePostIds == null ? List.of() : excludePostIds
            );

            ResponseEntity<String> response = restTemplate.postForEntity(
                    conf.recallEndpoint(),
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return parsePostIds(response);

        } catch (Exception e) {
            markRecallTemporarilyUnavailable(e);
            log.warn("vector recall failed: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            inFlightRecallRequests.release();
        }
    }

    public List<Long> recallSimilarPostIds(Long postId, int limit, List<Long> excludePostIds) {
        RecommendationProperties.DeepRank conf = recommendationProperties.deepRank();
        if (conf == null || !conf.enabled()) {
            return Collections.emptyList();
        }
        if (conf.recallEndpoint() == null || conf.recallEndpoint().isBlank()) {
            return Collections.emptyList();
        }
        if (isRecallTemporarilyUnavailable()) {
            return Collections.emptyList();
        }
        if (!tryAcquireInFlightSlot()) {
            return Collections.emptyList();
        }

        try {
            RestTemplate restTemplate = buildRestTemplate(conf.timeoutMs());
            HttpHeaders headers = buildHeaders(conf);
            Map<String, Object> body = Map.of(
                    "post_id", postId,
                    "limit", limit,
                    "exclude_post_ids", excludePostIds == null ? List.of() : excludePostIds
            );
            ResponseEntity<String> response = restTemplate.postForEntity(
                    conf.recallEndpoint() + "/similar",
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return parsePostIds(response);
        } catch (Exception e) {
            markRecallTemporarilyUnavailable(e);
            log.warn("similar vector recall failed: {}", e.getMessage());
            return Collections.emptyList();
        } finally {
            inFlightRecallRequests.release();
        }
    }

    private HttpHeaders buildHeaders(RecommendationProperties.DeepRank conf) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (conf.authToken() != null && !conf.authToken().isBlank()) {
            headers.setBearerAuth(conf.authToken());
        }
        return headers;
    }

    private List<Long> parsePostIds(ResponseEntity<String> response) throws Exception {
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return Collections.emptyList();
        }

        Map<String, Object> payload = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        Object ids = payload.get("post_ids");
        if (!(ids instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .map(x -> {
                    if (x instanceof Number n) {
                        return n.longValue();
                    }
                    try {
                        return Long.parseLong(String.valueOf(x));
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(x -> x != null)
                .toList();
    }

    private RestTemplate buildRestTemplate(Integer timeoutMs) {
        int configuredTimeout = timeoutMs == null || timeoutMs <= 0 ? DEFAULT_RECALL_TIMEOUT_MS : timeoutMs;
        int timeout = Math.min(configuredTimeout, DEFAULT_RECALL_TIMEOUT_MS);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Math.min(timeout, DEFAULT_CONNECT_TIMEOUT_MS));
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
    }

    private boolean tryAcquireInFlightSlot() {
        try {
            return inFlightRecallRequests.tryAcquire(10, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean isRecallTemporarilyUnavailable() {
        return System.currentTimeMillis() < recallDegradeUntilMillis;
    }

    private void markRecallTemporarilyUnavailable(Exception exception) {
        long nowMillis = System.currentTimeMillis();
        long nextRecoverMillis = nowMillis + RECALL_DEGRADE_BACKOFF_MILLIS;
        long previous = recallDegradeUntilMillis;
        recallDegradeUntilMillis = Math.max(previous, nextRecoverMillis);
        if (nowMillis >= previous) {
            String message = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            log.warn(
                    "vector recall temporarily degraded, backoff={}ms, reason={}",
                    RECALL_DEGRADE_BACKOFF_MILLIS,
                    message
            );
        }
    }
}
