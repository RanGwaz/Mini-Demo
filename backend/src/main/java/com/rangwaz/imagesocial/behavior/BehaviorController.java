package com.rangwaz.imagesocial.behavior;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.behavior.dto.BehaviorRequest;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.event.EventService;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/behaviors")
public class BehaviorController {

    private final EventService eventService;

    public BehaviorController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public ApiResponse<Void> record(@Valid @RequestBody BehaviorRequest request) {
        String eventType = toEventType(request.behaviorType());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("channelCode", request.channelCode());
        payload.put("scene", request.scene() == null || request.scene().isBlank() ? "client" : request.scene());
        payload.put("duration", request.duration() == null ? 0L : request.duration());
        payload.put("dwellMs", request.duration() == null ? 0L : request.duration());
        if (request.position() != null) {
            payload.put("position", request.position());
        }
        eventService.publish(eventType, SecurityUtils.currentUserIdOrNull(), "POST", request.postId(), payload);
        return ApiResponse.success(null, "记录成功");
    }

    private String toEventType(String behaviorType) {
        String normalized = behaviorType == null ? "" : behaviorType.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "view" -> "POST_DETAIL_VIEW";
            case "click" -> "POST_CLICK";
            case "like" -> "POST_LIKE";
            case "collect", "favorite" -> "POST_FAVORITE";
            case "comment" -> "POST_COMMENT";
            case "share" -> "POST_SHARE";
            case "dislike", "not_interested" -> "NOT_INTERESTED";
            default -> throw new BusinessException("不支持的行为类型");
        };
    }
}
