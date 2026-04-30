package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("topic_clusters")
public class TopicCluster {

    @TableId
    private String clusterKey;
    private String parentClusterKey;
    private Integer clusterLevel;
    private String clusterLabel;
    private String keywordsJson;
    private String samplePostIdsJson;
    private Integer postCount;
    private String taxonomyVersion;
    private LocalDateTime updatedAt;
}
