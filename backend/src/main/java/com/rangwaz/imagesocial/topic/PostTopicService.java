package com.rangwaz.imagesocial.topic;

import com.rangwaz.imagesocial.domain.entity.PostTopic;
import com.rangwaz.imagesocial.domain.mapper.PostTopicMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PostTopicService {

    private static final BigDecimal USER_CONFIDENCE = BigDecimal.ONE;

    private final PostTopicMapper postTopicMapper;

    public PostTopicService(PostTopicMapper postTopicMapper) {
        this.postTopicMapper = postTopicMapper;
    }

    public List<PostTopic> listByPostId(Long postId) {
        if (postId == null || postId <= 0L) {
            return List.of();
        }
        return postTopicMapper.selectByPostId(postId);
    }

    public void replaceUserTopics(Long postId, Collection<Long> topicIds) {
        if (postId == null || postId <= 0L) {
            return;
        }
        postTopicMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostTopic>()
                .eq(PostTopic::getPostId, postId)
                .eq(PostTopic::getSource, "USER"));

        Set<Long> normalizedTopicIds = new LinkedHashSet<>();
        if (topicIds != null) {
            for (Long topicId : topicIds) {
                if (topicId != null && topicId > 0L) {
                    normalizedTopicIds.add(topicId);
                }
            }
        }
        for (Long topicId : normalizedTopicIds) {
            PostTopic link = new PostTopic();
            link.setPostId(postId);
            link.setTopicId(topicId);
            link.setSource("USER");
            link.setConfidence(USER_CONFIDENCE);
            postTopicMapper.insert(link);
        }
    }
}
