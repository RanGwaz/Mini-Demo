package com.rangwaz.imagesocial.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.ContentReport;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostAsset;
import com.rangwaz.imagesocial.domain.entity.PostComment;
import com.rangwaz.imagesocial.domain.entity.PostFavorite;
import com.rangwaz.imagesocial.domain.entity.PostLike;
import com.rangwaz.imagesocial.domain.entity.PostNegativeFeedback;
import com.rangwaz.imagesocial.domain.entity.User;
import com.rangwaz.imagesocial.domain.mapper.ContentReportMapper;
import com.rangwaz.imagesocial.domain.mapper.PostAssetMapper;
import com.rangwaz.imagesocial.domain.mapper.PostCommentMapper;
import com.rangwaz.imagesocial.domain.mapper.PostFavoriteMapper;
import com.rangwaz.imagesocial.domain.mapper.PostLikeMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.PostNegativeFeedbackMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.post.dto.CreatePostRequest;
import com.rangwaz.imagesocial.post.dto.PostAssetRequest;
import com.rangwaz.imagesocial.post.dto.PostAssetView;
import com.rangwaz.imagesocial.post.dto.PostImageView;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.search.SearchIndexGateway;
import com.rangwaz.imagesocial.taxonomy.ContentChannel;
import com.rangwaz.imagesocial.user.UserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostService {
    private static final TypeReference<Map<String, Object>> EXTRA_TYPE = new TypeReference<>() {};
    private static final long VIEW_DEDUP_WINDOW_MILLIS = 30_000L;
    private static final ConcurrentHashMap<String, Long> VIEW_TRACKER = new ConcurrentHashMap<>();

    private final PostMapper postMapper;
    private final PostAssetMapper postAssetMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final PostCommentMapper postCommentMapper;
    private final PostNegativeFeedbackMapper postNegativeFeedbackMapper;
    private final ContentReportMapper contentReportMapper;
    private final UserService userService;
    private final EventService eventService;
    private final SearchIndexGateway searchIndexGateway;
    private final ObjectMapper objectMapper;

    public PostService(PostMapper postMapper,
                       PostAssetMapper postAssetMapper,
                       PostLikeMapper postLikeMapper,
                       PostFavoriteMapper postFavoriteMapper,
                       PostCommentMapper postCommentMapper,
                       PostNegativeFeedbackMapper postNegativeFeedbackMapper,
                       ContentReportMapper contentReportMapper,
                       UserService userService,
                       EventService eventService,
                       SearchIndexGateway searchIndexGateway,
                       ObjectMapper objectMapper) {
        this.postMapper = postMapper;
        this.postAssetMapper = postAssetMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.postCommentMapper = postCommentMapper;
        this.postNegativeFeedbackMapper = postNegativeFeedbackMapper;
        this.contentReportMapper = contentReportMapper;
        this.userService = userService;
        this.eventService = eventService;
        this.searchIndexGateway = searchIndexGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PostView createPost(Long authorId, CreatePostRequest request) {
        User author = userService.requireById(authorId);
        ContentChannel channel = resolveChannel(request);
        String postType = resolvePostType(channel, request.postType());
        String title = normalizeTitle(request.title());
        String content = request.content() == null ? "" : request.content().trim();
        List<String> normalizedTags = normalizeTags(request.tags());
        List<PostAssetRequest> assets = normalizeAssets(request);
        PostAssetRequest firstAsset = assets.isEmpty() ? null : assets.get(0);

        Post post = new Post();
        post.setAuthorId(authorId);
        post.setChannelCode(channel.key());
        post.setPostType(postType);
        post.setTitle(title);
        post.setContent(content);
        post.setExtra(toExtraJson(request.extra()));
        post.setTags(joinTags(normalizedTags));
        post.setTopicPath(channel.topicPath());
        post.setSemanticTags(joinRawTerms(buildSemanticTerms(channel, normalizedTags)));
        post.setStyleTags(assets.isEmpty() ? "text" : "image");
        post.setTopicClusterKey(channel.key());
        post.setSubtopicClusterKey(normalizedTags.isEmpty() ? channel.key() : normalizedTags.get(0));
        post.setTaxonomyVersion(ContentChannel.TAXONOMY_VERSION);
        post.setCoverUrl(firstAsset == null ? "" : firstAsset.fileUrl());
        post.setThumbUrl(firstAsset == null ? null : firstAsset.thumbUrl());
        post.setVisibility("PUBLIC");
        post.setAuditStatus("APPROVED");
        post.setLikeCount(0);
        post.setFavoriteCount(0);
        post.setCommentCount(0);
        post.setShareCount(0);
        post.setViewCount(0L);
        post.setHotScore(BigDecimal.ZERO);
        post.setQualityScore(initialQualityScore(request, normalizedTags, assets));
        post.setAestheticScore(assets.isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(0.6800d).setScale(4, RoundingMode.HALF_UP));
        post.setSafetyScore(BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP));
        post.setEmbeddingVersion(assets.isEmpty() ? "runtime-text-v1" : "runtime-media-pending-v1");
        postMapper.insert(post);

        for (PostAssetRequest assetRequest : assets) {
            PostAsset asset = new PostAsset();
            asset.setPostId(post.getId());
            asset.setObjectKey(assetRequest.objectKey());
            asset.setFileUrl(assetRequest.fileUrl());
            asset.setFileType(assetRequest.fileType());
            asset.setThumbUrl(assetRequest.thumbUrl());
            asset.setWidth(assetRequest.width());
            asset.setHeight(assetRequest.height());
            asset.setSortOrder(assetRequest.sortOrder());
            postAssetMapper.insert(asset);
        }

        PostView view = toView(post, author, "你的新内容");
        eventService.publish(
                "POST_CREATE",
                authorId,
                "POST",
                post.getId(),
                Map.of("title", post.getTitle(), "channelCode", channel.key(), "postType", postType, "tags", normalizedTags)
        );
        searchIndexGateway.syncPost(view);
        return view;
    }

    public Post requirePost(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        return post;
    }

    public PostView getPostView(Long postId) {
        Post post = requirePost(postId);
        return toView(
                post,
                userService.summaryOrPlaceholder(post.getAuthorId()),
                getAssetViews(post.getId()),
                "帖子详情"
        );
    }

    public List<PostView> listByAuthor(Long authorId, int limit) {
        User author = userService.requireById(authorId);
        return toViews(
                postMapper.selectByAuthorId(authorId, limit),
                Map.of(authorId, userService.toSummary(author)),
                post -> "作者发布"
        );
    }

    public List<PostView> search(String keyword, int limit) {
        return toViews(postMapper.searchPosts("%" + keyword + "%", limit), post -> "搜索结果");
    }

    public PageResponse<PostView> searchPostsPage(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        String pattern = "%" + keyword + "%";
        long total = postMapper.countSearchPosts(pattern);
        int offset = (safePage - 1) * safeSize;
        return new PageResponse<>(
                toViews(postMapper.searchPostsPage(pattern, offset, safeSize), post -> "搜索结果"),
                total,
                safePage,
                safeSize
        );
    }

    public void increaseView(Long postId, Long currentUserId, String requestIp) {
        String viewerKey = currentUserId != null ? "u:" + currentUserId : "ip:" + (requestIp == null ? "unknown" : requestIp);
        String key = postId + ":" + viewerKey;
        long now = System.currentTimeMillis();
        Long last = VIEW_TRACKER.get(key);
        if (last != null && now - last < VIEW_DEDUP_WINDOW_MILLIS) {
            return;
        }
        VIEW_TRACKER.put(key, now);
        postMapper.updateCounters(postId, 0, 0, 0, 1, 0.2d);
    }

    @Transactional
    public void deletePost(Long currentUserId, Long postId) {
        Post post = requirePost(postId);
        if (!post.getAuthorId().equals(currentUserId)) {
            throw new BusinessException("只能删除自己发布的帖子");
        }
        postAssetMapper.delete(new LambdaQueryWrapper<PostAsset>().eq(PostAsset::getPostId, postId));
        postLikeMapper.delete(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
        postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getPostId, postId));
        postCommentMapper.delete(new LambdaQueryWrapper<PostComment>().eq(PostComment::getPostId, postId));
        postNegativeFeedbackMapper.delete(new LambdaQueryWrapper<PostNegativeFeedback>().eq(PostNegativeFeedback::getPostId, postId));
        contentReportMapper.delete(new LambdaQueryWrapper<ContentReport>().eq(ContentReport::getPostId, postId));
        postMapper.deleteById(postId);
        eventService.publish("POST_DELETE", currentUserId, "POST", postId, Map.of("authorId", post.getAuthorId()));
    }

    public List<String> parseTags(Post post) {
        return parseCsv(post == null ? null : post.getTags());
    }

    public List<PostAssetView> getAssetViews(Long postId) {
        return mapAssets(postAssetMapper.selectByPostId(postId));
    }

    public PostView toView(Post post, User author, String reason) {
        return toView(post, userService.toSummary(author), getAssetViews(post.getId()), reason);
    }

    public List<PostView> toViews(List<Post> posts, Function<Post, String> reasonResolver) {
        Map<Long, UserSummary> authorMap = userService.summaryMapByIds(
                posts.stream().map(Post::getAuthorId).distinct().toList()
        );
        return toViews(posts, authorMap, reasonResolver);
    }

    public List<PostView> toViews(List<Post> posts,
                                  Map<Long, UserSummary> authorMap,
                                  Function<Post, String> reasonResolver) {
        Map<Long, UserSummary> safeAuthorMap = new LinkedHashMap<>(authorMap);
        for (Post post : posts) {
            safeAuthorMap.computeIfAbsent(post.getAuthorId(), userService::summaryOrPlaceholder);
        }

        Map<Long, List<PostAssetView>> assetMap = assetViewsByPostId(posts.stream().map(Post::getId).toList());
        return posts.stream()
                .map(post -> toView(
                        post,
                        safeAuthorMap.get(post.getAuthorId()),
                        assetMap.getOrDefault(post.getId(), List.of()),
                        reasonResolver.apply(post)
                ))
                .toList();
    }

    public List<Post> listRecentPublicPosts(int limit) {
        return postMapper.selectList(new LambdaQueryWrapper<Post>()
                        .eq(Post::getVisibility, "PUBLIC")
                        .eq(Post::getAuditStatus, "APPROVED")
                        .orderByDesc(Post::getCreatedAt)
                        .last("limit " + limit))
                .stream()
                .toList();
    }

    public long countByAuthorId(Long authorId) {
        return postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuthorId, authorId));
    }

    private Map<Long, List<PostAssetView>> assetViewsByPostId(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return postAssetMapper.selectByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(
                        PostAsset::getPostId,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::mapAssets)
                ));
    }

    private List<PostAssetView> mapAssets(List<PostAsset> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        return assets.stream()
                .map(asset -> new PostAssetView(
                        asset.getId(),
                        asset.getObjectKey(),
                        asset.getFileUrl(),
                        asset.getFileType(),
                        asset.getThumbUrl(),
                        asset.getWidth(),
                        asset.getHeight(),
                        asset.getSortOrder()
                ))
                .toList();
    }

    private PostView toView(Post post, UserSummary author, List<PostAssetView> assets, String reason) {
        if (author == null) {
            throw new BusinessException("帖子作者不存在");
        }
        ContentChannel channel = ContentChannel.fromPostTaxonomy(firstNonBlank(post.getChannelCode(), post.getTopicClusterKey()), post.getTopicPath());
        String postType = firstNonBlank(post.getPostType(), channel.postType());
        return new PostView(
                post.getId(),
                author,
                post.getTitle(),
                post.getContent(),
                parseTags(post),
                channel.key(),
                channel.key(),
                postType,
                post.getTopicPath(),
                parseCsv(post.getSemanticTags()),
                parseCsv(post.getStyleTags()),
                assets,
                toImageViews(assets),
                post.getCoverUrl(),
                post.getThumbUrl(),
                parseExtra(post.getExtra()),
                post.getLikeCount(),
                post.getFavoriteCount(),
                post.getFavoriteCount(),
                post.getCommentCount(),
                post.getShareCount() == null ? 0 : post.getShareCount(),
                post.getViewCount(),
                reason,
                post.getCreatedAt()
        );
    }

    private ContentChannel resolveChannel(CreatePostRequest request) {
        String channelKey = firstNonBlank(request.channelCode(), request.channel());
        if (channelKey == null || channelKey.isBlank()) {
            return ContentChannel.defaultChannel();
        }
        return ContentChannel.fromKey(channelKey)
                .orElseThrow(() -> new BusinessException("频道不存在"));
    }

    private String resolvePostType(ContentChannel channel, String rawPostType) {
        String value = firstNonBlank(rawPostType, channel.postType());
        return value == null || value.isBlank() ? channel.postType() : value;
    }

    private List<PostAssetRequest> normalizeAssets(CreatePostRequest request) {
        if (request.assets() != null && !request.assets().isEmpty()) {
            return request.assets().stream().filter(Objects::nonNull).toList();
        }
        if (request.imageUrls() == null || request.imageUrls().isEmpty()) {
            return List.of();
        }
        List<String> imageUrls = request.imageUrls().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(12)
                .toList();
        return java.util.stream.IntStream.range(0, imageUrls.size())
                .mapToObj(index -> new PostAssetRequest(imageUrls.get(index), imageUrls.get(index), "image", null, null, null, index))
                .toList();
    }

    private List<PostImageView> toImageViews(List<PostAssetView> assets) {
        if (assets == null || assets.isEmpty()) {
            return List.of();
        }
        return assets.stream()
                .map(asset -> new PostImageView(
                        firstNonBlank(asset.fileUrl(), asset.thumbUrl()),
                        asset.width(),
                        asset.height()
                ))
                .toList();
    }

    private String toExtraJson(Map<String, Object> extra) {
        if (extra == null || extra.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(extra);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("扩展字段格式错误");
        }
    }

    private Map<String, Object> parseExtra(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> value = objectMapper.readValue(raw, EXTRA_TYPE);
            return value == null ? Map.of() : value;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeTitle(String rawTitle) {
        if (rawTitle == null) {
            return "无标题分享";
        }
        String title = rawTitle.trim();
        return title.isBlank() ? "无标题分享" : title;
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .filter(value -> !ContentChannel.isChannelLabel(value))
                .distinct()
                .limit(10)
                .toList();
    }

    private String joinTags(List<String> tags) {
        List<String> normalizedTags = normalizeTags(tags);
        if (normalizedTags.isEmpty()) {
            return "";
        }
        return String.join(",", normalizedTags);
    }

    private List<String> buildSemanticTerms(ContentChannel channel, List<String> tags) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(channel.key(), channel.topicPath()), tags.stream())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(12)
                .toList();
    }

    private String joinRawTerms(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            return "";
        }
        return terms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(12)
                .collect(Collectors.joining(","));
    }

    private BigDecimal initialQualityScore(CreatePostRequest request,
                                           List<String> normalizedTags,
                                           List<PostAssetRequest> assets) {
        int titleLength = request.title() == null ? 0 : request.title().trim().length();
        int contentLength = request.content() == null ? 0 : request.content().trim().length();
        double contentScore = Math.min(1.0d, (titleLength / 64.0d) * 0.30d + (contentLength / 420.0d) * 0.45d);
        double tagScore = Math.min(0.16d, normalizedTags.size() * 0.025d);
        double mediaScore = assets.isEmpty() ? 0.0d : Math.min(0.18d, assets.size() * 0.06d);
        double baseline = assets.isEmpty() ? 0.46d : 0.52d;
        double score = Math.min(1.0d, baseline + contentScore + tagScore + mediaScore);
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }
}
