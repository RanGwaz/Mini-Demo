package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("content_rebuild_tasks")
public class ContentRebuildTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskType;
    private String status;
    private String scopeType;
    private String scopeId;
    private Long batchId;
    private Long postId;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private String paramsJson;
    private String errorMessage;
    private Long operatorId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
