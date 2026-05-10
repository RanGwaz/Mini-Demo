package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("content_import_batches")
public class ContentImportBatch {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String sourceType;
    private String status;
    private Integer totalCount;
    private Integer successCount;
    private Integer failedCount;
    private Long operatorId;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
