package com.rangwaz.imagesocial.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostFavorite;
import com.rangwaz.imagesocial.domain.entity.PostLike;
import com.rangwaz.imagesocial.domain.mapper.PostFavoriteMapper;
import com.rangwaz.imagesocial.domain.mapper.PostLikeMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicClusterMapper;
import com.rangwaz.imagesocial.feature.FeatureService;
import com.rangwaz.imagesocial.feature.entity.UserFeature;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.user.UserInterestService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class FeedRecallService {

    private static final int MIN_HOT_POOL_SIZE = 180;
    private static final int MAX_RECENT_POSITIVE_SEEDS = 2;
    private static final int RECENT_POSITIVE_DB_FALLBACK = 6;
    private static final int MIN_VECTOR_PER_SEED = 8;
    private static final int MAX_SEMANTIC_CLUSTER_LOOKUP_TERMS = 16;
    private static final int MAX_SEMANTIC_CLUSTER_KEYS = 20;
    private static final int MAX_TOPIC_PREFIXES = 8;
    private static final Pattern CONTENT_TERM_SPLITTER = Pattern.compile("[,|/\\\\>\\s_-]+");

    private final PostMapper postMapper;
    private final TopicClusterMapper topicClusterMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final PostService postService;
    private final FeatureService featureService;
    private final VectorRecallService vectorRecallService;
    private final UserInterestService userInterestService;

    public FeedRecallService(PostMapper postMapper,
                             TopicClusterMapper topicClusterMapper,
                             PostLikeMapper postLikeMapper,
                             PostFavoriteMapper postFavoriteMapper,
                             PostService postService,
                             FeatureService featureService,
                             VectorRecallService vectorRecallService,
                             UserInterestService userInterestService) {
        this.postMapper = postMapper;
        this.topicClusterMapper = topicClusterMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.postService = postService;
        this.featureService = featureService;
        this.vectorRecallService = vectorRecallService;
        this.userInterestService = userInterestService;
    }

    public List<Post> recallHot(int limit, String seed) {
        int safeLimit = Math.max(1, limit);
        List<Post> pool = postMapper.selectHotPosts(Math.max(safeLimit * 3, MIN_HOT_POOL_SIZE));
        return sliceStableWindow(pool, safeLimit, seed, "hot");
    }

    public List<Post> recallSocial(Long userId, int limit) {
        return postMapper.selectSocialPosts(userId, limit);
    }

    public List<Post> recallByContent(Long userId, int limit) {
        Set<String> tags = new LinkedHashSet<>();
        UserFeature userFeature = featureService.getUserFeature(userId);
        if (userFeature != null) {
            appendTerms(tags, userFeature.getTopInterestTags(), 5);
            appendTerms(tags, userFeature.getTopInterestTopics(), 4);
            appendTerms(tags, userFeature.getPreferredStyles(), 4);
        }
        userInterestService.listActiveFacetKeys(userId).stream()
                .map(this::normalizeTerm)
                .filter(value -> !value.isBlank())
                .limit(8)
                .forEach(tags::add);
        featureService.getOnlineInterestTerms(userId, 10).stream()
                .map(this::normalizeTerm)
                .filter(value -> !value.isBlank())
                .forEach(tags::add);

        if (tags.isEmpty()) {
            Set<Long> interestPostIds = new LinkedHashSet<>();
            interestPostIds.addAll(postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                            .eq(PostLike::getUserId, userId)
                            .orderByDesc(PostLike::getCreatedAt)
                            .last("limit 10"))
                    .stream()
                    .map(PostLike::getPostId)
                    .toList());
            interestPostIds.addAll(postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                            .eq(PostFavorite::getUserId, userId)
                            .orderByDesc(PostFavorite::getCreatedAt)
                            .last("limit 10"))
                    .stream()
                    .map(PostFavorite::getPostId)
                    .toList());
            tags.addAll(interestPostIds.stream()
                    .map(postService::requirePost)
                    .map(postService::parseTags)
                    .flatMap(Collection::stream)
                    .limit(5)
                    .collect(LinkedHashSet::new, Set::add, Set::addAll));
        }

        if (tags.isEmpty()) {
            return List.of();
        }
        return recallBySemanticTerms(userId, tags, limit);
    }

    public List<Post> recallByOnlineProfile(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<String> onlineTerms = featureService.getOnlineInterestTerms(userId, 24).stream()
                .map(this::normalizeTerm)
                .filter(value -> !value.isBlank())
                .toList();
        if (onlineTerms.isEmpty()) {
            return List.of();
        }
        return recallBySemanticTerms(userId, onlineTerms, limit);
    }

    public List<Post> recallByExplicitInterests(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<String> facetKeys = userInterestService.listActiveFacetKeys(userId).stream()
                .map(this::normalizeTerm)
                .filter(value -> !value.isBlank())
                .limit(16)
                .toList();
        if (facetKeys.isEmpty()) {
            return List.of();
        }
        return recallBySemanticTerms(userId, facetKeys, limit);
    }

    public List<Post> recallByRecentPositiveFeedback(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }

        List<Long> seedPostIds = collectRecentPositiveSeeds(userId);
        if (seedPostIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> excludes = new LinkedHashSet<>();
        excludes.addAll(featureService.getUserNegativeSignals(userId));
        excludes.addAll(seedPostIds);

        int seedCount = Math.max(1, Math.min(seedPostIds.size(), MAX_RECENT_POSITIVE_SEEDS));
        int perSeedLimit = Math.max(MIN_VECTOR_PER_SEED, limit / seedCount);
        LinkedHashSet<Long> recalledIds = new LinkedHashSet<>();
        int usedSeeds = 0;
        for (Long seedPostId : seedPostIds) {
            if (usedSeeds >= MAX_RECENT_POSITIVE_SEEDS || recalledIds.size() >= limit * 2) {
                break;
            }
            usedSeeds++;
            List<Long> ids = vectorRecallService.recallSimilarPostIds(
                    seedPostId,
                    perSeedLimit,
                    excludes.stream().toList()
            );
            for (Long id : ids) {
                if (id == null || Objects.equals(id, seedPostId)) {
                    continue;
                }
                recalledIds.add(id);
                if (recalledIds.size() >= limit * 2) {
                    break;
                }
            }
        }

        List<Post> vectorPosts = orderedPublicPosts(recalledIds.stream().toList(), Math.max(limit, 24));
        if (vectorPosts.size() >= Math.min(limit, 10)) {
            return vectorPosts.stream().limit(limit).toList();
        }

        List<Post> seedPosts = orderedPublicPosts(seedPostIds, Math.max(MAX_RECENT_POSITIVE_SEEDS, 8));
        Set<String> terms = collectSeedTerms(seedPosts);
        if (terms.isEmpty()) {
            return vectorPosts.stream().limit(limit).toList();
        }

        List<Post> semanticFallback = recallBySemanticTerms(
                userId,
                terms,
                Math.max(limit, 48),
                excludes
        );

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<Post> merged = new ArrayList<>();
        for (Post post : vectorPosts) {
            if (seen.add(post.getId())) {
                merged.add(post);
            }
            if (merged.size() >= limit) {
                return merged;
            }
        }
        for (Post post : semanticFallback) {
            if (post == null || !isPublicApproved(post)) {
                continue;
            }
            if (seen.add(post.getId())) {
                merged.add(post);
            }
            if (merged.size() >= limit) {
                break;
            }
        }
        return merged;
    }

    public List<Post> recallByI2I(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }

        List<Long> seedPostIds = collectRecentPositiveSeeds(userId);
        if (seedPostIds.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<Long> excludes = new LinkedHashSet<>();
        excludes.addAll(seedPostIds);
        excludes.addAll(featureService.getUserNegativeSignals(userId));
        excludes.addAll(featureService.getUserRecentExposureIds(userId, Math.max(48, limit)));
        excludes.addAll(featureService.getUserRecentViews(userId).stream().limit(Math.max(24, limit / 2)).toList());

        return postMapper.selectI2INeighborPosts(
                userId,
                seedPostIds.stream().limit(12).toList(),
                excludes.stream().filter(Objects::nonNull).distinct().toList(),
                Math.max(limit, 24)
        ).stream().limit(limit).toList();
    }

    public List<Post> recallBySemanticTerms(Long userId, Collection<String> terms, int limit) {
        return recallBySemanticTerms(userId, terms, limit, List.of());
    }

    public List<Post> recallBySemanticTerms(Long userId,
                                            Collection<String> terms,
                                            int limit,
                                            Collection<Long> excludeIds) {
        return recallBySemanticTerms(userId, terms, limit, excludeIds, "semantic");
    }

    public List<Post> recallBySemanticTerms(Long userId,
                                            Collection<String> terms,
                                            int limit,
                                            Collection<Long> excludeIds,
                                            String seed) {
        if (limit <= 0) {
            return List.of();
        }

        LinkedHashSet<String> normalizedTerms = normalizeDistinctTerms(terms, MAX_SEMANTIC_CLUSTER_LOOKUP_TERMS);
        if (normalizedTerms.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> clusterKeys = new LinkedHashSet<>(normalizedTerms);
        List<String> resolvedClusterKeys = topicClusterMapper.selectClusterKeysByTerms(
                new ArrayList<>(normalizedTerms),
                MAX_SEMANTIC_CLUSTER_KEYS
        );
        if (resolvedClusterKeys != null) {
            clusterKeys.addAll(resolvedClusterKeys);
        }

        List<String> topicPrefixes = toTopicPrefixes(normalizedTerms, MAX_TOPIC_PREFIXES);
        if (clusterKeys.isEmpty() && topicPrefixes.isEmpty()) {
            return List.of();
        }

        List<String> termPatterns = normalizedTerms.stream()
                .map(this::toLikePattern)
                .toList();
        List<Long> normalizedExcludeIds = normalizeExcludeIds(excludeIds);
        List<Post> richMatchedPosts = postMapper.selectSemanticMatchedPosts(
                userId == null ? -1L : userId,
                termPatterns,
                topicPrefixes,
                new ArrayList<>(clusterKeys),
                normalizedExcludeIds,
                stableHash(seed, "semantic-rich"),
                limit
        );
        if (!richMatchedPosts.isEmpty()) {
            return richMatchedPosts;
        }

        return postMapper.selectClusterPosts(
                userId == null ? -1L : userId,
                new ArrayList<>(clusterKeys),
                topicPrefixes,
                normalizedExcludeIds,
                limit
        );
    }

    private void appendTerms(Set<String> target, String raw, int limit) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        target.addAll(Arrays.stream(raw.split(","))
                .map(this::normalizeTerm)
                .filter(value -> !value.isBlank())
                .limit(limit)
                .toList());
    }

    private String normalizeTerm(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private LinkedHashSet<String> normalizeDistinctTerms(Collection<String> terms, int maxSize) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (terms == null || terms.isEmpty() || maxSize <= 0) {
            return normalized;
        }
        for (String term : terms) {
            String value = normalizeTerm(term);
            if (value.isBlank()) {
                continue;
            }
            normalized.add(value);
            if (normalized.size() >= maxSize) {
                break;
            }
        }
        return normalized;
    }

    private List<String> toTopicPrefixes(Collection<String> terms, int maxSize) {
        LinkedHashSet<String> prefixes = new LinkedHashSet<>();
        if (terms == null || terms.isEmpty() || maxSize <= 0) {
            return List.of();
        }
        for (String term : terms) {
            addTopicPrefix(prefixes, term);
            addTopicPrefix(prefixes, normalizeTerm(term).replaceAll("\\s*/\\s*", "/").replace(' ', '/'));
            if (prefixes.size() >= maxSize) {
                break;
            }
        }
        return prefixes.stream().limit(maxSize).toList();
    }

    private String toLikePattern(String term) {
        String normalized = normalizeTerm(term);
        if (normalized.isBlank()) {
            return "%";
        }
        return "%" + normalized.replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private void addTopicPrefix(Set<String> prefixes, String raw) {
        String normalized = normalizeTerm(raw).replaceAll("/+", "/");
        if (normalized.isBlank()) {
            return;
        }
        prefixes.add(normalized);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex > 1) {
            prefixes.add(normalized.substring(0, slashIndex));
        }
    }

    private List<Long> normalizeExcludeIds(Collection<Long> excludeIds) {
        if (excludeIds == null || excludeIds.isEmpty()) {
            return List.of();
        }
        return excludeIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public List<Post> recallExplore(Long userId, int limit, String seed) {
        long total = postMapper.countExplorePosts(userId == null ? -1L : userId);
        if (total <= 0) {
            return List.of();
        }

        int safeLimit = Math.max(1, limit);
        int maxOffset = Math.max(0, (int) total - safeLimit);
        int offset = maxOffset == 0 ? 0 : Math.floorMod(stableHash(seed, "explore"), maxOffset + 1);
        return postMapper.selectExplorePostsWindow(userId == null ? -1L : userId, offset, safeLimit);
    }

    public List<Post> recallByVector(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }

        LinkedHashSet<Long> excludes = new LinkedHashSet<>();
        excludes.addAll(featureService.getUserStrongPositiveSignals(userId));
        excludes.addAll(featureService.getUserNegativeSignals(userId));
        List<Long> ids = vectorRecallService.recallPostIds(userId, limit, excludes.stream().toList());
        return orderedPublicPosts(ids, limit);
    }

    private List<Long> collectRecentPositiveSeeds(Long userId) {
        LinkedHashSet<Long> seeds = new LinkedHashSet<>();
        seeds.addAll(featureService.getUserRecentFavorites(userId));
        seeds.addAll(featureService.getUserRecentLikes(userId));
        seeds.addAll(featureService.getUserRecentComments(userId));
        seeds.addAll(featureService.getUserRecentShares(userId));
        seeds.addAll(featureService.getUserRecentDetailViews(userId).stream().limit(6).toList());

        seeds.addAll(postFavoriteMapper.selectList(new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
                        .orderByDesc(PostFavorite::getCreatedAt)
                        .last("limit " + RECENT_POSITIVE_DB_FALLBACK))
                .stream()
                .map(PostFavorite::getPostId)
                .toList());
        seeds.addAll(postLikeMapper.selectList(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
                        .orderByDesc(PostLike::getCreatedAt)
                        .last("limit " + RECENT_POSITIVE_DB_FALLBACK))
                .stream()
                .map(PostLike::getPostId)
                .toList());

        return seeds.stream()
                .filter(Objects::nonNull)
                .limit(24)
                .toList();
    }

    private Set<String> collectSeedTerms(List<Post> seedPosts) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (Post post : seedPosts) {
            appendTextTerms(terms, post.getTopicPath(), 6);
            appendTextTerms(terms, post.getTags(), 8);
            appendTextTerms(terms, post.getSemanticTags(), 8);
            appendTextTerms(terms, post.getStyleTags(), 8);
            appendTextTerms(terms, post.getTopicClusterKey(), 3);
            appendTextTerms(terms, post.getSubtopicClusterKey(), 3);
            appendTextTerms(terms, post.getTitle(), 4);
            if (terms.size() >= 24) {
                break;
            }
        }
        return terms;
    }

    private void appendTextTerms(Set<String> target, String raw, int maxAdds) {
        if (raw == null || raw.isBlank() || maxAdds <= 0) {
            return;
        }
        int adds = 0;
        for (String token : CONTENT_TERM_SPLITTER.split(raw)) {
            String normalized = normalizeTerm(token);
            if (normalized.length() < 2) {
                continue;
            }
            if (target.add(normalized)) {
                adds++;
            }
            if (adds >= maxAdds || target.size() >= 24) {
                break;
            }
        }
    }

    private List<Post> orderedPublicPosts(List<Long> ids, int limit) {
        if (ids == null || ids.isEmpty() || limit <= 0) {
            return List.of();
        }
        List<Post> fetched = postMapper.selectByIds(ids);
        if (fetched.isEmpty()) {
            return List.of();
        }
        Map<Long, Post> byId = new HashMap<>();
        for (Post post : fetched) {
            byId.put(post.getId(), post);
        }

        List<Post> ordered = new ArrayList<>();
        for (Long id : ids) {
            Post post = byId.get(id);
            if (post == null || !isPublicApproved(post)) {
                continue;
            }
            ordered.add(post);
            if (ordered.size() >= limit) {
                break;
            }
        }
        return ordered;
    }

    private boolean isPublicApproved(Post post) {
        return post != null
                && "PUBLIC".equals(post.getVisibility())
                && "APPROVED".equals(post.getAuditStatus());
    }

    private List<Post> sliceStableWindow(List<Post> pool, int limit, String seed, String scene) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        if (pool.size() <= limit) {
            return pool;
        }

        int maxShift = Math.min(Math.max(pool.size() - limit, 0), Math.max(limit, 24));
        int start = maxShift == 0 ? 0 : Math.floorMod(stableHash(seed, scene), maxShift + 1);
        return new ArrayList<>(pool.subList(start, Math.min(pool.size(), start + limit)));
    }

    private int stableHash(String seed, String scene) {
        return Objects.hash(scene, seed == null ? "" : seed.trim());
    }

}
