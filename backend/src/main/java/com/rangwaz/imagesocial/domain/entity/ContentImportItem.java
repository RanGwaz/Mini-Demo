package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("content_import_items")
public class ContentImportItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long batchId;
    private Long postId;
    private String title;
    private String content;
    private String channelCode;
    private String topicNames;
    private String imageUrls;
    private String status;
    private String errorMessage;
    private String rawPayload;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
