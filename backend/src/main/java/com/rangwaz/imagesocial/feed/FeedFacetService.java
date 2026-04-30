package com.rangwaz.imagesocial.feed;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicClusterMapper;
import com.rangwaz.imagesocial.feed.dto.FeedFacetItem;
import com.rangwaz.imagesocial.feed.dto.FeedFacetsResponse;
import com.rangwaz.imagesocial.user.UserInterestService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FeedFacetService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final int DEFAULT_LIMIT = 24;
    private static final int MAX_LIMIT = 80;

    private final TopicClusterMapper topicClusterMapper;
    private final PostMapper postMapper;
    private final UserInterestService userInterestService;
    private final ObjectMapper objectMapper;

    public FeedFacetService(TopicClusterMapper topicClusterMapper,
                            PostMapper postMapper,
                            UserInterestService userInterestService,
                            ObjectMapper objectMapper) {
        this.topicClusterMapper = topicClusterMapper;
        this.postMapper = postMapper;
        this.userInterestService = userInterestService;
        this.objectMapper = objectMapper;
    }

    public FeedFacetsResponse listFeedFacets(Long userId, int limit, int recentDays) {
        int safeLimit = Math.max(6, Math.min(MAX_LIMIT, limit <= 0 ? DEFAULT_LIMIT : limit));
        int safeRecentDays = Math.max(1, Math.min(30, recentDays));
        Set<String> selectedFacetKeys = normalizeKeys(userInterestService.listActiveFacetKeys(userId));

        List<TopicFacetRow> facetRows = topicClusterMapper.selectFacetRows(
                safeRecentDays,
                2,
                Math.max(safeLimit * 4, safeLimit)
        );
        if (facetRows == null) {
            facetRows = List.of();
        }

        List<FeedFacetItem> topics;
        List<FeedFacetItem> subtopics;
        String taxonomyVersion;

        if (!facetRows.isEmpty()) {
            taxonomyVersion = facetRows.stream()
                    .map(TopicFacetRow::getTaxonomyVersion)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse("taxonomy:unknown");
            List<FeedFacetItem> all = new ArrayList<>(facetRows.size());
            for (TopicFacetRow row : facetRows) {
                all.add(new FeedFacetItem(
                        row.getClusterKey(),
                        normalizeLabel(row.getClusterLabel(), row.getClusterKey()),
                        row.getParentClusterKey(),
                        row.getClusterLevel(),
                        safeInt(row.getPostCount()),
                        safeInt(row.getRecentPostCount()),
                        trendScore(row.getHotScoreSum(), row.getRecentPostCount(), row.getPostCount()),
                        selectedFacetKeys.contains(normalizeKey(row.getClusterKey())),
                        parseKeywords(row.getKeywordsJson(), row.getClusterLabel())
                ));
            }
            topics = all.stream()
                    .filter(item -> item.level() != null && item.level() == 1)
                    .sorted(Comparator.comparingDouble(FeedFacetItem::trendScore).reversed())
                    .limit(safeLimit)
                    .toList();
            subtopics = all.stream()
                    .filter(item -> item.level() != null && item.level() >= 2)
                    .sorted(Comparator.comparingDouble(FeedFacetItem::trendScore).reversed())
                    .limit(safeLimit * 2L)
                    .toList();
        } else {
            taxonomyVersion = "taxonomy:fallback";
            List<FeedFacetItem> fallbackTopics = postMapper.selectFallbackTopicFacets(
                            safeRecentDays,
                            safeLimit
                    ).stream()
                    .map(row -> new FeedFacetItem(
                            row.getClusterKey(),
                            normalizeLabel(row.getClusterLabel(), row.getClusterKey()),
                            null,
                            1,
                            safeInt(row.getPostCount()),
                            safeInt(row.getPostCount()),
                            trendScore(row.getHotScoreSum(), row.getPostCount(), row.getPostCount()),
                            selectedFacetKeys.contains(normalizeKey(row.getClusterKey())),
                            inferKeywords(row.getClusterLabel(), row.getClusterKey())
                    ))
                    .sorted(Comparator.comparingDouble(FeedFacetItem::trendScore).reversed())
                    .toList();
            topics = fallbackTopics;
            subtopics = List.of();
        }

        return new FeedFacetsResponse(
                topics,
                subtopics,
                new ArrayList<>(selectedFacetKeys),
                taxonomyVersion,
                LocalDateTime.now()
        );
    }

    private Set<String> normalizeKeys(List<String> keys) {
        Set<String> normalized = new LinkedHashSet<>();
        if (keys == null || keys.isEmpty()) {
            return normalized;
        }
        for (String key : keys) {
            String value = normalizeKey(key);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String normalizeLabel(String label, String fallback) {
        if (label == null || label.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        return label.trim();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : Math.max(value, 0);
    }

    private double trendScore(java.math.BigDecimal hotScoreSum, Integer recentPostCount, Integer postCount) {
        double hotScore = hotScoreSum == null ? 0.0d : hotScoreSum.doubleValue();
        int recent = safeInt(recentPostCount);
        int total = safeInt(postCount);
        return hotScore * 0.60d + recent * 6.0d + total * 0.12d;
    }

    private List<String> parseKeywords(String keywordsJson, String fallbackLabel) {
        if (keywordsJson == null || keywordsJson.isBlank()) {
            return inferKeywords(fallbackLabel, fallbackLabel);
        }
        try {
            List<String> values = objectMapper.readValue(keywordsJson, STRING_LIST);
            if (values == null || values.isEmpty()) {
                return inferKeywords(fallbackLabel, fallbackLabel);
            }
            return values.stream()
                    .filter(item -> item != null && !item.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(6)
                    .toList();
        } catch (Exception ignore) {
            return inferKeywords(fallbackLabel, fallbackLabel);
        }
    }

    private List<String> inferKeywords(String label, String fallback) {
        String base = label == null || label.isBlank() ? (fallback == null ? "" : fallback) : label;
        if (base.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(base.toLowerCase(Locale.ROOT).split("[-_/\\s>]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .distinct()
                .limit(4)
                .toList();
    }
}
