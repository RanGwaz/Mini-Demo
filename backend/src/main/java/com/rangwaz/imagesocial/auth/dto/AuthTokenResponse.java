package com.rangwaz.imagesocial.auth.dto;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserSummary me
) {
}
