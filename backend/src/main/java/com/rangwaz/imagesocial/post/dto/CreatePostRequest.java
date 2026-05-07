package com.rangwaz.imagesocial.post.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreatePostRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 1024) String content,
        @Size(max = 64) String channel,
        @Size(max = 64) String channelCode,
        @Size(max = 64) String postType,
        List<String> imageUrls,
        List<String> tags,
        Map<String, Object> extra,
        List<@Valid PostAssetRequest> assets
) {
    public CreatePostRequest(String title,
                             String content,
                             String channel,
                             List<String> tags,
                             List<PostAssetRequest> assets) {
        this(title, content, channel, null, null, null, tags, Map.of(), assets);
    }
}
