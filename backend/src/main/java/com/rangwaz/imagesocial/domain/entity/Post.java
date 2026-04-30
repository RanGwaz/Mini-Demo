package com.rangwaz.imagesocial.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("posts")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String title;
    private String content;
    private String tags;
    private String topicPath;
    private String semanticTags;
    private String styleTags;
    private String taxonomyJson;
    private String topicClusterKey;
    private String subtopicClusterKey;
    private String coverUrl;
    private String thumbUrl;
    private String visibility;
    private String auditStatus;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer commentCount;
    private Long viewCount;
    private BigDecimal hotScore;
    private BigDecimal qualityScore;
    private BigDecimal aestheticScore;
    private BigDecimal safetyScore;
    private String embeddingVersion;
    private String taxonomyVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
