package com.rangwaz.imagesocial.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.domain.entity.User;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
            <script>
            SELECT * FROM users
            WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>
              #{id}
            </foreach>
            </script>
            """)
    List<User> selectByIds(@Param("ids") List<Long> ids);
}
