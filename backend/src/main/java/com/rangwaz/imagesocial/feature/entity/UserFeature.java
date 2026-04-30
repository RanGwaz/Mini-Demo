package com.rangwaz.imagesocial.feature.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户离线特征实体，对应 MySQL user_features 表。
 * 由 Python 离线脚本定期计算并写入，Java 侧只读。
 */
@Data
@TableName("user_features")
public class UserFeature {

    @TableId
    private Long userId;

    /** 注册天数 */
    private Integer registerDays;

    /** 总发帖数 */
    private Integer totalPosts;

    /** 总点赞数（用户主动给出的） */
    private Integer totalLikesGiven;

    /** 总收藏数 */
    private Integer totalFavoritesGiven;

    /** 总评论数 */
    private Integer totalCommentsGiven;

    /** 粉丝数 */
    private Integer totalFollowers;

    /** 关注数 */
    private Integer totalFollowing;

    /** 近7天日均点赞数 */
    private BigDecimal avgWeeklyLikes;

    /** 近7天日均收藏数 */
    private BigDecimal avgWeeklyFavorites;

    /**
     * 用户最感兴趣的标签（逗号分隔，top5）。
     * 由用户点赞/收藏帖子的标签统计得出。
     */
    private String topInterestTags;
    private String topInterestTopics;
    private String preferredStyles;

    /** 其他复杂特征的 JSON 扩展字段（备用） */
    private String featureJson;

    /** 最后更新时间 */
    private LocalDateTime updatedAt;
}
