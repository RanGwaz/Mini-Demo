package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topic_merge_logs")
public class TopicMergeLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromTopicId;
    private Long toTopicId;
    private Long operatorId;
    private String reason;
    private LocalDateTime createdAt;
}
