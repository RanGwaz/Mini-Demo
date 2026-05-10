package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("admin_operation_logs")
public class AdminOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String action;
    private String targetType;
    private String targetId;
    private String detailJson;
    private LocalDateTime createdAt;
}
