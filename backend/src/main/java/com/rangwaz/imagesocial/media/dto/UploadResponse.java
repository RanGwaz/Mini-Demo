package com.rangwaz.imagesocial.media.dto;

public record UploadResponse(
        String objectKey,
        String fileUrl,
        String fileType,
        String thumbUrl,
        Integer width,
        Integer height
) {
}
