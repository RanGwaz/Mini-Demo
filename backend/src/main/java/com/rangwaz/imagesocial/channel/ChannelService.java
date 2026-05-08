package com.rangwaz.imagesocial.channel;

import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.mapper.ChannelMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ChannelService {

    private final ChannelMapper channelMapper;

    public ChannelService(ChannelMapper channelMapper) {
        this.channelMapper = channelMapper;
    }

    public List<Channel> listActiveChannels() {
        return channelMapper.selectActiveChannels();
    }

    public Channel findByCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return channelMapper.selectByCode(code.trim());
    }

    public ChannelView toView(Channel channel) {
        String icon = firstNonBlank(channel.getIconUrl(), channel.getIcon());
        return new ChannelView(
                channel.getCode(),
                channel.getName(),
                Objects.toString(channel.getDescription(), ""),
                icon,
                channel.getSortOrder(),
                channel.getDefaultPostType(),
                Boolean.TRUE.equals(channel.getWaterfallEnabled())
        );
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "" : fallback;
    }
}
