package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("commercial_content_profiles")
public class CommercialContentProfile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private String brandName;
    private String disclosureType;
    private String campaignCode;
    private String status;
    private String bidType;
    private Long budgetCents;
    private String landingUrl;
    private String configJson;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
