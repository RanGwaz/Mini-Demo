package com.rangwaz.imagesocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenExpireSeconds
) {
}
