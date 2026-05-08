package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topic_trend_snapshots")
public class TopicTrendSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private String windowType;
    private Integer postCount;
    private Long viewCount;
    private Long interactionCount;
    private BigDecimal hotScore;
    private LocalDateTime snapshotAt;
    private LocalDateTime createdAt;
}
