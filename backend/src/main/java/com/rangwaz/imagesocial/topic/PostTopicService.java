package com.rangwaz.imagesocial.topic;

import com.rangwaz.imagesocial.domain.entity.PostTopic;
import com.rangwaz.imagesocial.domain.mapper.PostTopicMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
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

    public int addTopics(Long postId, Collection<Long> topicIds, String source) {
        if (postId == null || postId <= 0L || topicIds == null || topicIds.isEmpty()) {
            return 0;
        }
        Set<Long> normalizedTopicIds = new LinkedHashSet<>();
        for (Long topicId : topicIds) {
            if (topicId != null && topicId > 0L) {
                normalizedTopicIds.add(topicId);
            }
        }
        int inserted = 0;
        String safeSource = source == null || source.isBlank() ? "SYSTEM" : source.trim();
        for (Long topicId : normalizedTopicIds) {
            PostTopic link = new PostTopic();
            link.setPostId(postId);
            link.setTopicId(topicId);
            link.setSource(safeSource);
            link.setConfidence(USER_CONFIDENCE);
            try {
                postTopicMapper.insert(link);
                inserted++;
            } catch (DuplicateKeyException ignored) {
                // Existing relation is fine for idempotent backfills.
            }
        }
        return inserted;
    }

    public void deleteByPostId(Long postId) {
        if (postId == null || postId <= 0L) {
            return;
        }
        postTopicMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostTopic>()
                .eq(PostTopic::getPostId, postId));
    }
}
