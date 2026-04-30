package com.rangwaz.imagesocial.event;

import com.rangwaz.imagesocial.config.AppKafkaProperties;
import com.rangwaz.imagesocial.domain.entity.UserEvent;
import com.rangwaz.imagesocial.domain.mapper.UserEventMapper;
import com.rangwaz.imagesocial.feature.FeatureService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventService {
    private static final Logger log = LoggerFactory.getLogger(EventService.class);

    private final UserEventMapper userEventMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AppKafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;
    private final FeatureService featureService;

    public EventService(UserEventMapper userEventMapper,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        AppKafkaProperties kafkaProperties,
                        ObjectMapper objectMapper,
                        FeatureService featureService) {
        this.userEventMapper = userEventMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaProperties = kafkaProperties;
        this.objectMapper = objectMapper;
        this.featureService = featureService;
    }

    public void publish(String eventType, Long userId, String targetType, Long targetId, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        try {
            UserEvent event = new UserEvent();
            event.setUserId(userId);
            event.setEventType(eventType);
            event.setTargetType(targetType);
            event.setTargetId(targetId);
            event.setRequestId(extractString(safePayload, "requestId", "request_id"));
            event.setSessionId(extractString(safePayload, "sessionId", "session_id"));
            event.setSurface(extractString(safePayload, "surface", "scene"));
            event.setPageNo(extractInteger(safePayload, "pageNo", "page_no"));
            event.setRankPosition(extractInteger(safePayload, "rankPosition", "rank_position", "position"));
            event.setRecallSource(extractString(safePayload, "recallSource", "recall_source", "reason"));
            event.setDwellMs(extractLong(safePayload, "dwellMs", "dwell_ms", "stayMs", "stay_ms"));
            event.setDeviceType(extractString(safePayload, "deviceType", "device_type"));
            event.setExperimentId(extractString(safePayload, "experimentId", "experiment_id"));
            event.setPayloadJson(toJson(safePayload));
            userEventMapper.insert(event);

            if (kafkaProperties.enabled()) {
                try {
                    PlatformEvent platformEvent = new PlatformEvent(
                            eventType,
                            userId,
                            targetType,
                            targetId,
                            safePayload,
                            Instant.now());
                    kafkaTemplate.send(kafkaProperties.topics().userEvents(), String.valueOf(targetId), platformEvent);
                    kafkaTemplate.send(kafkaProperties.topics().searchSync(), String.valueOf(targetId), platformEvent);
                    return;
                } catch (Exception exception) {
                    log.warn("Kafka publish failed, fallback to local realtime ingestion: {}", exception.getMessage());
                }
            }

            if (!isTruthy(safePayload.get("realtimeAlreadyRecorded"))) {
                featureService.ingestRealtimeEvent(eventType, userId, targetType, targetId, safePayload);
            }
        } catch (Exception exception) {
            log.warn(
                    "Event publish skipped: type={}, userId={}, targetType={}, targetId={}, reason={}",
                    eventType,
                    userId,
                    targetType,
                    targetId,
                    exception.getMessage()
            );
        }
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && "true".equalsIgnoreCase(String.valueOf(value));
    }

    private String extractString(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private Integer extractInteger(Map<String, Object> payload, String... keys) {
        Long value = extractLong(payload, keys);
        if (value == null) {
            return null;
        }
        return value.intValue();
    }

    private Long extractLong(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value == null) {
                continue;
            }
            if (value instanceof Number number) {
                return number.longValue();
            }
            try {
                return Long.parseLong(String.valueOf(value).trim());
            } catch (Exception ignore) {
                // Ignore malformed payload values and keep trying alternative keys.
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }
}
