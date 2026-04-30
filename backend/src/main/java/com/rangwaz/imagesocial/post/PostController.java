package com.rangwaz.imagesocial.post;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.post.dto.CreatePostRequest;
import com.rangwaz.imagesocial.post.dto.PostView;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;
    private final PostEventService postEventService;

    public PostController(PostService postService, PostEventService postEventService) {
        this.postService = postService;
        this.postEventService = postEventService;
    }

    @PostMapping
    public ApiResponse<PostView> create(@Valid @RequestBody CreatePostRequest request) {
        return ApiResponse.success(postService.createPost(SecurityUtils.currentUserIdOrThrow(), request), "发布成功");
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostView> detail(@PathVariable Long postId,
                                        @RequestParam(defaultValue = "detail") String scene,
                                        HttpServletRequest request) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        postService.increaseView(postId, currentUserId, request.getRemoteAddr());
        postEventService.trackDetailView(currentUserId, postId, scene);
        return ApiResponse.success(postService.getPostView(postId));
    }

    @PostMapping("/{postId}/click")
    public ApiResponse<Void> trackClick(@PathVariable Long postId,
                                        @RequestParam(defaultValue = "feed") String scene,
                                        @RequestParam(required = false) Integer position) {
        postEventService.trackClick(SecurityUtils.currentUserIdOrNull(), postId, scene, position);
        return ApiResponse.success(null, "记录成功");
    }

    @PostMapping("/{postId}/share")
    public ApiResponse<Void> trackShare(@PathVariable Long postId,
                                        @RequestParam(defaultValue = "detail") String scene) {
        postEventService.trackShare(SecurityUtils.currentUserIdOrNull(), postId, scene);
        return ApiResponse.success(null, "分享记录成功");
    }

    @GetMapping("/author/{authorId}")
    public ApiResponse<List<PostView>> authorPosts(@PathVariable Long authorId,
                                                   @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(postService.listByAuthor(authorId, limit));
    }

    @DeleteMapping("/{postId}")
    public ApiResponse<Void> delete(@PathVariable Long postId) {
        postService.deletePost(SecurityUtils.currentUserIdOrThrow(), postId);
        return ApiResponse.success(null, "删除成功");
    }
}
