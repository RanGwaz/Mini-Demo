package com.rangwaz.imagesocial.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PostAssetRequest(
        @NotBlank String objectKey,
        @NotBlank String fileUrl,
        @NotBlank String fileType,
        String thumbUrl,
        Integer width,
        Integer height,
        @NotNull Integer sortOrder
) {
}
