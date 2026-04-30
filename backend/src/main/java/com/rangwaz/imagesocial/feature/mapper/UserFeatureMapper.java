package com.rangwaz.imagesocial.feature.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rangwaz.imagesocial.feature.entity.UserFeature;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户离线特征 Mapper。
 * 继承 BaseMapper 即可，selectById / selectList 满足推荐服务所有读取需求。
 */
@Mapper
public interface UserFeatureMapper extends BaseMapper<UserFeature> {
}
