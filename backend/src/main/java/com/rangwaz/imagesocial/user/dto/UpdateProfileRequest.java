package com.rangwaz.imagesocial.user.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 64) String nickname,
        @Size(max = 255) String avatarUrl,
        @Size(max = 255) String backgroundUrl,
        @Size(max = 255) String bio
) {
}
