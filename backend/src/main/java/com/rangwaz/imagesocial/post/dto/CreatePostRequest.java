package com.rangwaz.imagesocial.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreatePostRequest(
        @NotBlank @Size(max = 128) String title,
        @Size(max = 1024) String content,
        List<String> tags,
        List<PostAssetRequest> assets
) {
}
