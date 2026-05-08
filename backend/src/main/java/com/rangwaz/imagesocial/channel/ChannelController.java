package com.rangwaz.imagesocial.channel;

import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService channelService;

    public ChannelController(ChannelService channelService) {
        this.channelService = channelService;
    }

    @GetMapping
    public ApiResponse<List<ChannelView>> list() {
        List<ChannelView> result = channelService.listActiveChannels().stream()
                .map(channelService::toView)
                .toList();
        return ApiResponse.success(result);
    }
}
