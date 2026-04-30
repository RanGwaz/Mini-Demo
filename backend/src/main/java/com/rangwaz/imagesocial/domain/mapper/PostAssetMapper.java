package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.PostAsset;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PostAssetMapper extends BaseMapper<PostAsset> {

    @Select("SELECT * FROM post_assets WHERE post_id = #{postId} ORDER BY sort_order ASC, id ASC")
    List<PostAsset> selectByPostId(@Param("postId") Long postId);

    @Select("""
            <script>
            SELECT * FROM post_assets
            WHERE post_id IN
            <foreach collection='postIds' item='postId' open='(' separator=',' close=')'>
              #{postId}
            </foreach>
            ORDER BY post_id ASC, sort_order ASC, id ASC
            </script>
            """)
    List<PostAsset> selectByPostIds(@Param("postIds") List<Long> postIds);
}
