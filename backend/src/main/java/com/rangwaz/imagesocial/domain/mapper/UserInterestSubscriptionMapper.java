package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.UserInterestSubscription;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserInterestSubscriptionMapper extends BaseMapper<UserInterestSubscription> {

    @Select("""
            SELECT *
            FROM user_interest_subscriptions
            WHERE user_id = #{userId}
              AND status = 'ACTIVE'
            ORDER BY weight DESC, updated_at DESC, id DESC
            LIMIT #{limit}
            """)
    List<UserInterestSubscription> selectActiveByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
