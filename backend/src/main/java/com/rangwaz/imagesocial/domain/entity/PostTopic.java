package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("post_topics")
public class PostTopic {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long topicId;
    private String source;
    private BigDecimal confidence;
    private LocalDateTime createdAt;
}
