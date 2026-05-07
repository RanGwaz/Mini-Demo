package com.rangwaz.imagesocial.channel.dto;

public record ChannelView(
        String code,
        String name,
        String description,
        String icon,
        Integer sortOrder,
        String postType,
        Boolean waterfall
) {
}
