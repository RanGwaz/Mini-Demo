package com.rangwaz.imagesocial.topic;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.domain.mapper.TopicMapper;
import com.rangwaz.imagesocial.topic.dto.TopicView;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TopicService {

    private static final int DEFAULT_SEARCH_LIMIT = 20;
    private static final int MAX_SEARCH_LIMIT = 100;
    private static final int MAX_PUBLISH_TOPICS = 10;
    private static final Pattern SLUG_SAFE_CHARS = Pattern.compile("[^a-z0-9]+");

    private final TopicMapper topicMapper;

    public TopicService(TopicMapper topicMapper) {
        this.topicMapper = topicMapper;
    }

    public List<Topic> searchActiveTopics(String keyword, int limit) {
        return topicMapper.searchActiveTopics(normalizeKeyword(keyword), normalizeLimit(limit));
    }

    public List<Topic> listTrendingTopics(int limit) {
        return topicMapper.selectTrendingTopics(normalizeLimit(limit));
    }

    public List<Topic> listChannelTopics(String channelCode, int limit) {
        if (channelCode == null || channelCode.isBlank()) {
            return List.of();
        }
        return topicMapper.selectByChannel(channelCode.trim(), normalizeLimit(limit));
    }

    public Topic findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        return topicMapper.selectBySlug(slug.trim());
    }

    public Topic requireActiveById(Long topicId) {
        if (topicId == null || topicId <= 0L) {
            return null;
        }
        Topic topic = topicMapper.selectById(topicId);
        if (topic == null || !"ACTIVE".equalsIgnoreCase(topic.getStatus())) {
            return null;
        }
        return topic;
    }

    public List<Topic> resolvePublishTopics(Collection<Long> topicIds,
                                            Collection<String> topicNames,
                                            Collection<String> legacyTags,
                                            Channel channel,
                                            Long createdBy) {
        Map<Long, Topic> resolved = new LinkedHashMap<>();
        if (topicIds != null && !topicIds.isEmpty()) {
            List<Topic> topics = topicMapper.selectList(new LambdaQueryWrapper<Topic>()
                    .in(Topic::getId, topicIds)
                    .eq(Topic::getStatus, "ACTIVE"));
            for (Topic topic : topics) {
                if (resolved.size() >= MAX_PUBLISH_TOPICS) {
                    break;
                }
                resolved.put(topic.getId(), topic);
            }
        }

        addResolvedNames(resolved, topicNames, channel, createdBy);
        addResolvedNames(resolved, legacyTags, channel, createdBy);
        return resolved.values().stream().limit(MAX_PUBLISH_TOPICS).toList();
    }

    public Topic findOrCreateUserTopic(String rawName, Long createdBy) {
        String name = normalizeTopicName(rawName);
        if (name == null) {
            return null;
        }
        Topic existing = topicMapper.selectByName(name);
        if (existing != null) {
            return existing;
        }
        String slug = generateSlug(name);
        existing = topicMapper.selectBySlug(slug);
        if (existing != null) {
            return existing;
        }

        Topic topic = new Topic();
        topic.setName(name);
        topic.setSlug(slug);
        topic.setDescription("");
        topic.setCoverUrl("");
        topic.setStatus("ACTIVE");
        topic.setRiskLevel("NORMAL");
        topic.setTopicType("USER");
        topic.setSource("USER");
        topic.setPostCount(0);
        topic.setFollowerCount(0);
        topic.setHotScore(BigDecimal.ZERO);
        topic.setCreatedBy(createdBy);
        try {
            topicMapper.insert(topic);
            return topic;
        } catch (DuplicateKeyException exception) {
            Topic retry = topicMapper.selectByName(name);
            if (retry != null) {
                return retry;
            }
            return topicMapper.selectBySlug(slug);
        }
    }

    public TopicView toView(Topic topic) {
        return new TopicView(
                topic.getId(),
                topic.getName(),
                topic.getSlug(),
                Objects.toString(topic.getDescription(), ""),
                Objects.toString(topic.getCoverUrl(), ""),
                topic.getStatus(),
                topic.getRiskLevel(),
                topic.getTopicType(),
                topic.getPostCount(),
                topic.getFollowerCount(),
                topic.getHotScore()
        );
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(limit, MAX_SEARCH_LIMIT);
    }

    private void addResolvedNames(Map<Long, Topic> resolved,
                                  Collection<String> rawNames,
                                  Channel channel,
                                  Long createdBy) {
        if (rawNames == null || rawNames.isEmpty()) {
            return;
        }
        for (String rawName : rawNames) {
            if (resolved.size() >= MAX_PUBLISH_TOPICS) {
                return;
            }
            String name = normalizeTopicName(rawName);
            if (name == null || isChannelName(name, channel)) {
                continue;
            }
            Topic topic = findOrCreateUserTopic(name, createdBy);
            if (topic != null && topic.getId() != null && "ACTIVE".equalsIgnoreCase(topic.getStatus())) {
                resolved.putIfAbsent(topic.getId(), topic);
            }
        }
    }

    private boolean isChannelName(String name, Channel channel) {
        if (channel == null || name == null) {
            return false;
        }
        return name.equalsIgnoreCase(Objects.toString(channel.getCode(), ""))
                || name.equalsIgnoreCase(Objects.toString(channel.getName(), ""));
    }

    private String normalizeTopicName(String rawName) {
        if (rawName == null) {
            return null;
        }
        String name = rawName.trim();
        while (name.startsWith("#")) {
            name = name.substring(1).trim();
        }
        if (name.isBlank()) {
            return null;
        }
        return name.length() > 64 ? name.substring(0, 64) : name;
    }

    private String generateSlug(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase();
        String slug = SLUG_SAFE_CHARS.matcher(ascii).replaceAll("-")
                .replaceAll("^-+|-+$", "");
        if (!slug.isBlank()) {
            return slug.length() > 96 ? slug.substring(0, 96) : slug;
        }
        return "topic-" + Integer.toHexString(name.hashCode());
    }
}
