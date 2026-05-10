package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("creator_profiles")
public class CreatorProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String domainTags;
    private String creatorLevel;
    private BigDecimal qualityScore;
    private String violationStatus;
    private String monetizationStatus;
    private String commercialStatus;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
