package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.UserFollow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT * FROM user_follows WHERE follower_id = #{userId}")
    List<UserFollow> selectFollowing(@Param("userId") Long userId);

    @Select("SELECT * FROM user_follows WHERE followed_id = #{userId}")
    List<UserFollow> selectFollowers(@Param("userId") Long userId);

    @Select("""
            SELECT * FROM user_follows
            WHERE follower_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<UserFollow> selectFollowingPaged(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Select("""
            SELECT * FROM user_follows
            WHERE followed_id = #{userId}
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<UserFollow> selectFollowersPaged(@Param("userId") Long userId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);
}
