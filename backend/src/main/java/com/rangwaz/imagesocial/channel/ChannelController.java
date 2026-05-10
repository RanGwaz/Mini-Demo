package com.rangwaz.imagesocial.channel;

import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.topic.TopicService;
import com.rangwaz.imagesocial.topic.dto.TopicView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;
    private final TopicService topicService;
    private final PostService postService;

    public ChannelController(ChannelService channelService,
                             TopicService topicService,
                             PostService postService) {
        this.channelService = channelService;
        this.topicService = topicService;
        this.postService = postService;
    }

    @GetMapping
    public ApiResponse<List<ChannelView>> list() {
        List<ChannelView> result = channelService.listActiveChannels().stream()
                .map(channelService::toView)
                .toList();
        return ApiResponse.success(result);
    }

    @GetMapping("/{code}")
    public ApiResponse<ChannelView> detail(@PathVariable String code) {
        Channel channel = requireActiveChannel(code);
        return ApiResponse.success(channelService.toView(channel));
    }

    @GetMapping("/{code}/topics")
    public ApiResponse<List<TopicView>> topics(@PathVariable String code,
                                               @RequestParam(defaultValue = "30") int limit) {
        Channel channel = requireActiveChannel(code);
        return ApiResponse.success(topicService.listChannelTopics(channel.getCode(), limit).stream()
                .map(topicService::toView)
                .toList());
    }

    @GetMapping("/{code}/posts")
    public ApiResponse<PageResponse<PostView>> posts(@PathVariable String code,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "24") int size,
                                                     @RequestParam(defaultValue = "hot") String sort) {
        Channel channel = requireActiveChannel(code);
        return ApiResponse.success(postService.listByChannel(channel.getCode(), sort, page, size));
    }

    private Channel requireActiveChannel(String code) {
        Channel channel = channelService.findActiveByCode(code);
        if (channel == null) {
            throw new BusinessException("Channel does not exist");
        }
        return channel;
    }
}
