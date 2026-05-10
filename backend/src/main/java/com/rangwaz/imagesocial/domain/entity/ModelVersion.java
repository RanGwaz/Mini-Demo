package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("model_versions")
public class ModelVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String modelName;
    private String version;
    private String modelType;
    private String status;
    private Long datasetId;
    private String artifactUri;
    private Boolean shadowEnabled;
    private Boolean onlineEnabled;
    private BigDecimal trafficPercent;
    private String guardrailJson;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
