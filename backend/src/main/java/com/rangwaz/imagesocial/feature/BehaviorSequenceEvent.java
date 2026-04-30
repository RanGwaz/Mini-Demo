package com.rangwaz.imagesocial.feature;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 用户行为序列事件：用于深度排序的时序输入。
 */
public record BehaviorSequenceEvent(
        Long targetId,
        String eventType,
        Long eventTs,
        Long dwellMs,
        Integer rankPosition,
        String surface,
        Integer pageNo,
        String deviceType,
        String recallSource
) {
    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("target_id", targetId == null ? 0L : targetId);
        payload.put("event_type", eventType == null ? "UNKNOWN" : eventType);
        payload.put("event_ts", eventTs == null ? 0L : eventTs);
        payload.put("dwell_ms", dwellMs == null ? 0L : dwellMs);
        payload.put("rank_position", rankPosition == null ? 0 : rankPosition);
        payload.put("surface", surface == null ? "" : surface);
        payload.put("page_no", pageNo == null ? 0 : pageNo);
        payload.put("device_type", deviceType == null ? "" : deviceType);
        payload.put("recall_source", recallSource == null ? "" : recallSource);
        return payload;
    }
}

