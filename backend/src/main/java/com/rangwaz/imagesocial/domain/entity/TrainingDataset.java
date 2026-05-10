package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("training_datasets")
public class TrainingDataset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String datasetType;
    private String status;
    private String splitStrategy;
    private LocalDateTime sourceWindowStart;
    private LocalDateTime sourceWindowEnd;
    private Long rowCount;
    private Long positiveCount;
    private Long negativeCount;
    private String filePath;
    private String metricsJson;
    private Long operatorId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
