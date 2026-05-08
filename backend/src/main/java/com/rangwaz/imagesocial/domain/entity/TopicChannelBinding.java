package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topic_channel_bindings")
public class TopicChannelBinding {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long topicId;
    private String channelCode;
    private BigDecimal weight;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
