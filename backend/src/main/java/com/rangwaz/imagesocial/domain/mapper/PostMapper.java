package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.feed.FallbackTopicFacetRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostMapper extends BaseMapper<Post> {

    @Select("""
            <script>
            SELECT
              id,
              author_id,
              channel_code,
              post_type,
              title,
              content,
              extra,
              tags,
              topic_path,
              semantic_tags,
              style_tags,
              taxonomy_json,
              topic_cluster_key,
              subtopic_cluster_key,
              cover_url,
              thumb_url,
              visibility,
              audit_status,
              like_count,
              favorite_count,
              comment_count,
              share_count,
              CAST(view_count AS SIGNED) AS view_count,
              hot_score,
              quality_score,
              aesthetic_score,
              safety_score,
              embedding_version,
              taxonomy_version,
              created_at,
              updated_at
            FROM posts
            WHERE visibility = 'PUBLIC' AND audit_status = 'APPROVED'
            ORDER BY hot_score DESC, created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectHotPosts(@Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.* FROM posts p
            INNER JOIN user_follows uf ON uf.followed_id = p.author_id
            WHERE uf.follower_id = #{userId}
              AND p.visibility = 'PUBLIC'
              AND p.audit_status = 'APPROVED'
            ORDER BY p.created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectSocialPosts(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND author_id != #{userId}
              AND (
              <foreach collection='tagPatterns' item='pattern' separator=' OR '>
                tags LIKE #{pattern}
                OR semantic_tags LIKE #{pattern}
                OR style_tags LIKE #{pattern}
                OR topic_path LIKE #{pattern}
                OR topic_cluster_key LIKE #{pattern}
                OR subtopic_cluster_key LIKE #{pattern}
              </foreach>
              )
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectContentPosts(@Param("userId") Long userId,
                                  @Param("tagPatterns") List<String> tagPatterns,
                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND author_id != #{userId}
              <if test='excludeIds != null and excludeIds.size() > 0'>
                AND id NOT IN
                <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>
                  #{id}
                </foreach>
              </if>
              AND (
                <trim prefixOverrides="OR">
                  <if test='clusterKeys != null and clusterKeys.size() > 0'>
                    OR topic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                    OR subtopic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                  </if>
                  <if test='topicPrefixes != null and topicPrefixes.size() > 0'>
                    OR (
                      <foreach collection='topicPrefixes' item='prefix' separator=' OR '>
                        topic_path LIKE CONCAT(#{prefix}, '%')
                      </foreach>
                    )
                  </if>
                </trim>
              )
            ORDER BY hot_score DESC, created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectClusterPosts(@Param("userId") Long userId,
                                  @Param("clusterKeys") List<String> clusterKeys,
                                  @Param("topicPrefixes") List<String> topicPrefixes,
                                  @Param("excludeIds") List<Long> excludeIds,
                                  @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*,
              (
                0
                <if test='clusterKeys != null and clusterKeys.size() > 0'>
                  + CASE WHEN p.topic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                    THEN 34 ELSE 0 END
                  + CASE WHEN p.subtopic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                    THEN 28 ELSE 0 END
                </if>
                <if test='topicPrefixes != null and topicPrefixes.size() > 0'>
                  + CASE WHEN (
                    <foreach collection='topicPrefixes' item='prefix' separator=' OR '>
                      p.topic_path LIKE CONCAT(#{prefix}, '%')
                    </foreach>
                  ) THEN 24 ELSE 0 END
                </if>
                <if test='termPatterns != null and termPatterns.size() > 0'>
                  <foreach collection='termPatterns' item='pattern'>
                    + CASE WHEN p.tags LIKE #{pattern} THEN 14 ELSE 0 END
                    + CASE WHEN p.semantic_tags LIKE #{pattern} THEN 12 ELSE 0 END
                    + CASE WHEN p.style_tags LIKE #{pattern} THEN 10 ELSE 0 END
                    + CASE WHEN p.topic_path LIKE #{pattern} THEN 10 ELSE 0 END
                    + CASE WHEN p.title LIKE #{pattern} THEN 8 ELSE 0 END
                    + CASE WHEN p.content LIKE #{pattern} THEN 3 ELSE 0 END
                  </foreach>
                </if>
                + LEAST(COALESCE(p.hot_score, 0), 90) * 0.08
                + COALESCE(p.quality_score, 0) * 10
                + COALESCE(p.aesthetic_score, 0) * 8
                + COALESCE(p.safety_score, 1) * 4
                + (1.0 / (1.0 + GREATEST(0, TIMESTAMPDIFF(HOUR, p.created_at, NOW())) / 36.0))
              ) AS semantic_match_score
            FROM posts p
            WHERE p.visibility = 'PUBLIC'
              AND p.audit_status = 'APPROVED'
              <if test='userId != null and userId >= 0'>
                AND p.author_id != #{userId}
              </if>
              <if test='excludeIds != null and excludeIds.size() > 0'>
                AND p.id NOT IN
                <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>
                  #{id}
                </foreach>
              </if>
              AND (
                <trim prefixOverrides="OR">
                  <if test='clusterKeys != null and clusterKeys.size() > 0'>
                    OR p.topic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                    OR p.subtopic_cluster_key IN
                    <foreach collection='clusterKeys' item='key' open='(' separator=',' close=')'>
                      #{key}
                    </foreach>
                  </if>
                  <if test='topicPrefixes != null and topicPrefixes.size() > 0'>
                    OR (
                      <foreach collection='topicPrefixes' item='prefix' separator=' OR '>
                        p.topic_path LIKE CONCAT(#{prefix}, '%')
                      </foreach>
                    )
                  </if>
                  <if test='termPatterns != null and termPatterns.size() > 0'>
                    OR (
                      <foreach collection='termPatterns' item='pattern' separator=' OR '>
                        p.tags LIKE #{pattern}
                        OR p.semantic_tags LIKE #{pattern}
                        OR p.style_tags LIKE #{pattern}
                        OR p.topic_path LIKE #{pattern}
                        OR p.title LIKE #{pattern}
                        OR p.content LIKE #{pattern}
                      </foreach>
                    )
                  </if>
                </trim>
              )
            ORDER BY semantic_match_score DESC,
                     MOD(ABS(p.id * 1103515245 + #{seedShift}), 2147483647) ASC,
                     p.created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectSemanticMatchedPosts(@Param("userId") Long userId,
                                          @Param("termPatterns") List<String> termPatterns,
                                          @Param("topicPrefixes") List<String> topicPrefixes,
                                          @Param("clusterKeys") List<String> clusterKeys,
                                          @Param("excludeIds") List<Long> excludeIds,
                                          @Param("seedShift") int seedShift,
                                          @Param("limit") int limit);

    @Select("""
            <script>
            SELECT
              COALESCE(NULLIF(topic_cluster_key, ''), CONCAT('topic:', COALESCE(NULLIF(SUBSTRING_INDEX(topic_path, '/', 1), ''), 'unknown'))) AS cluster_key,
              COALESCE(NULLIF(SUBSTRING_INDEX(topic_path, '/', 1), ''), 'discover') AS cluster_label,
              COUNT(*) AS post_count,
              SUM(COALESCE(hot_score, 0)) AS hot_score_sum
            FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND created_at <![CDATA[>=]]> DATE_SUB(NOW(), INTERVAL #{recentDays} DAY)
            GROUP BY cluster_key, cluster_label
            ORDER BY hot_score_sum DESC, post_count DESC
            LIMIT #{limit}
            </script>
            """)
    List<FallbackTopicFacetRow> selectFallbackTopicFacets(@Param("recentDays") int recentDays, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT COUNT(*) FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND author_id != #{userId}
            </script>
            """)
    long countExplorePosts(@Param("userId") Long userId);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND author_id != #{userId}
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Post> selectExplorePostsWindow(@Param("userId") Long userId,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE author_id = #{authorId}
            ORDER BY created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectByAuthorId(@Param("authorId") Long authorId, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND (title LIKE #{keyword} OR content LIKE #{keyword} OR tags LIKE #{keyword})
            ORDER BY hot_score DESC, created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> searchPosts(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND (title LIKE #{keyword} OR content LIKE #{keyword} OR tags LIKE #{keyword})
            """)
    long countSearchPosts(@Param("keyword") String keyword);

    @Select("""
            <script>
            SELECT tags, semantic_tags, style_tags
            FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND created_at <![CDATA[>=]]> DATE_SUB(NOW(), INTERVAL #{recentDays} DAY)
              AND (
                COALESCE(tags, '') != ''
                OR COALESCE(semantic_tags, '') != ''
                OR COALESCE(style_tags, '') != ''
              )
            ORDER BY hot_score DESC, created_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Post> selectRecentTagSamples(@Param("recentDays") int recentDays, @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE visibility = 'PUBLIC'
              AND audit_status = 'APPROVED'
              AND (title LIKE #{keyword} OR content LIKE #{keyword} OR tags LIKE #{keyword})
            ORDER BY hot_score DESC, created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Post> searchPostsPage(@Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("limit") int limit);

    @Select("""
            <script>
            SELECT p.*
            FROM (
              SELECT
                n.neighbor_post_id,
                MAX(n.score) AS i2i_score
              FROM post_i2i_neighbors n
              WHERE n.post_id IN
                <foreach collection='seedPostIds' item='id' open='(' separator=',' close=')'>
                  #{id}
                </foreach>
                <if test='excludeIds != null and excludeIds.size() > 0'>
                  AND n.neighbor_post_id NOT IN
                  <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>
                    #{id}
                  </foreach>
                </if>
              GROUP BY n.neighbor_post_id
              ORDER BY i2i_score DESC
              LIMIT #{limit}
            ) candidate
            INNER JOIN posts p ON p.id = candidate.neighbor_post_id
            WHERE p.visibility = 'PUBLIC'
              AND p.audit_status = 'APPROVED'
              <if test='userId != null and userId >= 0'>
                AND p.author_id != #{userId}
              </if>
            ORDER BY candidate.i2i_score DESC, p.hot_score DESC, p.created_at DESC
            </script>
            """)
    List<Post> selectI2INeighborPosts(@Param("userId") Long userId,
                                      @Param("seedPostIds") List<Long> seedPostIds,
                                      @Param("excludeIds") List<Long> excludeIds,
                                      @Param("limit") int limit);

    @Select("""
            <script>
            SELECT * FROM posts
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
    List<Post> selectByIds(@Param("ids") List<Long> ids);

    @Update("""
            UPDATE posts
            SET like_count = like_count + #{likeDelta},
                favorite_count = favorite_count + #{favoriteDelta},
                comment_count = comment_count + #{commentDelta},
                view_count = view_count + #{viewDelta},
                hot_score = hot_score + #{scoreDelta}
            WHERE id = #{postId}
            """)
    void updateCounters(@Param("postId") Long postId,
                        @Param("likeDelta") int likeDelta,
                        @Param("favoriteDelta") int favoriteDelta,
                        @Param("commentDelta") int commentDelta,
                        @Param("viewDelta") int viewDelta,
                        @Param("scoreDelta") double scoreDelta);
}
