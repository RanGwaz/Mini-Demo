package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("content_reports")
public class ContentReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private Long postId;
    private String reason;
    private LocalDateTime createdAt;
}
