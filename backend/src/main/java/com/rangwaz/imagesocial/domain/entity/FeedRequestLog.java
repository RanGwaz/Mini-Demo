package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("feed_request_logs")
public class FeedRequestLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String requestId;
    private Long userId;
    private String surface;
    private Integer pageNo;
    private Integer pageSize;
    private String seed;
    private String filtersJson;
    private String userSegment;
    private String experimentId;
    private String experimentBucket;
    private Integer totalCandidates;
    private Integer returnedCount;
    private Long latencyMs;
    private Boolean degraded;
    private LocalDateTime createdAt;
}
