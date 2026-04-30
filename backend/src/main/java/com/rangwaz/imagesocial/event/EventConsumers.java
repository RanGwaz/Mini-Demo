package com.rangwaz.imagesocial.event;

import com.rangwaz.imagesocial.feature.FeatureService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.kafka", name = "enabled", havingValue = "true")
public class EventConsumers {

    private static final Logger log = LoggerFactory.getLogger(EventConsumers.class);

    private final FeatureService featureService;

    public EventConsumers(FeatureService featureService) {
        this.featureService = featureService;
    }

    @KafkaListener(topics = "${app.kafka.topics.user-events}", groupId = "image-social-stats")
    public void consumeUserEvent(PlatformEvent event) {
        log.info("stats consumer received event: {}", event);

        if (event.userId() == null) {
            return;
        }
        featureService.ingestRealtimeEvent(
                event.eventType(),
                event.userId(),
                event.targetType(),
                event.targetId(),
                event.payload()
        );
    }

    @KafkaListener(topics = "${app.kafka.topics.search-sync}", groupId = "image-social-search")
    public void consumeSearchSync(PlatformEvent event) {
        log.info("search sync consumer received event: {}", event);
    }
}
