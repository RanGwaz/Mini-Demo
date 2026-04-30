package com.rangwaz.imagesocial.interaction;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.interaction.dto.CommentView;
import com.rangwaz.imagesocial.interaction.dto.CreateCommentRequest;
import com.rangwaz.imagesocial.interaction.dto.NegativeFeedbackRequest;
import com.rangwaz.imagesocial.interaction.dto.PostInteractionStatus;
import com.rangwaz.imagesocial.interaction.dto.ReportRequest;
import com.rangwaz.imagesocial.interaction.dto.ToggleResult;
import com.rangwaz.imagesocial.common.api.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interactions/posts")
public class InteractionController {

    private final InteractionService interactionService;

    public InteractionController(InteractionService interactionService) {
        this.interactionService = interactionService;
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<Void> like(@PathVariable Long postId) {
        interactionService.like(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(null, "点赞成功");
    }

    @DeleteMapping("/{postId}/like")
    public ApiResponse<Void> unlike(@PathVariable Long postId) {
        interactionService.unlike(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(null, "取消点赞成功");
    }

    @PostMapping("/{postId}/like/toggle")
    public ApiResponse<ToggleResult> toggleLike(@PathVariable Long postId) {
        boolean active = interactionService.toggleLike(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(new ToggleResult(active), active ? "点赞成功" : "取消点赞成功");
    }

    @PostMapping("/{postId}/favorite")
    public ApiResponse<Void> favorite(@PathVariable Long postId) {
        interactionService.favorite(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(null, "收藏成功");
    }

    @DeleteMapping("/{postId}/favorite")
    public ApiResponse<Void> unfavorite(@PathVariable Long postId) {
        interactionService.unfavorite(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(null, "取消收藏成功");
    }

    @PostMapping("/{postId}/favorite/toggle")
    public ApiResponse<ToggleResult> toggleFavorite(@PathVariable Long postId) {
        boolean active = interactionService.toggleFavorite(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(new ToggleResult(active), active ? "收藏成功" : "取消收藏成功");
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<CommentView> comment(@PathVariable Long postId,
                                            @Valid @RequestBody CreateCommentRequest request) {
        return ApiResponse.success(interactionService.comment(SecurityUtils.currentUserIdOrThrow(), postId, request), "评论成功");
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<List<CommentView>> comments(@PathVariable Long postId) {
        return ApiResponse.success(interactionService.comments(postId));
    }

    @GetMapping("/{postId}/comments/page")
    public ApiResponse<PageResponse<CommentView>> commentsPaged(@PathVariable Long postId,
                                                                @RequestParam(defaultValue = "1") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(interactionService.commentsPaged(postId, page, size));
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    public ApiResponse<Void> deleteComment(@PathVariable Long postId,
                                           @PathVariable Long commentId) {
        interactionService.deleteComment(SecurityUtils.currentUserIdOrThrow(), postId, commentId);
        return ApiResponse.success(null, "评论已删除");
    }

    @GetMapping("/{postId}/status")
    public ApiResponse<PostInteractionStatus> interactionStatus(@PathVariable Long postId) {
        return ApiResponse.success(interactionService.interactionStatus(SecurityUtils.currentUserIdOrNull(), postId));
    }

    @PostMapping("/{postId}/negative-feedback")
    public ApiResponse<Void> negativeFeedback(@PathVariable Long postId,
                                              @Valid @RequestBody NegativeFeedbackRequest request) {
        interactionService.negativeFeedback(SecurityUtils.currentUserIdOrThrow(), postId, request);
        return ApiResponse.success(null, "已减少类似内容");
    }

    @PostMapping("/{postId}/report")
    public ApiResponse<Void> report(@PathVariable Long postId,
                                    @Valid @RequestBody ReportRequest request) {
        interactionService.report(SecurityUtils.currentUserIdOrThrow(), postId, request);
        return ApiResponse.success(null, "举报已提交");
    }

    @PostMapping("/block-user/{blockedUserId}")
    public ApiResponse<Void> blockUser(@PathVariable Long blockedUserId) {
        interactionService.blockUser(SecurityUtils.currentUserIdOrThrow(), blockedUserId);
        return ApiResponse.success(null, "已屏蔽该用户");
    }

    @DeleteMapping("/block-user/{blockedUserId}")
    public ApiResponse<Void> unblockUser(@PathVariable Long blockedUserId) {
        interactionService.unblockUser(SecurityUtils.currentUserIdOrThrow(), blockedUserId);
        return ApiResponse.success(null, "已取消屏蔽");
    }
}
