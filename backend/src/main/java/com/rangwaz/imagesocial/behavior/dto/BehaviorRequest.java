package com.rangwaz.imagesocial.behavior.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BehaviorRequest(
        @NotNull Long postId,
        String channelCode,
        @NotBlank String behaviorType,
        Long duration,
        String scene,
        Integer position
) {
}
