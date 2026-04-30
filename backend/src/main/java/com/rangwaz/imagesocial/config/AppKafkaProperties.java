package com.rangwaz.imagesocial.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record AppKafkaProperties(
        boolean enabled,
        Topics topics
) {
    public record Topics(
            String userEvents,
            String searchSync
    ) {
    }
}
