package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.PostTopic;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostTopicMapper extends BaseMapper<PostTopic> {

    @Select("""
            SELECT *
            FROM post_topics
            WHERE post_id = #{postId}
            ORDER BY id ASC
            """)
    List<PostTopic> selectByPostId(@Param("postId") Long postId);
}
