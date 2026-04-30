package com.rangwaz.imagesocial.post.dto;

public record PostAssetView(
        Long id,
        String objectKey,
        String fileUrl,
        String fileType,
        String thumbUrl,
        Integer width,
        Integer height,
        Integer sortOrder
) {
}
