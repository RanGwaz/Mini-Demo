package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("site_messages")
public class SiteMessage {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long senderId;
    private Long recipientId;
    private Long peerId;
    private String messageKind;
    private String title;
    private String content;
    private String actionUrl;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
