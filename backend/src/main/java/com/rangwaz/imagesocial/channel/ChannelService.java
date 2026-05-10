package com.rangwaz.imagesocial.channel;

import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.mapper.ChannelMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ChannelService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;

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

    public Channel findActiveByCode(String code) {
        Channel channel = findByCode(code);
        if (channel == null
                || !"ACTIVE".equalsIgnoreCase(channel.getStatus())
                || !Boolean.TRUE.equals(channel.getEnabled())) {
            return null;
        }
        return channel;
    }

    public List<Channel> searchActiveChannels(String keyword, int limit) {
        return channelMapper.searchActiveChannels(normalizeKeyword(keyword), normalizeLimit(limit));
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

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }
}
