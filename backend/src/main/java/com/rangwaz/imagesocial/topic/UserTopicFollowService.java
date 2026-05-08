package com.rangwaz.imagesocial.topic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.domain.entity.UserTopicFollow;
import com.rangwaz.imagesocial.domain.mapper.UserTopicFollowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserTopicFollowService {

    private final UserTopicFollowMapper userTopicFollowMapper;
    private final TopicService topicService;

    public UserTopicFollowService(UserTopicFollowMapper userTopicFollowMapper,
                                  TopicService topicService) {
        this.userTopicFollowMapper = userTopicFollowMapper;
        this.topicService = topicService;
    }

    @Transactional
    public void follow(Long userId, Long topicId) {
        Topic topic = topicService.requireActiveById(topicId);
        if (topic == null) {
            throw new BusinessException("Topic does not exist");
        }
        UserTopicFollow existing = userTopicFollowMapper.selectOne(new LambdaQueryWrapper<UserTopicFollow>()
                .eq(UserTopicFollow::getUserId, userId)
                .eq(UserTopicFollow::getTopicId, topicId));
        if (existing == null) {
            UserTopicFollow follow = new UserTopicFollow();
            follow.setUserId(userId);
            follow.setTopicId(topicId);
            follow.setStatus("ACTIVE");
            follow.setSource("USER");
            userTopicFollowMapper.insert(follow);
            return;
        }
        if (!"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
            userTopicFollowMapper.update(null, new LambdaUpdateWrapper<UserTopicFollow>()
                    .eq(UserTopicFollow::getId, existing.getId())
                    .set(UserTopicFollow::getStatus, "ACTIVE"));
        }
    }

    @Transactional
    public void unfollow(Long userId, Long topicId) {
        userTopicFollowMapper.update(null, new LambdaUpdateWrapper<UserTopicFollow>()
                .eq(UserTopicFollow::getUserId, userId)
                .eq(UserTopicFollow::getTopicId, topicId)
                .set(UserTopicFollow::getStatus, "INACTIVE"));
    }
}
