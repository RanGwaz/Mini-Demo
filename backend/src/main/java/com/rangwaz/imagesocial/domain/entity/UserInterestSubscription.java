package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("user_interest_subscriptions")
public class UserInterestSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String facetType;
    private String facetKey;
    private String facetLabel;
    private BigDecimal weight;
    private String source;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
