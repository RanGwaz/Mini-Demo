package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("content_moderation_cases")
public class ContentModerationCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long reporterId;
    private String reason;
    private String status;
    private String priority;
    private String riskLevel;
    private Long assignedTo;
    private String decision;
    private String actionNote;
    private Long operatorId;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
