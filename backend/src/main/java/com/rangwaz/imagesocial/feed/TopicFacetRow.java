package com.rangwaz.imagesocial.feed;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class TopicFacetRow {
    private String clusterKey;
    private String parentClusterKey;
    private Integer clusterLevel;
    private String clusterLabel;
    private String keywordsJson;
    private Integer postCount;
    private String taxonomyVersion;
    private Integer recentPostCount;
    private BigDecimal hotScoreSum;
}
