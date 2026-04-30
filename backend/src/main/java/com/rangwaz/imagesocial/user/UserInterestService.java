package com.rangwaz.imagesocial.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.domain.entity.UserInterestSubscription;
import com.rangwaz.imagesocial.domain.mapper.UserInterestSubscriptionMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.user.dto.UserInterestFacetPayload;
import com.rangwaz.imagesocial.user.dto.UserInterestFacetView;
import com.rangwaz.imagesocial.user.dto.UserInterestsResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInterestService {

    private static final int MAX_ACTIVE_INTERESTS = 80;
    private static final Set<String> ALLOWED_FACET_TYPES = Set.of("TOPIC", "SUBTOPIC", "TAG", "STYLE");

    private final UserInterestSubscriptionMapper userInterestSubscriptionMapper;
    private final EventService eventService;

    public UserInterestService(UserInterestSubscriptionMapper userInterestSubscriptionMapper,
                               EventService eventService) {
        this.userInterestSubscriptionMapper = userInterestSubscriptionMapper;
        this.eventService = eventService;
    }

    public List<UserInterestSubscription> listActiveSubscriptions(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userInterestSubscriptionMapper.selectActiveByUserId(userId, MAX_ACTIVE_INTERESTS);
    }

    public List<String> listActiveFacetKeys(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return listActiveSubscriptions(userId).stream()
                .map(UserInterestSubscription::getFacetKey)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    public Set<String> listActiveFacetKeySet(Long userId) {
        return new LinkedHashSet<>(listActiveFacetKeys(userId));
    }

    public UserInterestsResponse getUserInterests(Long userId) {
        List<UserInterestSubscription> rows = listActiveSubscriptions(userId);
        return toResponse(userId, rows);
    }

    @Transactional
    public UserInterestsResponse replaceUserInterests(Long userId, List<UserInterestFacetPayload> requestedFacets) {
        List<UserInterestSubscription> normalized = normalizeRequestedFacets(userId, requestedFacets);

        userInterestSubscriptionMapper.delete(new LambdaQueryWrapper<UserInterestSubscription>()
                .eq(UserInterestSubscription::getUserId, userId));
        for (UserInterestSubscription row : normalized) {
            userInterestSubscriptionMapper.insert(row);
        }

        if (normalized.isEmpty()) {
            eventService.publish(
                    "USER_INTEREST_CLEAR",
                    userId,
                    "USER",
                    userId,
                    Map.of("facetCount", 0, "source", "manual")
            );
            return new UserInterestsResponse(userId, List.of(), LocalDateTime.now());
        }

        eventService.publish(
                "USER_INTEREST_SUBSCRIBE",
                userId,
                "USER",
                userId,
                Map.of(
                        "facetCount", normalized.size(),
                        "facetKeys", normalized.stream().map(UserInterestSubscription::getFacetKey).toList(),
                        "source", "manual"
                )
        );
        return toResponse(userId, normalized);
    }

    private UserInterestsResponse toResponse(Long userId, List<UserInterestSubscription> rows) {
        LocalDateTime latestUpdatedAt = rows.stream()
                .map(UserInterestSubscription::getUpdatedAt)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(LocalDateTime.now());
        List<UserInterestFacetView> facets = rows.stream()
                .map(row -> new UserInterestFacetView(
                        row.getFacetType(),
                        row.getFacetKey(),
                        row.getFacetLabel(),
                        row.getWeight()))
                .toList();
        return new UserInterestsResponse(userId, facets, latestUpdatedAt);
    }

    private List<UserInterestSubscription> normalizeRequestedFacets(Long userId,
                                                                    List<UserInterestFacetPayload> requestedFacets) {
        if (requestedFacets == null || requestedFacets.isEmpty()) {
            return List.of();
        }

        Map<String, UserInterestSubscription> deduplicated = new LinkedHashMap<>();
        for (UserInterestFacetPayload payload : requestedFacets) {
            if (payload == null) {
                continue;
            }

            String normalizedKey = normalizeFacetKey(payload.facetKey());
            if (normalizedKey.isBlank()) {
                continue;
            }
            String normalizedType = normalizeFacetType(payload.facetType());
            String dedupeKey = normalizedType + "::" + normalizedKey;

            UserInterestSubscription row = new UserInterestSubscription();
            row.setUserId(userId);
            row.setFacetType(normalizedType);
            row.setFacetKey(normalizedKey);
            row.setFacetLabel(normalizeFacetLabel(payload.facetLabel(), normalizedKey));
            row.setWeight(normalizeWeight(payload.weight()));
            row.setSource("MANUAL");
            row.setStatus("ACTIVE");

            deduplicated.put(dedupeKey, row);
            if (deduplicated.size() >= MAX_ACTIVE_INTERESTS) {
                break;
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private String normalizeFacetType(String facetType) {
        if (facetType == null || facetType.isBlank()) {
            return "TOPIC";
        }
        String normalized = facetType.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_FACET_TYPES.contains(normalized) ? normalized : "TOPIC";
    }

    private String normalizeFacetKey(String facetKey) {
        if (facetKey == null || facetKey.isBlank()) {
            return "";
        }
        String normalized = facetKey.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.length() > 128) {
            return normalized.substring(0, 128);
        }
        return normalized;
    }

    private String normalizeFacetLabel(String facetLabel, String fallback) {
        if (facetLabel == null || facetLabel.isBlank()) {
            return fallback;
        }
        String normalized = facetLabel.trim().replaceAll("\\s+", " ");
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private BigDecimal normalizeWeight(BigDecimal weight) {
        if (weight == null) {
            return BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal bounded = weight.max(new BigDecimal("0.1")).min(new BigDecimal("5.0"));
        return bounded.setScale(4, RoundingMode.HALF_UP);
    }
}
