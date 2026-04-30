package com.rangwaz.imagesocial.interaction;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.domain.entity.ContentReport;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostComment;
import com.rangwaz.imagesocial.domain.entity.PostFavorite;
import com.rangwaz.imagesocial.domain.entity.PostLike;
import com.rangwaz.imagesocial.domain.entity.PostNegativeFeedback;
import com.rangwaz.imagesocial.domain.entity.UserBlock;
import com.rangwaz.imagesocial.domain.mapper.ContentReportMapper;
import com.rangwaz.imagesocial.domain.mapper.PostCommentMapper;
import com.rangwaz.imagesocial.domain.mapper.PostFavoriteMapper;
import com.rangwaz.imagesocial.domain.mapper.PostLikeMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.PostNegativeFeedbackMapper;
import com.rangwaz.imagesocial.domain.mapper.UserBlockMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.interaction.dto.CommentView;
import com.rangwaz.imagesocial.interaction.dto.CreateCommentRequest;
import com.rangwaz.imagesocial.interaction.dto.NegativeFeedbackRequest;
import com.rangwaz.imagesocial.interaction.dto.PostInteractionStatus;
import com.rangwaz.imagesocial.interaction.dto.ReportRequest;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.user.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InteractionService {

    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final PostCommentMapper postCommentMapper;
    private final PostMapper postMapper;
    private final PostNegativeFeedbackMapper postNegativeFeedbackMapper;
    private final ContentReportMapper contentReportMapper;
    private final UserBlockMapper userBlockMapper;
    private final PostService postService;
    private final UserService userService;
    private final EventService eventService;

    public InteractionService(PostLikeMapper postLikeMapper,
                              PostFavoriteMapper postFavoriteMapper,
                              PostCommentMapper postCommentMapper,
                              PostMapper postMapper,
                              PostNegativeFeedbackMapper postNegativeFeedbackMapper,
                              ContentReportMapper contentReportMapper,
                              UserBlockMapper userBlockMapper,
                              PostService postService,
                              UserService userService,
                              EventService eventService) {
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.postCommentMapper = postCommentMapper;
        this.postMapper = postMapper;
        this.postNegativeFeedbackMapper = postNegativeFeedbackMapper;
        this.contentReportMapper = contentReportMapper;
        this.userBlockMapper = userBlockMapper;
        this.postService = postService;
        this.userService = userService;
        this.eventService = eventService;
    }

    @Transactional
    public void like(Long userId, Long postId) {
        postService.requirePost(postId);
        Long count = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId)
                .eq(PostLike::getPostId, postId));
        if (count > 0) {
            return;
        }
        PostLike postLike = new PostLike();
        postLike.setUserId(userId);
        postLike.setPostId(postId);
        postLikeMapper.insert(postLike);
        postMapper.updateCounters(postId, 1, 0, 0, 0, 3.0d);
        eventService.publish("POST_LIKE", userId, "POST", postId, Map.of());
    }

    @Transactional
    public boolean toggleLike(Long userId, Long postId) {
        postService.requirePost(postId);
        int deleted = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId)
                .eq(PostLike::getPostId, postId));
        if (deleted > 0) {
            postMapper.updateCounters(postId, -1, 0, 0, 0, -3.0d);
            eventService.publish("POST_UNLIKE", userId, "POST", postId, Map.of());
            return false;
        }
        PostLike postLike = new PostLike();
        postLike.setUserId(userId);
        postLike.setPostId(postId);
        postLikeMapper.insert(postLike);
        postMapper.updateCounters(postId, 1, 0, 0, 0, 3.0d);
        eventService.publish("POST_LIKE", userId, "POST", postId, Map.of());
        return true;
    }

    @Transactional
    public void unlike(Long userId, Long postId) {
        int deleted = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId)
                .eq(PostLike::getPostId, postId));
        if (deleted > 0) {
            postMapper.updateCounters(postId, -1, 0, 0, 0, -3.0d);
            eventService.publish("POST_UNLIKE", userId, "POST", postId, Map.of());
        }
    }

    @Transactional
    public void favorite(Long userId, Long postId) {
        postService.requirePost(postId);
        Long count = postFavoriteMapper.selectCount(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId));
        if (count > 0) {
            return;
        }
        PostFavorite favorite = new PostFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(postId);
        postFavoriteMapper.insert(favorite);
        postMapper.updateCounters(postId, 0, 1, 0, 0, 5.0d);
        eventService.publish("POST_FAVORITE", userId, "POST", postId, Map.of());
    }

    @Transactional
    public boolean toggleFavorite(Long userId, Long postId) {
        postService.requirePost(postId);
        int deleted = postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId));
        if (deleted > 0) {
            postMapper.updateCounters(postId, 0, -1, 0, 0, -5.0d);
            eventService.publish("POST_UNFAVORITE", userId, "POST", postId, Map.of());
            return false;
        }
        PostFavorite favorite = new PostFavorite();
        favorite.setUserId(userId);
        favorite.setPostId(postId);
        postFavoriteMapper.insert(favorite);
        postMapper.updateCounters(postId, 0, 1, 0, 0, 5.0d);
        eventService.publish("POST_FAVORITE", userId, "POST", postId, Map.of());
        return true;
    }

    @Transactional
    public void unfavorite(Long userId, Long postId) {
        int deleted = postFavoriteMapper.delete(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId));
        if (deleted > 0) {
            postMapper.updateCounters(postId, 0, -1, 0, 0, -5.0d);
            eventService.publish("POST_UNFAVORITE", userId, "POST", postId, Map.of());
        }
    }

    @Transactional
    public CommentView comment(Long userId, Long postId, CreateCommentRequest request) {
        Post post = postService.requirePost(postId);
        PostComment parentComment = null;
        if (request.parentCommentId() != null) {
            parentComment = postCommentMapper.selectById(request.parentCommentId());
            if (parentComment == null || !postId.equals(parentComment.getPostId())) {
                throw new IllegalArgumentException("回复的评论不存在");
            }
        }
        PostComment comment = new PostComment();
        comment.setUserId(userId);
        comment.setPostId(postId);
        comment.setParentCommentId(request.parentCommentId());
        comment.setContent(request.content());
        postCommentMapper.insert(comment);
        postMapper.updateCounters(post.getId(), 0, 0, 1, 0, 2.0d);
        eventService.publish("POST_COMMENT", userId, "POST", postId, Map.of(
                "content", request.content(),
                "parentCommentId", request.parentCommentId() == null ? 0L : request.parentCommentId()
        ));
        UserSummary userSummary = userService.summaryOrPlaceholder(userId);
        UserSummary replyToUser = parentComment == null
                ? null
                : userService.summaryOrPlaceholder(parentComment.getUserId());
        return new CommentView(comment.getId(), userSummary, comment.getParentCommentId(), replyToUser, comment.getContent(), comment.getCreatedAt());
    }

    public List<CommentView> comments(Long postId) {
        Map<Long, UserSummary> userSummaryCache = new java.util.HashMap<>();
        java.util.function.Function<Long, UserSummary> getUserSummary = userId ->
                userSummaryCache.computeIfAbsent(userId, userService::summaryOrPlaceholder);

        Map<Long, PostComment> commentMap = postCommentMapper.selectByPostId(postId).stream()
                .collect(java.util.stream.Collectors.toMap(PostComment::getId, comment -> comment));

        return postCommentMapper.selectByPostId(postId).stream()
                .map(comment -> new CommentView(
                        comment.getId(),
                        getUserSummary.apply(comment.getUserId()),
                        comment.getParentCommentId(),
                        comment.getParentCommentId() == null || !commentMap.containsKey(comment.getParentCommentId())
                                ? null
                                : getUserSummary.apply(commentMap.get(comment.getParentCommentId()).getUserId()),
                        comment.getContent(),
                        comment.getCreatedAt()))
                .toList();
    }

    public PageResponse<CommentView> commentsPaged(Long postId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        List<PostComment> rows = postCommentMapper.selectByPostIdPaged(postId, offset, safeSize);
        Map<Long, PostComment> commentMap = postCommentMapper.selectByPostId(postId).stream()
                .collect(java.util.stream.Collectors.toMap(PostComment::getId, comment -> comment));
        Map<Long, UserSummary> userSummaryCache = new java.util.HashMap<>();
        java.util.function.Function<Long, UserSummary> getUserSummary = userId ->
                userSummaryCache.computeIfAbsent(userId, userService::summaryOrPlaceholder);
        List<CommentView> records = rows.stream()
                .map(comment -> new CommentView(
                        comment.getId(),
                        getUserSummary.apply(comment.getUserId()),
                        comment.getParentCommentId(),
                        comment.getParentCommentId() == null || !commentMap.containsKey(comment.getParentCommentId())
                                ? null
                                : getUserSummary.apply(commentMap.get(comment.getParentCommentId()).getUserId()),
                        comment.getContent(),
                        comment.getCreatedAt()))
                .toList();
        long total = postCommentMapper.countByPostId(postId);
        return new PageResponse<>(records, total, safePage, safeSize);
    }

    public PostInteractionStatus interactionStatus(Long userId, Long postId) {
        if (userId == null) {
            return new PostInteractionStatus(false, false);
        }
        long liked = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getUserId, userId)
                .eq(PostLike::getPostId, postId));
        long favorited = postFavoriteMapper.selectCount(new LambdaQueryWrapper<PostFavorite>()
                .eq(PostFavorite::getUserId, userId)
                .eq(PostFavorite::getPostId, postId));
        return new PostInteractionStatus(liked > 0, favorited > 0);
    }

    @Transactional
    public void deleteComment(Long userId, Long postId, Long commentId) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null || !postId.equals(comment.getPostId())) {
            throw new IllegalArgumentException("评论不存在");
        }
        if (!userId.equals(comment.getUserId())) {
            throw new IllegalArgumentException("只能删除自己的评论");
        }
        int deleted = postCommentMapper.delete(new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getId, commentId)
                .eq(PostComment::getUserId, userId)
                .eq(PostComment::getPostId, postId));
        if (deleted > 0) {
            postMapper.updateCounters(postId, 0, 0, -1, 0, -2.0d);
            eventService.publish("POST_COMMENT_DELETE", userId, "POST", postId, Map.of("commentId", commentId));
        }
    }

    @Transactional
    public void negativeFeedback(Long userId, Long postId, NegativeFeedbackRequest request) {
        postService.requirePost(postId);
        Long count = postNegativeFeedbackMapper.selectCount(new LambdaQueryWrapper<PostNegativeFeedback>()
                .eq(PostNegativeFeedback::getUserId, userId)
                .eq(PostNegativeFeedback::getPostId, postId)
                .eq(PostNegativeFeedback::getFeedbackType, request.feedbackType()));
        if (count > 0) {
            return;
        }
        PostNegativeFeedback feedback = new PostNegativeFeedback();
        feedback.setUserId(userId);
        feedback.setPostId(postId);
        feedback.setFeedbackType(request.feedbackType());
        feedback.setReason(request.reason());
        postNegativeFeedbackMapper.insert(feedback);
        postMapper.updateCounters(postId, 0, 0, 0, 0, -8.0d);
        eventService.publish(resolveNegativeFeedbackEventType(request.feedbackType()), userId, "POST", postId, Map.of(
                "feedbackType", request.feedbackType(),
                "reason", request.reason() == null ? "" : request.reason()
        ));
    }

    @Transactional
    public void report(Long userId, Long postId, ReportRequest request) {
        postService.requirePost(postId);
        ContentReport report = new ContentReport();
        report.setReporterId(userId);
        report.setPostId(postId);
        report.setReason(request.reason());
        contentReportMapper.insert(report);
        eventService.publish("POST_REPORT", userId, "POST", postId, Map.of("reason", request.reason()));
    }

    @Transactional
    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            return;
        }
        userService.requireById(blockedUserId);
        Long count = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId)
                .eq(UserBlock::getBlockedUserId, blockedUserId));
        if (count > 0) {
            return;
        }
        UserBlock block = new UserBlock();
        block.setUserId(userId);
        block.setBlockedUserId(blockedUserId);
        userBlockMapper.insert(block);
        eventService.publish("USER_BLOCK", userId, "USER", blockedUserId, Map.of());

        List<Post> blockedUserPosts = postMapper.selectList(new LambdaQueryWrapper<Post>()
                .eq(Post::getAuthorId, blockedUserId)
                .select(Post::getId));
        for (Post post : blockedUserPosts) {
            eventService.publish("POST_HIDE", userId, "POST", post.getId(), Map.of(
                    "reason", "BLOCK_AUTHOR",
                    "blockedUserId", blockedUserId
            ));
        }
    }

    @Transactional
    public void unblockUser(Long userId, Long blockedUserId) {
        userBlockMapper.delete(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId)
                .eq(UserBlock::getBlockedUserId, blockedUserId));
        eventService.publish("USER_UNBLOCK", userId, "USER", blockedUserId, Map.of());
    }

    private String resolveNegativeFeedbackEventType(String feedbackType) {
        if (feedbackType == null || feedbackType.isBlank()) {
            return "POST_NEGATIVE_FEEDBACK";
        }
        return switch (feedbackType.trim().toUpperCase()) {
            case "NOT_INTERESTED" -> "NOT_INTERESTED";
            case "POST_HIDE", "HIDE" -> "POST_HIDE";
            default -> "POST_NEGATIVE_FEEDBACK";
        };
    }
}
