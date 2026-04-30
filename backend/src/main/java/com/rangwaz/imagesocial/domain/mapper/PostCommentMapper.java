package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.PostComment;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostCommentMapper extends BaseMapper<PostComment> {

    @Select("SELECT * FROM post_comments WHERE post_id = #{postId} ORDER BY created_at ASC")
    List<PostComment> selectByPostId(@Param("postId") Long postId);

    @Select("""
            SELECT * FROM post_comments
            WHERE post_id = #{postId}
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<PostComment> selectByPostIdPaged(@Param("postId") Long postId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Select("SELECT COUNT(1) FROM post_comments WHERE post_id = #{postId}")
    long countByPostId(@Param("postId") Long postId);
}
