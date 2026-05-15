package com.rangwaz.imagesocial.feed.controller;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.channel.ChannelService;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.feed.FeedFacetService;
import com.rangwaz.imagesocial.feed.FeedOnlineMetricsService;
import com.rangwaz.imagesocial.feed.FeedQuotaGuardService;
import com.rangwaz.imagesocial.feed.FeedSourceHealthTrackerService;
import com.rangwaz.imagesocial.feed.dto.FeedFacetsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedHomeDiagnosticsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedOnlineMetricsResponse;
import com.rangwaz.imagesocial.feed.dto.FeedQuotaExperimentSnapshot;
import com.rangwaz.imagesocial.feed.dto.FeedSourceHealthResponse;
import com.rangwaz.imagesocial.feed.service.FeedService;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.topic.TopicService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final FeedService feedService;
    private final FeedFacetService feedFacetService;
    private final FeedOnlineMetricsService feedOnlineMetricsService;
    private final FeedQuotaGuardService feedQuotaGuardService;
    private final FeedSourceHealthTrackerService feedSourceHealthTrackerService;
    private final PostService postService;
    private final ChannelService channelService;
    private final TopicService topicService;

    @Autowired
    public FeedController(FeedService feedService,
                          FeedFacetService feedFacetService,
                          FeedOnlineMetricsService feedOnlineMetricsService,
                          FeedQuotaGuardService feedQuotaGuardService,
                          FeedSourceHealthTrackerService feedSourceHealthTrackerService,
                          PostService postService,
                          ChannelService channelService,
                          TopicService topicService) {
        this.feedService = feedService;
        this.feedFacetService = feedFacetService;
        this.feedOnlineMetricsService = feedOnlineMetricsService;
        this.feedQuotaGuardService = feedQuotaGuardService;
        this.feedSourceHealthTrackerService = feedSourceHealthTrackerService;
        this.postService = postService;
        this.channelService = channelService;
        this.topicService = topicService;
    }

    @GetMapping
    public ApiResponse<PageResponse<PostView>> feed(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(required = false) Integer pageSize,
                                                    @RequestParam(required = false) Integer size,
                                                    @RequestParam(required = false) String seed,
                                                    @RequestParam(required = false) String channelCode,
                                                    @RequestParam(required = false) Long topicId,
                                                    @RequestParam(required = false) String topicSlug,
                                                    @RequestParam(required = false) String topic,
                                                    @RequestParam(required = false) String style,
                                                    @RequestParam(required = false) String tag) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        int resolvedSize = pageSize == null ? (size == null ? 24 : size) : pageSize;
        if (hasScopeFilter(channelCode, topicId, topicSlug)) {
            return ApiResponse.success(postService.listPublicPostsByScope(
                    resolveChannelCode(channelCode),
                    resolveTopicId(topicId),
                    resolveTopicSlug(topicId, topicSlug),
                    page,
                    resolvedSize
            ));
        }
        return ApiResponse.success(feedService.homeFeed(currentUserId, page, resolvedSize, seed, topic, style, tag));
    }

    @GetMapping("/home")
    public ApiResponse<PageResponse<PostView>> home(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "24") int size,
                                                    @RequestParam(required = false) String seed,
                                                    @RequestParam(required = false) String channelCode,
                                                    @RequestParam(required = false) Long topicId,
                                                    @RequestParam(required = false) String topicSlug,
                                                    @RequestParam(required = false) String topic,
                                                    @RequestParam(required = false) String style,
                                                    @RequestParam(required = false) String tag) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        if (hasScopeFilter(channelCode, topicId, topicSlug)) {
            return ApiResponse.success(postService.listPublicPostsByScope(
                    resolveChannelCode(channelCode),
                    resolveTopicId(topicId),
                    resolveTopicSlug(topicId, topicSlug),
                    page,
                    size
            ));
        }
        return ApiResponse.success(feedService.homeFeed(currentUserId, page, size, seed, topic, style, tag));
    }

    @GetMapping("/social")
    public ApiResponse<PageResponse<PostView>> social(@RequestParam(defaultValue = "following") String mode,
                                                      @RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "24") int size) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        return ApiResponse.success(feedService.socialFeed(currentUserId, mode, page, size));
    }

    @GetMapping("/home/diagnostics")
    public ApiResponse<FeedHomeDiagnosticsResponse> homeDiagnostics(@RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "24") int size,
                                                                    @RequestParam(required = false) String seed,
                                                                    @RequestParam(required = false) String topic,
                                                                    @RequestParam(required = false) String style,
                                                                    @RequestParam(required = false) String tag) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        return ApiResponse.success(feedService.homeFeedDiagnostics(currentUserId, page, size, seed, topic, style, tag));
    }

    @GetMapping("/posts/{postId}/similar")
    public ApiResponse<PageResponse<PostView>> similar(@PathVariable Long postId,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "24") int size,
                                                       @RequestParam(required = false) String topic,
                                                       @RequestParam(required = false) String style,
                                                       @RequestParam(required = false) String tag) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        return ApiResponse.success(feedService.similarPosts(currentUserId, postId, page, size, topic, style, tag));
    }

    @GetMapping("/facets")
    public ApiResponse<FeedFacetsResponse> facets(@RequestParam(defaultValue = "24") int limit,
                                                  @RequestParam(defaultValue = "14") int recentDays) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        return ApiResponse.success(feedFacetService.listFeedFacets(currentUserId, limit, recentDays));
    }

    @GetMapping("/metrics/online")
    public ApiResponse<FeedOnlineMetricsResponse> onlineMetrics(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) String surface,
            @RequestParam(required = false) Integer sourceLimit,
            @RequestParam(required = false) Integer experimentLimit,
            @RequestParam(defaultValue = "mine") String scope) {
        Long currentUserId = SecurityUtils.currentUserIdOrNull();
        Long metricUserId = "global".equalsIgnoreCase(scope) ? null : currentUserId;
        return ApiResponse.success(feedOnlineMetricsService.fetchOnlineMetrics(
                metricUserId,
                days,
                surface,
                sourceLimit,
                experimentLimit
        ));
    }

    @GetMapping("/health/sources")
    public ApiResponse<FeedSourceHealthResponse> sourceHealth(
            @RequestParam(required = false) String surface,
            @RequestParam(required = false) Integer sourceLimit) {
        return ApiResponse.success(feedSourceHealthTrackerService.snapshot(surface, sourceLimit));
    }

    @GetMapping("/experiments/home-quota")
    public ApiResponse<FeedQuotaExperimentSnapshot> homeQuotaExperiment(
            @RequestParam(required = false) String experimentName) {
        return ApiResponse.success(feedQuotaGuardService.snapshot(experimentName));
    }

    private boolean hasScopeFilter(String channelCode, Long topicId, String topicSlug) {
        return (channelCode != null && !channelCode.isBlank() && !"all".equalsIgnoreCase(channelCode.trim()))
                || (topicId != null && topicId > 0L)
                || (topicSlug != null && !topicSlug.isBlank());
    }

    private String resolveChannelCode(String channelCode) {
        if (channelCode == null || channelCode.isBlank() || "all".equalsIgnoreCase(channelCode.trim())) {
            return null;
        }
        Channel channel = channelService.findByCode(channelCode.trim());
        if (channel == null || !"ACTIVE".equalsIgnoreCase(channel.getStatus()) || !Boolean.TRUE.equals(channel.getEnabled())) {
            throw new BusinessException("频道不存在");
        }
        return channel.getCode();
    }

    private Long resolveTopicId(Long topicId) {
        if (topicId == null || topicId <= 0L) {
            return null;
        }
        if (topicService.requireActiveById(topicId) == null) {
            throw new BusinessException("Topic does not exist");
        }
        return topicId;
    }

    private String resolveTopicSlug(Long topicId, String topicSlug) {
        if (topicId != null && topicId > 0L) {
            return null;
        }
        if (topicSlug == null || topicSlug.isBlank()) {
            return null;
        }
        Topic topic = topicService.findBySlug(topicSlug.trim());
        if (topic == null || !"ACTIVE".equalsIgnoreCase(topic.getStatus())) {
            throw new BusinessException("Topic does not exist");
        }
        return topic.getSlug();
    }
}
