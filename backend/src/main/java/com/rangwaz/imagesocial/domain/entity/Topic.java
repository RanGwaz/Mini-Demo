package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topics")
public class Topic {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String slug;
    private String description;
    private String coverUrl;
    private String status;
    private String riskLevel;
    private String topicType;
    private String source;
    private Long parentTopicId;
    private Integer postCount;
    private Integer followerCount;
    private BigDecimal hotScore;
    private LocalDateTime lastTrendedAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
