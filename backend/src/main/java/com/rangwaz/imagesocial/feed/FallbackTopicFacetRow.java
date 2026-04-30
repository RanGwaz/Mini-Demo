package com.rangwaz.imagesocial.feed;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class FallbackTopicFacetRow {
    private String clusterKey;
    private String clusterLabel;
    private Integer postCount;
    private BigDecimal hotScoreSum;
}
