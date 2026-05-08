package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_topic_follows")
public class UserTopicFollow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long topicId;
    private String status;
    private String source;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
