package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.Channel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelMapper extends BaseMapper<Channel> {

    @Select("""
            SELECT *
            FROM channels
            WHERE status = 'ACTIVE'
              AND enabled = 1
            ORDER BY sort_order ASC, id ASC
            """)
    List<Channel> selectActiveChannels();

    @Select("""
            SELECT *
            FROM channels
            WHERE code = #{code}
            LIMIT 1
            """)
    Channel selectByCode(@Param("code") String code);
}
