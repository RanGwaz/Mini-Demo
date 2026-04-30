package com.rangwaz.imagesocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.elasticsearch")
public record ElasticsearchProperties(
        boolean enabled,
        String endpoint
) {
}
