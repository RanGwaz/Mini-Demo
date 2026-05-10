package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feed_impression_logs")
public class FeedImpressionLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private Long postId;
    private Integer rankPosition;
    private String recallSource;
    private BigDecimal rankScore;
    private String channelCode;
    private String topicNames;
    private String reason;
    private LocalDateTime createdAt;
}
