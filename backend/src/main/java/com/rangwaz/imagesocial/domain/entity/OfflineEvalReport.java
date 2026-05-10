package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("offline_eval_reports")
public class OfflineEvalReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long modelVersionId;
    private Long datasetId;
    private BigDecimal auc;
    private BigDecimal ndcg;
    private BigDecimal recallScore;
    private BigDecimal precisionScore;
    private String metricsJson;
    private String reportPath;
    private String status;
    private LocalDateTime createdAt;
}
