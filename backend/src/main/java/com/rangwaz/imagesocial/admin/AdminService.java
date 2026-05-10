package com.rangwaz.imagesocial.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.channel.ChannelService;
import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.AdminOperationLog;
import com.rangwaz.imagesocial.domain.entity.Channel;
import com.rangwaz.imagesocial.domain.entity.ContentImportBatch;
import com.rangwaz.imagesocial.domain.entity.ContentImportItem;
import com.rangwaz.imagesocial.domain.entity.ContentRebuildTask;
import com.rangwaz.imagesocial.domain.entity.FeedImpressionLog;
import com.rangwaz.imagesocial.domain.entity.FeedRequestLog;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.PostTopic;
import com.rangwaz.imagesocial.domain.entity.Topic;
import com.rangwaz.imagesocial.domain.entity.TopicAlias;
import com.rangwaz.imagesocial.domain.entity.TopicChannelBinding;
import com.rangwaz.imagesocial.domain.entity.TopicMergeLog;
import com.rangwaz.imagesocial.domain.mapper.AdminOperationLogMapper;
import com.rangwaz.imagesocial.domain.mapper.ChannelMapper;
import com.rangwaz.imagesocial.domain.mapper.ContentImportBatchMapper;
import com.rangwaz.imagesocial.domain.mapper.ContentImportItemMapper;
import com.rangwaz.imagesocial.domain.mapper.ContentRebuildTaskMapper;
import com.rangwaz.imagesocial.domain.mapper.FeedImpressionLogMapper;
import com.rangwaz.imagesocial.domain.mapper.FeedRequestLogMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.PostTopicMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicAliasMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicChannelBindingMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicMapper;
import com.rangwaz.imagesocial.domain.mapper.TopicMergeLogMapper;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.CreatePostRequest;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.topic.TopicService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_REBUILD_TYPES = Set.of(
            "SEARCH_INDEX",
            "EMBEDDING",
            "FEATURE",
            "I2I",
            "THUMBNAIL",
            "SEMANTIC",
            "ALL"
    );

    private final ChannelMapper channelMapper;
    private final ChannelService channelService;
    private final TopicMapper topicMapper;
    private final TopicService topicService;
    private final TopicAliasMapper topicAliasMapper;
    private final TopicChannelBindingMapper topicChannelBindingMapper;
    private final TopicMergeLogMapper topicMergeLogMapper;
    private final PostMapper postMapper;
    private final PostService postService;
    private final PostTopicMapper postTopicMapper;
    private final ContentImportBatchMapper importBatchMapper;
    private final ContentImportItemMapper importItemMapper;
    private final ContentRebuildTaskMapper rebuildTaskMapper;
    private final FeedRequestLogMapper feedRequestLogMapper;
    private final FeedImpressionLogMapper feedImpressionLogMapper;
    private final AdminOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public AdminService(ChannelMapper channelMapper,
                        ChannelService channelService,
                        TopicMapper topicMapper,
                        TopicService topicService,
                        TopicAliasMapper topicAliasMapper,
                        TopicChannelBindingMapper topicChannelBindingMapper,
                        TopicMergeLogMapper topicMergeLogMapper,
                        PostMapper postMapper,
                        PostService postService,
                        PostTopicMapper postTopicMapper,
                        ContentImportBatchMapper importBatchMapper,
                        ContentImportItemMapper importItemMapper,
                        ContentRebuildTaskMapper rebuildTaskMapper,
                        FeedRequestLogMapper feedRequestLogMapper,
                        FeedImpressionLogMapper feedImpressionLogMapper,
                        AdminOperationLogMapper operationLogMapper,
                        ObjectMapper objectMapper) {
        this.channelMapper = channelMapper;
        this.channelService = channelService;
        this.topicMapper = topicMapper;
        this.topicService = topicService;
        this.topicAliasMapper = topicAliasMapper;
        this.topicChannelBindingMapper = topicChannelBindingMapper;
        this.topicMergeLogMapper = topicMergeLogMapper;
        this.postMapper = postMapper;
        this.postService = postService;
        this.postTopicMapper = postTopicMapper;
        this.importBatchMapper = importBatchMapper;
        this.importItemMapper = importItemMapper;
        this.rebuildTaskMapper = rebuildTaskMapper;
        this.feedRequestLogMapper = feedRequestLogMapper;
        this.feedImpressionLogMapper = feedImpressionLogMapper;
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    public AdminOverview overview() {
        long channels = channelMapper.selectCount(new LambdaQueryWrapper<Channel>().eq(Channel::getStatus, "ACTIVE"));
        long topics = topicMapper.selectCount(new LambdaQueryWrapper<Topic>().eq(Topic::getStatus, "ACTIVE"));
        long approvedPosts = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuditStatus, "APPROVED"));
        long pendingPosts = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuditStatus, "PENDING_REVIEW"));
        long rejectedPosts = postMapper.selectCount(new LambdaQueryWrapper<Post>().eq(Post::getAuditStatus, "REJECTED"));
        long importBatches = importBatchMapper.selectCount(new LambdaQueryWrapper<ContentImportBatch>());
        return new AdminOverview(channels, topics, approvedPosts, pendingPosts, rejectedPosts, importBatches);
    }

    public PageResponse<AdminChannelView> listChannels(String keyword, String status, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<Channel> wrapper = new LambdaQueryWrapper<>();
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(Channel::getCode, keyword)
                    .or().like(Channel::getName, keyword)
                    .or().like(Channel::getDescription, keyword));
        }
        if (hasText(status)) {
            wrapper.eq(Channel::getStatus, status.trim());
        }
        long total = channelMapper.selectCount(wrapper);
        List<Channel> records = channelMapper.selectList(wrapper
                .orderByAsc(Channel::getSortOrder)
                .orderByAsc(Channel::getId)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toAdminChannelView).toList(), total, safePage, safeSize);
    }

    @Transactional
    public AdminChannelView createChannel(Long operatorId, ChannelMutationRequest request) {
        String code = requireCode(request.code());
        if (channelMapper.selectByCode(code) != null) {
            throw new BusinessException("频道 code 已存在");
        }
        Channel channel = new Channel();
        applyChannelMutation(channel, request, code);
        channelMapper.insert(channel);
        log(operatorId, "CHANNEL_CREATE", "CHANNEL", code, request);
        return toAdminChannelView(channelMapper.selectByCode(code));
    }

    @Transactional
    public AdminChannelView updateChannel(Long operatorId, String code, ChannelMutationRequest request) {
        Channel channel = requireChannel(code);
        applyChannelMutation(channel, request, channel.getCode());
        channelMapper.updateById(channel);
        log(operatorId, "CHANNEL_UPDATE", "CHANNEL", channel.getCode(), request);
        return toAdminChannelView(channelMapper.selectByCode(channel.getCode()));
    }

    @Transactional
    public void updateChannelStatus(Long operatorId, String code, StatusMutationRequest request) {
        Channel channel = requireChannel(code);
        channel.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        if (request.enabled() != null) {
            channel.setEnabled(request.enabled());
        }
        channelMapper.updateById(channel);
        log(operatorId, "CHANNEL_STATUS", "CHANNEL", channel.getCode(), request);
    }

    @Transactional
    public void reorderChannels(Long operatorId, ReorderRequest request) {
        if (request.items() == null) {
            return;
        }
        for (ReorderItem item : request.items()) {
            Channel channel = requireChannel(item.code());
            channel.setSortOrder(item.sortOrder() == null ? 0 : item.sortOrder());
            channelMapper.updateById(channel);
        }
        log(operatorId, "CHANNEL_REORDER", "CHANNEL", "bulk", request);
    }

    public PageResponse<AdminTopicView> listTopics(String keyword, String status, String channelCode, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<Topic> wrapper = new LambdaQueryWrapper<>();
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(Topic::getName, keyword)
                    .or().like(Topic::getSlug, keyword)
                    .or().like(Topic::getDescription, keyword));
        }
        if (hasText(status)) {
            wrapper.eq(Topic::getStatus, status.trim());
        }
        if (hasText(channelCode)) {
            List<Long> topicIds = topicChannelBindingMapper.selectList(new LambdaQueryWrapper<TopicChannelBinding>()
                            .eq(TopicChannelBinding::getChannelCode, channelCode.trim())
                            .eq(TopicChannelBinding::getStatus, "ACTIVE"))
                    .stream()
                    .map(TopicChannelBinding::getTopicId)
                    .distinct()
                    .toList();
            if (topicIds.isEmpty()) {
                return new PageResponse<>(List.of(), 0, safePage, safeSize);
            }
            wrapper.in(Topic::getId, topicIds);
        }
        long total = topicMapper.selectCount(wrapper);
        List<Topic> topics = topicMapper.selectList(wrapper
                .orderByDesc(Topic::getHotScore)
                .orderByDesc(Topic::getUpdatedAt)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(topics.stream().map(this::toAdminTopicView).toList(), total, safePage, safeSize);
    }

    public AdminTopicDetail topicDetail(Long id) {
        Topic topic = requireTopic(id);
        List<TopicAlias> aliases = topicAliasMapper.selectList(new LambdaQueryWrapper<TopicAlias>()
                .eq(TopicAlias::getTopicId, id)
                .orderByAsc(TopicAlias::getId));
        List<TopicChannelBinding> bindings = topicChannelBindingMapper.selectList(new LambdaQueryWrapper<TopicChannelBinding>()
                .eq(TopicChannelBinding::getTopicId, id)
                .orderByDesc(TopicChannelBinding::getWeight));
        return new AdminTopicDetail(
                toAdminTopicView(topic),
                aliases.stream().map(alias -> new TopicAliasView(alias.getId(), alias.getAlias(), alias.getNormalizedAlias(), alias.getSource())).toList(),
                bindings.stream().map(binding -> new TopicBindingView(binding.getChannelCode(), binding.getWeight(), binding.getStatus())).toList()
        );
    }

    @Transactional
    public AdminTopicView createTopic(Long operatorId, TopicMutationRequest request) {
        String name = requireText(request.name(), "话题名称不能为空");
        String slug = requireCode(firstNonBlank(request.slug(), generateSlug(name)));
        if (topicMapper.selectBySlug(slug) != null) {
            throw new BusinessException("话题 slug 已存在");
        }
        Topic topic = new Topic();
        applyTopicMutation(topic, request, name, slug);
        topicMapper.insert(topic);
        bindTopicChannels(topic.getId(), request.channelCodes());
        log(operatorId, "TOPIC_CREATE", "TOPIC", String.valueOf(topic.getId()), request);
        return toAdminTopicView(topicMapper.selectById(topic.getId()));
    }

    @Transactional
    public AdminTopicView updateTopic(Long operatorId, Long id, TopicMutationRequest request) {
        Topic topic = requireTopic(id);
        applyTopicMutation(topic, request, firstNonBlank(request.name(), topic.getName()), topic.getSlug());
        topicMapper.updateById(topic);
        if (request.channelCodes() != null) {
            bindTopicChannels(id, request.channelCodes());
        }
        log(operatorId, "TOPIC_UPDATE", "TOPIC", String.valueOf(id), request);
        return toAdminTopicView(topicMapper.selectById(id));
    }

    @Transactional
    public void updateTopicStatus(Long operatorId, Long id, StatusMutationRequest request) {
        Topic topic = requireTopic(id);
        topic.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        topicMapper.updateById(topic);
        log(operatorId, "TOPIC_STATUS", "TOPIC", String.valueOf(id), request);
    }

    @Transactional
    public TopicAliasView addTopicAlias(Long operatorId, Long topicId, AliasMutationRequest request) {
        requireTopic(topicId);
        String aliasValue = requireText(request.alias(), "别名不能为空");
        TopicAlias alias = new TopicAlias();
        alias.setTopicId(topicId);
        alias.setAlias(aliasValue);
        alias.setNormalizedAlias(normalizeAlias(aliasValue));
        alias.setSource(firstNonBlank(request.source(), "ADMIN"));
        topicAliasMapper.insert(alias);
        log(operatorId, "TOPIC_ALIAS_ADD", "TOPIC", String.valueOf(topicId), request);
        return new TopicAliasView(alias.getId(), alias.getAlias(), alias.getNormalizedAlias(), alias.getSource());
    }

    @Transactional
    public void deleteTopicAlias(Long operatorId, Long aliasId) {
        TopicAlias alias = topicAliasMapper.selectById(aliasId);
        if (alias != null) {
            topicAliasMapper.deleteById(aliasId);
            log(operatorId, "TOPIC_ALIAS_DELETE", "TOPIC", String.valueOf(alias.getTopicId()), Map.of("aliasId", aliasId));
        }
    }

    @Transactional
    public TopicBindingView upsertTopicBinding(Long operatorId, Long topicId, BindingMutationRequest request) {
        requireTopic(topicId);
        String channelCode = requireCode(request.channelCode());
        requireChannel(channelCode);
        TopicChannelBinding binding = topicChannelBindingMapper.selectOne(new LambdaQueryWrapper<TopicChannelBinding>()
                .eq(TopicChannelBinding::getTopicId, topicId)
                .eq(TopicChannelBinding::getChannelCode, channelCode));
        if (binding == null) {
            binding = new TopicChannelBinding();
            binding.setTopicId(topicId);
            binding.setChannelCode(channelCode);
        }
        binding.setWeight(request.weight() == null ? BigDecimal.ONE : request.weight());
        binding.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        if (binding.getId() == null) {
            topicChannelBindingMapper.insert(binding);
        } else {
            topicChannelBindingMapper.updateById(binding);
        }
        log(operatorId, "TOPIC_BINDING_UPSERT", "TOPIC", String.valueOf(topicId), request);
        return new TopicBindingView(binding.getChannelCode(), binding.getWeight(), binding.getStatus());
    }

    @Transactional
    public void deleteTopicBinding(Long operatorId, Long topicId, String channelCode) {
        topicChannelBindingMapper.delete(new LambdaQueryWrapper<TopicChannelBinding>()
                .eq(TopicChannelBinding::getTopicId, topicId)
                .eq(TopicChannelBinding::getChannelCode, channelCode));
        log(operatorId, "TOPIC_BINDING_DELETE", "TOPIC", String.valueOf(topicId), Map.of("channelCode", channelCode));
    }

    @Transactional
    public void mergeTopics(Long operatorId, TopicMergeRequest request) {
        Long fromId = request.fromTopicId();
        Long toId = request.toTopicId();
        if (Objects.equals(fromId, toId)) {
            throw new BusinessException("不能合并到同一个话题");
        }
        Topic from = requireTopic(fromId);
        Topic to = requireTopic(toId);
        for (PostTopic link : postTopicMapper.selectList(new LambdaQueryWrapper<PostTopic>().eq(PostTopic::getTopicId, fromId))) {
            PostTopic moved = new PostTopic();
            moved.setPostId(link.getPostId());
            moved.setTopicId(toId);
            moved.setSource("MERGED");
            moved.setConfidence(link.getConfidence());
            try {
                postTopicMapper.insert(moved);
            } catch (DuplicateKeyException ignored) {
                // Target relation already exists.
            }
        }
        postTopicMapper.delete(new LambdaQueryWrapper<PostTopic>().eq(PostTopic::getTopicId, fromId));
        moveAliases(fromId, toId);
        moveBindings(fromId, toId);
        from.setStatus("MERGED");
        topicMapper.updateById(from);
        TopicMergeLog log = new TopicMergeLog();
        log.setFromTopicId(fromId);
        log.setToTopicId(toId);
        log.setOperatorId(operatorId);
        log.setReason(firstNonBlank(request.reason(), "ADMIN_MERGE"));
        topicMergeLogMapper.insert(log);
        log(operatorId, "TOPIC_MERGE", "TOPIC", fromId + "->" + toId, Map.of("from", from.getName(), "to", to.getName()));
    }

    public PageResponse<AdminPostView> listPosts(String keyword, String channelCode, String auditStatus, String visibility, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (hasText(keyword)) {
            wrapper.and(w -> w.like(Post::getTitle, keyword)
                    .or().like(Post::getContent, keyword)
                    .or().like(Post::getTags, keyword));
        }
        if (hasText(channelCode)) {
            wrapper.eq(Post::getChannelCode, channelCode.trim());
        }
        if (hasText(auditStatus)) {
            wrapper.eq(Post::getAuditStatus, auditStatus.trim());
        }
        if (hasText(visibility)) {
            wrapper.eq(Post::getVisibility, visibility.trim());
        }
        long total = postMapper.selectCount(wrapper);
        List<Post> records = postMapper.selectList(wrapper
                .orderByDesc(Post::getCreatedAt)
                .last(limitOffset(safePage, safeSize)));
        List<PostView> views = postService.toViews(records, post -> "后台内容管理");
        return new PageResponse<>(toAdminPostViews(records, views), total, safePage, safeSize);
    }

    @Transactional
    public AdminPostView moderatePost(Long operatorId, Long postId, PostModerationRequest request) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException("内容不存在");
        }
        if (hasText(request.auditStatus())) {
            post.setAuditStatus(request.auditStatus().trim());
        }
        if (hasText(request.visibility())) {
            post.setVisibility(request.visibility().trim());
        }
        if (request.qualityScore() != null) {
            post.setQualityScore(request.qualityScore());
        }
        if (request.safetyScore() != null) {
            post.setSafetyScore(request.safetyScore());
        }
        postMapper.updateById(post);
        log(operatorId, "POST_MODERATE", "POST", String.valueOf(postId), request);
        return toAdminPostViews(List.of(post), postService.toViews(List.of(post), ignored -> "后台内容管理")).get(0);
    }

    public PageResponse<ContentImportBatchView> listImportBatches(String status, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<ContentImportBatch> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) {
            wrapper.eq(ContentImportBatch::getStatus, status.trim());
        }
        long total = importBatchMapper.selectCount(wrapper);
        List<ContentImportBatch> records = importBatchMapper.selectList(wrapper
                .orderByDesc(ContentImportBatch::getCreatedAt)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toImportBatchView).toList(), total, safePage, safeSize);
    }

    @Transactional
    public ContentImportBatchView createImportBatch(Long operatorId, ImportBatchMutationRequest request) {
        ContentImportBatch batch = new ContentImportBatch();
        batch.setName(requireText(request.name(), "批次名称不能为空"));
        batch.setDescription(firstNonBlank(request.description(), ""));
        batch.setSourceType(firstNonBlank(request.sourceType(), "EDITORIAL"));
        batch.setStatus("DRAFT");
        batch.setTotalCount(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setOperatorId(operatorId);
        importBatchMapper.insert(batch);
        log(operatorId, "IMPORT_BATCH_CREATE", "IMPORT_BATCH", String.valueOf(batch.getId()), request);
        return toImportBatchView(importBatchMapper.selectById(batch.getId()));
    }

    @Transactional
    public ContentImportBatchView updateImportBatchStatus(Long operatorId, Long batchId, StatusMutationRequest request) {
        ContentImportBatch batch = requireBatch(batchId);
        batch.setStatus(normalizeStatus(request.status(), "DRAFT"));
        if ("RUNNING".equalsIgnoreCase(batch.getStatus())) {
            batch.setStartedAt(LocalDateTime.now());
        }
        if ("PUBLISHED".equalsIgnoreCase(batch.getStatus()) || "FAILED".equalsIgnoreCase(batch.getStatus())) {
            batch.setFinishedAt(LocalDateTime.now());
        }
        importBatchMapper.updateById(batch);
        log(operatorId, "IMPORT_BATCH_STATUS", "IMPORT_BATCH", String.valueOf(batchId), request);
        return toImportBatchView(importBatchMapper.selectById(batchId));
    }

    public PageResponse<ContentImportItemView> listImportItems(Long batchId, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<ContentImportItem> wrapper = new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId);
        long total = importItemMapper.selectCount(wrapper);
        List<ContentImportItem> records = importItemMapper.selectList(wrapper
                .orderByDesc(ContentImportItem::getCreatedAt)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toImportItemView).toList(), total, safePage, safeSize);
    }

    @Transactional
    public ContentImportItemView createImportItem(Long operatorId, Long batchId, ImportItemMutationRequest request) {
        ContentImportBatch batch = requireBatch(batchId);
        ContentImportItem item = new ContentImportItem();
        item.setBatchId(batchId);
        item.setTitle(firstNonBlank(request.title(), ""));
        item.setContent(firstNonBlank(request.content(), ""));
        item.setChannelCode(firstNonBlank(request.channelCode(), "campus"));
        item.setTopicNames(joinCsv(request.topics()));
        item.setImageUrls(joinCsv(request.imageUrls()));
        item.setStatus("DRAFT");
        item.setRawPayload(toJson(request));
        importItemMapper.insert(item);
        batch.setTotalCount(safeInt(batch.getTotalCount()) + 1);
        importBatchMapper.updateById(batch);
        log(operatorId, "IMPORT_ITEM_CREATE", "IMPORT_ITEM", String.valueOf(item.getId()), request);
        return toImportItemView(importItemMapper.selectById(item.getId()));
    }

    @Transactional
    public ContentImportItemView publishImportItem(Long operatorId, Long itemId) {
        ContentImportItem item = importItemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException("导入项不存在");
        }
        ContentImportBatch batch = requireBatch(item.getBatchId());
        item.setStatus("RUNNING");
        importItemMapper.updateById(item);
        try {
            PostView post = postService.createPost(operatorId, new CreatePostRequest(
                    item.getTitle(),
                    item.getContent(),
                    item.getChannelCode(),
                    item.getChannelCode(),
                    null,
                    splitCsv(item.getImageUrls()),
                    splitCsv(item.getTopicNames()),
                    null,
                    splitCsv(item.getTopicNames()),
                    Map.of("importBatchId", batch.getId(), "sourceType", batch.getSourceType()),
                    null
            ));
            item.setPostId(post.id());
            item.setStatus("PUBLISHED");
            item.setErrorMessage("");
            batch.setSuccessCount(safeInt(batch.getSuccessCount()) + 1);
        } catch (Exception exception) {
            item.setStatus("FAILED");
            item.setErrorMessage(exception.getMessage());
            batch.setFailedCount(safeInt(batch.getFailedCount()) + 1);
        }
        importItemMapper.updateById(item);
        refreshImportBatchCounters(batch.getId());
        log(operatorId, "IMPORT_ITEM_PUBLISH", "IMPORT_ITEM", String.valueOf(itemId), Map.of("status", item.getStatus()));
        return toImportItemView(importItemMapper.selectById(itemId));
    }

    @Transactional
    public ContentImportBatchView publishImportBatch(Long operatorId, Long batchId) {
        ContentImportBatch batch = requireBatch(batchId);
        batch.setStatus("RUNNING");
        batch.setStartedAt(LocalDateTime.now());
        importBatchMapper.updateById(batch);

        List<ContentImportItem> items = importItemMapper.selectList(new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId)
                .orderByAsc(ContentImportItem::getId));
        for (ContentImportItem item : items) {
            if (!"PUBLISHED".equalsIgnoreCase(item.getStatus())) {
                publishImportItem(operatorId, item.getId());
            }
        }

        ContentImportBatch refreshed = refreshImportBatchCounters(batchId);
        refreshed.setStatus(safeInt(refreshed.getFailedCount()) > 0 ? "PARTIAL_SUCCESS" : "PUBLISHED");
        refreshed.setFinishedAt(LocalDateTime.now());
        importBatchMapper.updateById(refreshed);
        log(operatorId, "IMPORT_BATCH_PUBLISH", "IMPORT_BATCH", String.valueOf(batchId), Map.of("items", items.size()));
        return toImportBatchView(importBatchMapper.selectById(batchId));
    }

    @Transactional
    public ContentImportBatchView rollbackImportBatch(Long operatorId, Long batchId) {
        ContentImportBatch batch = requireBatch(batchId);
        List<ContentImportItem> items = importItemMapper.selectList(new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId)
                .isNotNull(ContentImportItem::getPostId));
        for (ContentImportItem item : items) {
            Post post = postMapper.selectById(item.getPostId());
            if (post != null) {
                post.setVisibility("PRIVATE");
                post.setAuditStatus("REJECTED");
                postMapper.updateById(post);
            }
            item.setStatus("ROLLED_BACK");
            item.setErrorMessage("");
            importItemMapper.updateById(item);
        }
        ContentImportBatch refreshed = refreshImportBatchCounters(batchId);
        refreshed.setStatus("ROLLED_BACK");
        refreshed.setFinishedAt(LocalDateTime.now());
        importBatchMapper.updateById(refreshed);
        log(operatorId, "IMPORT_BATCH_ROLLBACK", "IMPORT_BATCH", String.valueOf(batchId), Map.of("items", items.size()));
        return toImportBatchView(importBatchMapper.selectById(batchId));
    }

    public PageResponse<RebuildTaskView> listRebuildTasks(String taskType, String status, int page, int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<ContentRebuildTask> wrapper = new LambdaQueryWrapper<>();
        if (hasText(taskType)) {
            wrapper.eq(ContentRebuildTask::getTaskType, taskType.trim().toUpperCase());
        }
        if (hasText(status)) {
            wrapper.eq(ContentRebuildTask::getStatus, status.trim().toUpperCase());
        }
        long total = rebuildTaskMapper.selectCount(wrapper);
        List<ContentRebuildTask> records = rebuildTaskMapper.selectList(wrapper
                .orderByDesc(ContentRebuildTask::getCreatedAt)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toRebuildTaskView).toList(), total, safePage, safeSize);
    }

    @Transactional
    public RebuildTaskView createRebuildTask(Long operatorId, RebuildTaskMutationRequest request) {
        String taskType = normalizeRebuildTaskType(request.taskType());
        ContentRebuildTask task = new ContentRebuildTask();
        task.setTaskType(taskType);
        task.setStatus("PENDING");
        task.setScopeType(firstNonBlank(request.scopeType(), "ALL").toUpperCase());
        task.setScopeId(firstNonBlank(request.scopeId(), ""));
        task.setBatchId(request.batchId());
        task.setPostId(request.postId());
        task.setTotalCount(0);
        task.setSuccessCount(0);
        task.setFailedCount(0);
        task.setParamsJson(toJson(request.params() == null ? Map.of() : request.params()));
        task.setErrorMessage("");
        task.setOperatorId(operatorId);
        rebuildTaskMapper.insert(task);
        log(operatorId, "REBUILD_TASK_CREATE", "REBUILD_TASK", String.valueOf(task.getId()), request);
        return toRebuildTaskView(rebuildTaskMapper.selectById(task.getId()));
    }

    @Transactional
    public RebuildTaskView updateRebuildTaskStatus(Long operatorId, Long taskId, RebuildTaskStatusRequest request) {
        ContentRebuildTask task = requireRebuildTask(taskId);
        String status = normalizeStatus(request.status(), task.getStatus());
        task.setStatus(status);
        if ("RUNNING".equals(status) && task.getStartedAt() == null) {
            task.setStartedAt(LocalDateTime.now());
        }
        if (Set.of("SUCCESS", "FAILED", "CANCELED").contains(status)) {
            task.setFinishedAt(LocalDateTime.now());
        }
        if (request.totalCount() != null) {
            task.setTotalCount(request.totalCount());
        }
        if (request.successCount() != null) {
            task.setSuccessCount(request.successCount());
        }
        if (request.failedCount() != null) {
            task.setFailedCount(request.failedCount());
        }
        if (request.errorMessage() != null) {
            task.setErrorMessage(request.errorMessage());
        }
        rebuildTaskMapper.updateById(task);
        log(operatorId, "REBUILD_TASK_STATUS", "REBUILD_TASK", String.valueOf(taskId), request);
        return toRebuildTaskView(rebuildTaskMapper.selectById(taskId));
    }

    public PageResponse<FeedRequestLogView> listFeedRequests(String surface,
                                                             String experimentId,
                                                             int page,
                                                             int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<FeedRequestLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(surface)) {
            wrapper.eq(FeedRequestLog::getSurface, surface.trim());
        }
        if (hasText(experimentId)) {
            wrapper.eq(FeedRequestLog::getExperimentId, experimentId.trim());
        }
        long total = feedRequestLogMapper.selectCount(wrapper);
        List<FeedRequestLog> records = feedRequestLogMapper.selectList(wrapper
                .orderByDesc(FeedRequestLog::getCreatedAt)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toFeedRequestLogView).toList(), total, safePage, safeSize);
    }

    public PageResponse<FeedImpressionLogView> listFeedImpressions(String requestId,
                                                                   Long postId,
                                                                   int page,
                                                                   int size) {
        int safePage = normalizePage(page);
        int safeSize = normalizeSize(size);
        LambdaQueryWrapper<FeedImpressionLog> wrapper = new LambdaQueryWrapper<>();
        if (hasText(requestId)) {
            wrapper.eq(FeedImpressionLog::getRequestId, requestId.trim());
        }
        if (postId != null && postId > 0) {
            wrapper.eq(FeedImpressionLog::getPostId, postId);
        }
        long total = feedImpressionLogMapper.selectCount(wrapper);
        List<FeedImpressionLog> records = feedImpressionLogMapper.selectList(wrapper
                .orderByDesc(FeedImpressionLog::getCreatedAt)
                .orderByAsc(FeedImpressionLog::getRankPosition)
                .last(limitOffset(safePage, safeSize)));
        return new PageResponse<>(records.stream().map(this::toFeedImpressionLogView).toList(), total, safePage, safeSize);
    }

    private void applyChannelMutation(Channel channel, ChannelMutationRequest request, String code) {
        channel.setCode(code);
        channel.setName(requireText(request.name(), "频道名称不能为空"));
        channel.setDescription(firstNonBlank(request.description(), ""));
        channel.setIcon(firstNonBlank(request.icon(), ""));
        channel.setIconUrl(firstNonBlank(request.iconUrl(), ""));
        channel.setCoverUrl(firstNonBlank(request.coverUrl(), ""));
        channel.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        channel.setEnabled(request.enabled() == null || request.enabled());
        channel.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        channel.setNavGroup(firstNonBlank(request.navGroup(), "MAIN"));
        channel.setDefaultPostType(firstNonBlank(request.defaultPostType(), "general_post"));
        channel.setWaterfallEnabled(request.waterfallEnabled() == null || request.waterfallEnabled());
        channel.setPublishEnabled(request.publishEnabled() == null || request.publishEnabled());
        channel.setRecommendEnabled(request.recommendEnabled() == null || request.recommendEnabled());
        channel.setConfigJson(firstNonBlank(request.configJson(), "{}"));
    }

    private void applyTopicMutation(Topic topic, TopicMutationRequest request, String name, String slug) {
        topic.setName(requireText(name, "话题名称不能为空"));
        topic.setSlug(requireCode(slug));
        topic.setDescription(firstNonBlank(request.description(), ""));
        topic.setCoverUrl(firstNonBlank(request.coverUrl(), ""));
        topic.setStatus(normalizeStatus(request.status(), "ACTIVE"));
        topic.setRiskLevel(firstNonBlank(request.riskLevel(), "NORMAL"));
        topic.setTopicType(firstNonBlank(request.topicType(), "GENERAL"));
        topic.setSource(firstNonBlank(request.source(), "ADMIN"));
        topic.setHotScore(request.hotScore() == null ? BigDecimal.ZERO : request.hotScore());
        if (topic.getPostCount() == null) topic.setPostCount(0);
        if (topic.getFollowerCount() == null) topic.setFollowerCount(0);
    }

    private void bindTopicChannels(Long topicId, List<String> channelCodes) {
        topicChannelBindingMapper.delete(new LambdaQueryWrapper<TopicChannelBinding>().eq(TopicChannelBinding::getTopicId, topicId));
        if (channelCodes == null) {
            return;
        }
        Set<String> uniqueCodes = new LinkedHashSet<>();
        for (String channelCode : channelCodes) {
            if (hasText(channelCode)) {
                uniqueCodes.add(channelCode.trim());
            }
        }
        for (String channelCode : uniqueCodes) {
            requireChannel(channelCode);
            TopicChannelBinding binding = new TopicChannelBinding();
            binding.setTopicId(topicId);
            binding.setChannelCode(channelCode);
            binding.setWeight(BigDecimal.ONE);
            binding.setStatus("ACTIVE");
            topicChannelBindingMapper.insert(binding);
        }
    }

    private void moveAliases(Long fromId, Long toId) {
        for (TopicAlias alias : topicAliasMapper.selectList(new LambdaQueryWrapper<TopicAlias>().eq(TopicAlias::getTopicId, fromId))) {
            alias.setTopicId(toId);
            try {
                topicAliasMapper.updateById(alias);
            } catch (DuplicateKeyException exception) {
                topicAliasMapper.deleteById(alias.getId());
            }
        }
    }

    private void moveBindings(Long fromId, Long toId) {
        for (TopicChannelBinding binding : topicChannelBindingMapper.selectList(new LambdaQueryWrapper<TopicChannelBinding>().eq(TopicChannelBinding::getTopicId, fromId))) {
            TopicChannelBinding existing = topicChannelBindingMapper.selectOne(new LambdaQueryWrapper<TopicChannelBinding>()
                    .eq(TopicChannelBinding::getTopicId, toId)
                    .eq(TopicChannelBinding::getChannelCode, binding.getChannelCode()));
            if (existing == null) {
                binding.setTopicId(toId);
                topicChannelBindingMapper.updateById(binding);
            } else {
                topicChannelBindingMapper.deleteById(binding.getId());
            }
        }
    }

    private AdminChannelView toAdminChannelView(Channel channel) {
        ChannelView view = channelService.toView(channel);
        return new AdminChannelView(
                channel.getId(),
                view.code(),
                view.name(),
                view.description(),
                view.icon(),
                channel.getCoverUrl(),
                channel.getSortOrder(),
                channel.getStatus(),
                Boolean.TRUE.equals(channel.getEnabled()),
                channel.getNavGroup(),
                channel.getDefaultPostType(),
                Boolean.TRUE.equals(channel.getWaterfallEnabled()),
                Boolean.TRUE.equals(channel.getPublishEnabled()),
                Boolean.TRUE.equals(channel.getRecommendEnabled()),
                channel.getConfigJson()
        );
    }

    private AdminTopicView toAdminTopicView(Topic topic) {
        return new AdminTopicView(
                topic.getId(),
                topic.getName(),
                topic.getSlug(),
                topic.getDescription(),
                topic.getCoverUrl(),
                topic.getStatus(),
                topic.getRiskLevel(),
                topic.getTopicType(),
                topic.getSource(),
                topic.getPostCount(),
                topic.getFollowerCount(),
                topic.getHotScore()
        );
    }

    private ContentImportBatchView toImportBatchView(ContentImportBatch batch) {
        return new ContentImportBatchView(
                batch.getId(),
                batch.getName(),
                batch.getDescription(),
                batch.getSourceType(),
                batch.getStatus(),
                batch.getTotalCount(),
                batch.getSuccessCount(),
                batch.getFailedCount(),
                batch.getOperatorId(),
                batch.getStartedAt(),
                batch.getFinishedAt(),
                batch.getCreatedAt()
        );
    }

    private ContentImportItemView toImportItemView(ContentImportItem item) {
        return new ContentImportItemView(
                item.getId(),
                item.getBatchId(),
                item.getPostId(),
                item.getTitle(),
                item.getContent(),
                item.getChannelCode(),
                splitCsv(item.getTopicNames()),
                splitCsv(item.getImageUrls()),
                item.getStatus(),
                item.getErrorMessage(),
                item.getCreatedAt()
        );
    }

    private RebuildTaskView toRebuildTaskView(ContentRebuildTask task) {
        return new RebuildTaskView(
                task.getId(),
                task.getTaskType(),
                task.getStatus(),
                task.getScopeType(),
                task.getScopeId(),
                task.getBatchId(),
                task.getPostId(),
                task.getTotalCount(),
                task.getSuccessCount(),
                task.getFailedCount(),
                task.getParamsJson(),
                task.getErrorMessage(),
                task.getOperatorId(),
                task.getStartedAt(),
                task.getFinishedAt(),
                task.getCreatedAt()
        );
    }

    private FeedRequestLogView toFeedRequestLogView(FeedRequestLog log) {
        return new FeedRequestLogView(
                log.getId(),
                log.getRequestId(),
                log.getUserId(),
                log.getSurface(),
                log.getPageNo(),
                log.getPageSize(),
                log.getSeed(),
                log.getFiltersJson(),
                log.getUserSegment(),
                log.getExperimentId(),
                log.getExperimentBucket(),
                log.getTotalCandidates(),
                log.getReturnedCount(),
                log.getLatencyMs(),
                log.getDegraded(),
                log.getCreatedAt()
        );
    }

    private FeedImpressionLogView toFeedImpressionLogView(FeedImpressionLog log) {
        return new FeedImpressionLogView(
                log.getId(),
                log.getRequestId(),
                log.getUserId(),
                log.getPostId(),
                log.getRankPosition(),
                log.getRecallSource(),
                log.getRankScore(),
                log.getChannelCode(),
                log.getTopicNames(),
                log.getReason(),
                log.getCreatedAt()
        );
    }

    private ContentImportBatch refreshImportBatchCounters(Long batchId) {
        ContentImportBatch batch = requireBatch(batchId);
        batch.setTotalCount(Math.toIntExact(importItemMapper.selectCount(new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId))));
        batch.setSuccessCount(Math.toIntExact(importItemMapper.selectCount(new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId)
                .eq(ContentImportItem::getStatus, "PUBLISHED"))));
        batch.setFailedCount(Math.toIntExact(importItemMapper.selectCount(new LambdaQueryWrapper<ContentImportItem>()
                .eq(ContentImportItem::getBatchId, batchId)
                .eq(ContentImportItem::getStatus, "FAILED"))));
        importBatchMapper.updateById(batch);
        return batch;
    }

    private List<AdminPostView> toAdminPostViews(List<Post> posts, List<PostView> views) {
        List<AdminPostView> result = new ArrayList<>();
        for (int index = 0; index < posts.size(); index++) {
            Post post = posts.get(index);
            result.add(new AdminPostView(
                    views.get(index),
                    post.getAuditStatus(),
                    post.getVisibility(),
                    post.getQualityScore(),
                    post.getSafetyScore(),
                    post.getHotScore()
            ));
        }
        return result;
    }

    private Channel requireChannel(String code) {
        Channel channel = channelMapper.selectByCode(requireCode(code));
        if (channel == null) {
            throw new BusinessException("频道不存在");
        }
        return channel;
    }

    private Topic requireTopic(Long id) {
        Topic topic = topicMapper.selectById(id);
        if (topic == null) {
            throw new BusinessException("话题不存在");
        }
        return topic;
    }

    private ContentImportBatch requireBatch(Long id) {
        ContentImportBatch batch = importBatchMapper.selectById(id);
        if (batch == null) {
            throw new BusinessException("导入批次不存在");
        }
        return batch;
    }

    private ContentRebuildTask requireRebuildTask(Long id) {
        ContentRebuildTask task = rebuildTaskMapper.selectById(id);
        if (task == null) {
            throw new BusinessException("重建任务不存在");
        }
        return task;
    }

    private void log(Long operatorId, String action, String targetType, String targetId, Object detail) {
        AdminOperationLog log = new AdminOperationLog();
        log.setOperatorId(operatorId);
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetailJson(toJson(detail));
        operationLogMapper.insert(log);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private String limitOffset(int page, int size) {
        return "LIMIT " + size + " OFFSET " + ((page - 1) * size);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String requireCode(String value) {
        String code = requireText(value, "编码不能为空").trim().toLowerCase().replace("-", "_");
        if (!code.matches("[a-z0-9_]{2,64}")) {
            throw new BusinessException("编码只允许小写字母、数字和下划线");
        }
        return code;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (hasText(value)) return value.trim();
        }
        return null;
    }

    private String normalizeStatus(String status, String fallback) {
        return hasText(status) ? status.trim().toUpperCase() : fallback;
    }

    private String normalizeRebuildTaskType(String taskType) {
        String normalized = normalizeStatus(taskType, "ALL");
        if (!ALLOWED_REBUILD_TYPES.contains(normalized)) {
            throw new BusinessException("不支持的重建任务类型");
        }
        return normalized;
    }

    private String normalizeAlias(String alias) {
        return alias.trim().toLowerCase();
    }

    private String generateSlug(String name) {
        return "topic-" + Integer.toHexString(name.hashCode());
    }

    private String joinCsv(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().filter(this::hasText).map(String::trim).distinct().reduce((a, b) -> a + "," + b).orElse("");
    }

    private List<String> splitCsv(String raw) {
        if (!hasText(raw)) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : raw.split(",")) {
            if (hasText(item)) result.add(item.trim());
        }
        return result;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    public record AdminOverview(long activeChannels,
                                long activeTopics,
                                long approvedPosts,
                                long pendingPosts,
                                long rejectedPosts,
                                long importBatches) {
    }

    public record AdminChannelView(Long id,
                                   String code,
                                   String name,
                                   String description,
                                   String icon,
                                   String coverUrl,
                                   Integer sortOrder,
                                   String status,
                                   Boolean enabled,
                                   String navGroup,
                                   String defaultPostType,
                                   Boolean waterfallEnabled,
                                   Boolean publishEnabled,
                                   Boolean recommendEnabled,
                                   String configJson) {
    }

    public record ChannelMutationRequest(String code,
                                         String name,
                                         String description,
                                         String icon,
                                         String iconUrl,
                                         String coverUrl,
                                         Integer sortOrder,
                                         String status,
                                         Boolean enabled,
                                         String navGroup,
                                         String defaultPostType,
                                         Boolean waterfallEnabled,
                                         Boolean publishEnabled,
                                         Boolean recommendEnabled,
                                         String configJson) {
    }

    public record StatusMutationRequest(String status, Boolean enabled) {
    }

    public record ReorderRequest(List<ReorderItem> items) {
    }

    public record ReorderItem(String code, Integer sortOrder) {
    }

    public record AdminTopicView(Long id,
                                 String name,
                                 String slug,
                                 String description,
                                 String coverUrl,
                                 String status,
                                 String riskLevel,
                                 String topicType,
                                 String source,
                                 Integer postCount,
                                 Integer followerCount,
                                 BigDecimal hotScore) {
    }

    public record AdminTopicDetail(AdminTopicView topic,
                                   List<TopicAliasView> aliases,
                                   List<TopicBindingView> bindings) {
    }

    public record TopicMutationRequest(String name,
                                       String slug,
                                       String description,
                                       String coverUrl,
                                       String status,
                                       String riskLevel,
                                       String topicType,
                                       String source,
                                       BigDecimal hotScore,
                                       List<String> channelCodes) {
    }

    public record TopicAliasView(Long id, String alias, String normalizedAlias, String source) {
    }

    public record AliasMutationRequest(String alias, String source) {
    }

    public record TopicBindingView(String channelCode, BigDecimal weight, String status) {
    }

    public record BindingMutationRequest(String channelCode, BigDecimal weight, String status) {
    }

    public record TopicMergeRequest(Long fromTopicId, Long toTopicId, String reason) {
    }

    public record PostModerationRequest(String auditStatus,
                                        String visibility,
                                        BigDecimal qualityScore,
                                        BigDecimal safetyScore) {
    }

    public record AdminPostView(PostView post,
                                String auditStatus,
                                String visibility,
                                BigDecimal qualityScore,
                                BigDecimal safetyScore,
                                BigDecimal hotScore) {
    }

    public record ContentImportBatchView(Long id,
                                         String name,
                                         String description,
                                         String sourceType,
                                         String status,
                                         Integer totalCount,
                                         Integer successCount,
                                         Integer failedCount,
                                         Long operatorId,
                                         LocalDateTime startedAt,
                                         LocalDateTime finishedAt,
                                         LocalDateTime createdAt) {
    }

    public record ImportBatchMutationRequest(String name, String description, String sourceType) {
    }

    public record ContentImportItemView(Long id,
                                        Long batchId,
                                        Long postId,
                                        String title,
                                        String content,
                                        String channelCode,
                                        List<String> topics,
                                        List<String> imageUrls,
                                        String status,
                                        String errorMessage,
                                        LocalDateTime createdAt) {
    }

    public record ImportItemMutationRequest(String title,
                                            String content,
                                            String channelCode,
                                            List<String> topics,
                                            List<String> imageUrls) {
    }

    public record RebuildTaskView(Long id,
                                  String taskType,
                                  String status,
                                  String scopeType,
                                  String scopeId,
                                  Long batchId,
                                  Long postId,
                                  Integer totalCount,
                                  Integer successCount,
                                  Integer failedCount,
                                  String paramsJson,
                                  String errorMessage,
                                  Long operatorId,
                                  LocalDateTime startedAt,
                                  LocalDateTime finishedAt,
                                  LocalDateTime createdAt) {
    }

    public record RebuildTaskMutationRequest(String taskType,
                                             String scopeType,
                                             String scopeId,
                                             Long batchId,
                                             Long postId,
                                             Map<String, Object> params) {
    }

    public record RebuildTaskStatusRequest(String status,
                                           Integer totalCount,
                                           Integer successCount,
                                           Integer failedCount,
                                           String errorMessage) {
    }

    public record FeedRequestLogView(Long id,
                                     String requestId,
                                     Long userId,
                                     String surface,
                                     Integer pageNo,
                                     Integer pageSize,
                                     String seed,
                                     String filtersJson,
                                     String userSegment,
                                     String experimentId,
                                     String experimentBucket,
                                     Integer totalCandidates,
                                     Integer returnedCount,
                                     Long latencyMs,
                                     Boolean degraded,
                                     LocalDateTime createdAt) {
    }

    public record FeedImpressionLogView(Long id,
                                        String requestId,
                                        Long userId,
                                        Long postId,
                                        Integer rankPosition,
                                        String recallSource,
                                        BigDecimal rankScore,
                                        String channelCode,
                                        String topicNames,
                                        String reason,
                                        LocalDateTime createdAt) {
    }
}
