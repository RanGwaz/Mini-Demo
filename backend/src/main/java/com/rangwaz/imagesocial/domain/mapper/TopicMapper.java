package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.Topic;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TopicMapper extends BaseMapper<Topic> {

    @Select("""
            <script>
            SELECT DISTINCT t.*
            FROM topics t
            LEFT JOIN topic_aliases ta ON ta.topic_id = t.id
            WHERE t.status = 'ACTIVE'
            <if test='keyword != null and keyword != ""'>
              AND (
                t.name LIKE CONCAT('%', #{keyword}, '%')
                OR t.slug LIKE CONCAT('%', #{keyword}, '%')
                OR ta.alias LIKE CONCAT('%', #{keyword}, '%')
                OR ta.normalized_alias LIKE CONCAT('%', #{keyword}, '%')
              )
            </if>
            ORDER BY t.hot_score DESC, t.post_count DESC, t.updated_at DESC
            LIMIT #{limit}
            </script>
            """)
    List<Topic> searchActiveTopics(@Param("keyword") String keyword,
                                   @Param("limit") int limit);

    @Select("""
            SELECT *
            FROM topics
            WHERE status = 'ACTIVE'
            ORDER BY hot_score DESC, post_count DESC, updated_at DESC
            LIMIT #{limit}
            """)
    List<Topic> selectTrendingTopics(@Param("limit") int limit);

    @Select("""
            SELECT *
            FROM topics
            WHERE slug = #{slug}
            LIMIT 1
            """)
    Topic selectBySlug(@Param("slug") String slug);

    @Select("""
            SELECT *
            FROM topics
            WHERE name = #{name}
            LIMIT 1
            """)
    Topic selectByName(@Param("name") String name);

    @Select("""
            SELECT t.*
            FROM topics t
            JOIN topic_channel_bindings b ON b.topic_id = t.id
            WHERE b.channel_code = #{channelCode}
              AND b.status = 'ACTIVE'
              AND t.status = 'ACTIVE'
            ORDER BY b.weight DESC, t.hot_score DESC, t.post_count DESC, t.updated_at DESC
            LIMIT #{limit}
            """)
    List<Topic> selectByChannel(@Param("channelCode") String channelCode,
                                @Param("limit") int limit);
}
