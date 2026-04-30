package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_events")
public class UserEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String eventType;
    private String targetType;
    private Long targetId;
    private String requestId;
    private String sessionId;
    private String surface;
    private Integer pageNo;
    private Integer rankPosition;
    private String recallSource;
    private Long dwellMs;
    private String deviceType;
    private String experimentId;
    private String payloadJson;
    private LocalDateTime createdAt;
}
