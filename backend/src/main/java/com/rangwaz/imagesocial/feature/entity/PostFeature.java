package com.rangwaz.imagesocial.feature.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 帖子离线特征实体，对应 MySQL post_features 表。
 * 由 Python 离线脚本定期计算并写入，Java 侧只读。
 */
@Data
@TableName("post_features")
public class PostFeature {

    @TableId
    private Long postId;

    /** 作者 ID */
    private Long authorId;

    /** 发布时间（小时 0-23） */
    private Integer publishHour;

    /** 发布星期几（0=周一，6=周日） */
    private Integer publishDayOfWeek;

    /** 热度分（从 posts.hot_score 同步） */
    private BigDecimal hotScore;

    /** 浏览量 */
    private Long viewCount;

    /** 点赞数 */
    private Integer likeCount;

    /** 收藏数 */
    private Integer favoriteCount;

    /** 评论数 */
    private Integer commentCount;

    /** 标签（逗号分隔，从 posts.tags 同步） */
    private String tags;
    private String topicPath;
    private String semanticTags;
    private String styleTags;

    /** 作者粉丝数 */
    private Integer authorFansCount;

    /** 作者历史帖子平均热度分（衡量作者内容质量） */
    private BigDecimal authorAvgHotScore;
    private BigDecimal qualityScore;
    private BigDecimal aestheticScore;
    private BigDecimal safetyScore;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
