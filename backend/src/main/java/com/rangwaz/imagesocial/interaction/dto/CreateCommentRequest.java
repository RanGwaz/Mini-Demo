package com.rangwaz.imagesocial.interaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        Long parentCommentId,
        @NotBlank @Size(max = 512) String content
) {
}
