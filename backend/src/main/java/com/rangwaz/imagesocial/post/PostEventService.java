package com.rangwaz.imagesocial.post;

import com.rangwaz.imagesocial.event.EventService;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PostEventService {

    private final EventService eventService;

    public PostEventService(EventService eventService) {
        this.eventService = eventService;
    }

    public void trackClick(Long userId, Long postId, String scene, Integer position) {
        eventService.publish("POST_CLICK", userId, "POST", postId, Map.of(
                "scene", safeScene(scene),
                "position", position == null ? -1 : position
        ));
    }

    public void trackDetailView(Long userId, Long postId, String scene) {
        eventService.publish("POST_DETAIL_VIEW", userId, "POST", postId, Map.of(
                "scene", safeScene(scene)
        ));
    }

    public void trackShare(Long userId, Long postId, String scene) {
        eventService.publish("POST_SHARE", userId, "POST", postId, Map.of(
                "scene", safeScene(scene)
        ));
    }

    private String safeScene(String scene) {
        return scene == null || scene.isBlank() ? "unknown" : scene;
    }
}
