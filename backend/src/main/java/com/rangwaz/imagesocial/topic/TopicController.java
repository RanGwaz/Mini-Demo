package com.rangwaz.imagesocial.topic;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.topic.dto.TopicView;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/topics")
public class TopicController {

    private final TopicService topicService;
    private final UserTopicFollowService userTopicFollowService;
    private final PostService postService;

    public TopicController(TopicService topicService,
                           UserTopicFollowService userTopicFollowService,
                           PostService postService) {
        this.topicService = topicService;
        this.userTopicFollowService = userTopicFollowService;
        this.postService = postService;
    }

    @GetMapping("/search")
    public ApiResponse<List<TopicView>> search(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(topicService.searchActiveTopics(keyword, limit).stream()
                .map(topicService::toView)
                .toList());
    }

    @GetMapping("/trending")
    public ApiResponse<List<TopicView>> trending(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(topicService.listTrendingTopics(limit).stream()
                .map(topicService::toView)
                .toList());
    }

    @GetMapping("/{slug}")
    public ApiResponse<TopicView> detail(@PathVariable String slug) {
        Topic topic = topicService.findBySlug(slug);
        if (topic == null || !"ACTIVE".equalsIgnoreCase(topic.getStatus())) {
            throw new BusinessException("Topic does not exist");
        }
        return ApiResponse.success(topicService.toView(topic));
    }

    @GetMapping("/{slug}/posts")
    public ApiResponse<PageResponse<PostView>> posts(@PathVariable String slug,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "24") int size) {
        Topic topic = topicService.findBySlug(slug);
        if (topic == null || !"ACTIVE".equalsIgnoreCase(topic.getStatus())) {
            throw new BusinessException("Topic does not exist");
        }
        return ApiResponse.success(postService.listByTopic(topic.getId(), page, size));
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Void> follow(@PathVariable Long id) {
        userTopicFollowService.follow(SecurityUtils.currentUserIdOrThrow(), id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/follow")
    public ApiResponse<Void> unfollow(@PathVariable Long id) {
        userTopicFollowService.unfollow(SecurityUtils.currentUserIdOrThrow(), id);
        return ApiResponse.success(null);
    }
}
