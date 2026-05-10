package com.rangwaz.imagesocial.topic;

import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.topic.dto.TopicBackfillResult;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicBackfillService {

    private static final Logger log = LoggerFactory.getLogger(TopicBackfillService.class);
    private static final int DEFAULT_LIMIT = 500;
    private static final int MAX_LIMIT = 5000;

    private final PostMapper postMapper;
    private final TopicService topicService;
    private final PostTopicService postTopicService;

    public TopicBackfillService(PostMapper postMapper,
                                TopicService topicService,
                                PostTopicService postTopicService) {
        this.postMapper = postMapper;
        this.topicService = topicService;
        this.postTopicService = postTopicService;
    }

    @Transactional
    public TopicBackfillResult backfillFromPostTags(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<Post> posts = postMapper.selectPostsForTopicBackfill(safeLimit);
        int linkedTopics = 0;
        for (Post post : posts) {
            List<Topic> topics = parseTags(post.getTags()).stream()
                    .map(tag -> topicService.findOrCreateUserTopic(tag, post.getAuthorId()))
                    .filter(Objects::nonNull)
                    .toList();
            int inserted = postTopicService.addTopics(
                    post.getId(),
                    topics.stream().map(Topic::getId).toList(),
                    "LEGACY_TAG"
            );
            linkedTopics += inserted;
            log.info("Topic backfill postId={} tagCount={} insertedLinks={}", post.getId(), topics.size(), inserted);
        }
        return new TopicBackfillResult(posts.size(), linkedTopics);
    }

    private List<String> parseTags(String rawTags) {
        if (rawTags == null || rawTags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
                .toList();
    }
}
