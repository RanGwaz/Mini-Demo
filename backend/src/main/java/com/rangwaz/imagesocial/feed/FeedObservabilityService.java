package com.rangwaz.imagesocial.feed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.domain.entity.FeedImpressionLog;
import com.rangwaz.imagesocial.domain.entity.FeedRequestLog;
import com.rangwaz.imagesocial.domain.mapper.FeedImpressionLogMapper;
import com.rangwaz.imagesocial.domain.mapper.FeedRequestLogMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FeedObservabilityService {
    private static final Logger log = LoggerFactory.getLogger(FeedObservabilityService.class);

    private final FeedRequestLogMapper requestLogMapper;
    private final FeedImpressionLogMapper impressionLogMapper;
    private final ObjectMapper objectMapper;

    public FeedObservabilityService(FeedRequestLogMapper requestLogMapper,
                                    FeedImpressionLogMapper impressionLogMapper,
                                    ObjectMapper objectMapper) {
        this.requestLogMapper = requestLogMapper;
        this.impressionLogMapper = impressionLogMapper;
        this.objectMapper = objectMapper;
    }

    public void recordHomeFeed(HomeFeedTrace trace) {
        try {
            FeedRequestLog requestLog = new FeedRequestLog();
            requestLog.setRequestId(trace.requestId());
            requestLog.setUserId(trace.userId());
            requestLog.setSurface(trace.surface());
            requestLog.setPageNo(trace.pageNo());
            requestLog.setPageSize(trace.pageSize());
            requestLog.setSeed(trace.seed());
            requestLog.setFiltersJson(toJson(trace.filters()));
            requestLog.setUserSegment(segment(trace.userId(), trace.pageNo()));
            requestLog.setExperimentId(trace.experimentId());
            requestLog.setExperimentBucket(trace.experimentBucket());
            requestLog.setTotalCandidates(safeInt(trace.totalCandidates()));
            requestLog.setReturnedCount(trace.pageItems().size());
            requestLog.setLatencyMs(Math.max(0L, trace.latency().toMillis()));
            requestLog.setDegraded(trace.degraded());
            requestLogMapper.insert(requestLog);

            for (int index = 0; index < trace.pageItems().size(); index++) {
                RankedPost rankedPost = trace.pageItems().get(index);
                FeedImpressionLog impression = new FeedImpressionLog();
                impression.setRequestId(trace.requestId());
                impression.setUserId(trace.userId());
                impression.setPostId(rankedPost.post().getId());
                impression.setRankPosition(trace.fromIndex() + index + 1);
                impression.setRecallSource(toRecallSource(rankedPost.reason()));
                impression.setRankScore(BigDecimal.valueOf(rankedPost.score()).setScale(4, RoundingMode.HALF_UP));
                impression.setChannelCode(rankedPost.post().getChannelCode());
                impression.setTopicNames(shorten(rankedPost.post().getTags(), 512));
                impression.setReason(shorten(rankedPost.reason(), 255));
                impressionLogMapper.insert(impression);
            }
        } catch (Exception exception) {
            log.warn("feed observability write failed: {}", exception.getMessage());
        }
    }

    private String segment(Long userId, int pageNo) {
        if (userId == null) {
            return "anonymous";
        }
        if (pageNo <= 1) {
            return "active_or_new";
        }
        return "returning_pagination";
    }

    private String toRecallSource(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown";
        }
        String normalized = reason.trim().toLowerCase()
                .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "_")
                .replaceAll("_+", "_");
        return shorten(normalized, 128);
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private int safeInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return Math.max(0, (int) value);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public record HomeFeedTrace(String requestId,
                                Long userId,
                                String surface,
                                int pageNo,
                                int pageSize,
                                String seed,
                                Map<String, Object> filters,
                                String experimentId,
                                String experimentBucket,
                                long totalCandidates,
                                int fromIndex,
                                List<RankedPost> pageItems,
                                Duration latency,
                                boolean degraded) {

        public HomeFeedTrace {
            filters = filters == null ? new LinkedHashMap<>() : filters;
        }
    }
}
