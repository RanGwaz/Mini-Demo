package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topic_aliases")
public class TopicAlias {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private String alias;
    private String normalizedAlias;
    private String source;
    private LocalDateTime createdAt;
}
