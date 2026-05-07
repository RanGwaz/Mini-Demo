package com.rangwaz.imagesocial.channel;

import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.taxonomy.ContentChannel;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    @GetMapping
    public ApiResponse<List<ChannelView>> list() {
        List<ContentChannel> channels = ContentChannel.displayChannels();
        List<ChannelView> result = java.util.stream.IntStream.range(0, channels.size())
                .mapToObj(index -> toView(channels.get(index), index + 1))
                .toList();
        return ApiResponse.success(result);
    }

    private ChannelView toView(ContentChannel channel, int sortOrder) {
        return new ChannelView(
                channel.key(),
                channel.label(),
                description(channel),
                "",
                sortOrder,
                channel.postType(),
                !"tech_moment".equals(channel.key())
        );
    }

    private String description(ContentChannel channel) {
        return switch (channel) {
            case CAMPUS -> "大学生校园日常、吐槽、学习、生活分享";
            case ANIME_OUTFIT -> "二次元风格穿搭、角色灵感、日系搭配";
            case PET -> "猫狗萌宠、宠物生活、治愈瞬间";
            case PHOTOGRAPHY -> "摄影作品、拍摄参数、地点分享";
            case TECH_MOMENT -> "程序员日常、AI工具、效率工具、技术趣事";
            default -> "跨频道内容";
        };
    }
}
