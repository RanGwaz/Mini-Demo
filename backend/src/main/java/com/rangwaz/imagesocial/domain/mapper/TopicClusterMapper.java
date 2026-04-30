package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.TopicCluster;
import com.rangwaz.imagesocial.feed.TopicFacetRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TopicClusterMapper extends BaseMapper<TopicCluster> {

    @Select("""
            <script>
            SELECT
                tc.cluster_key AS cluster_key,
                tc.parent_cluster_key AS parent_cluster_key,
                tc.cluster_level AS cluster_level,
                tc.cluster_label AS cluster_label,
                tc.keywords_json AS keywords_json,
                tc.post_count AS post_count,
                tc.taxonomy_version AS taxonomy_version,
                COALESCE(metrics.recent_post_count, 0) AS recent_post_count,
                COALESCE(metrics.hot_score_sum, 0) AS hot_score_sum
            FROM topic_clusters tc
            LEFT JOIN (
                SELECT
                    cluster_key,
                    SUM(recent_post_count) AS recent_post_count,
                    SUM(hot_score_sum) AS hot_score_sum
                FROM (
                    SELECT
                        topic_cluster_key AS cluster_key,
                        COUNT(*) AS recent_post_count,
                        SUM(COALESCE(hot_score, 0)) AS hot_score_sum
                    FROM posts
                    WHERE visibility = 'PUBLIC'
                      AND audit_status = 'APPROVED'
                      AND topic_cluster_key IS NOT NULL
                      AND topic_cluster_key != ''
                      AND created_at <![CDATA[>=]]> DATE_SUB(NOW(), INTERVAL #{recentDays} DAY)
                    GROUP BY topic_cluster_key

                    UNION ALL

                    SELECT
                        subtopic_cluster_key AS cluster_key,
                        COUNT(*) AS recent_post_count,
                        SUM(COALESCE(hot_score, 0)) AS hot_score_sum
                    FROM posts
                    WHERE visibility = 'PUBLIC'
                      AND audit_status = 'APPROVED'
                      AND subtopic_cluster_key IS NOT NULL
                      AND subtopic_cluster_key != ''
                      AND created_at <![CDATA[>=]]> DATE_SUB(NOW(), INTERVAL #{recentDays} DAY)
                    GROUP BY subtopic_cluster_key
                ) merged
                GROUP BY cluster_key
            ) metrics ON metrics.cluster_key = tc.cluster_key
            WHERE tc.cluster_level <![CDATA[<=]]> #{maxLevel}
            ORDER BY
                (
                    COALESCE(metrics.hot_score_sum, 0) * 0.60
                    + COALESCE(metrics.recent_post_count, 0) * 6.00
                    + COALESCE(tc.post_count, 0) * 0.12
                ) DESC,
                tc.post_count DESC,
                tc.updated_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<TopicFacetRow> selectFacetRows(@Param("recentDays") int recentDays,
                                        @Param("maxLevel") int maxLevel,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT cluster_key
            FROM topic_clusters
            WHERE
            <trim prefix="(" suffix=")" prefixOverrides="OR">
                <foreach collection='terms' item='term'>
                    OR cluster_key = #{term}
                    OR cluster_label LIKE CONCAT('%', #{term}, '%')
                    OR keywords_json LIKE CONCAT('%', #{term}, '%')
                </foreach>
            </trim>
            ORDER BY cluster_level ASC, post_count DESC, updated_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<String> selectClusterKeysByTerms(@Param("terms") List<String> terms,
                                          @Param("limit") int limit);
}
