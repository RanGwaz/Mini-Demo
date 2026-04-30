package com.rangwaz.imagesocial.feed;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.domain.entity.PostNegativeFeedback;
import com.rangwaz.imagesocial.domain.entity.UserBlock;
import com.rangwaz.imagesocial.domain.mapper.PostNegativeFeedbackMapper;
import com.rangwaz.imagesocial.domain.mapper.UserBlockMapper;
import com.rangwaz.imagesocial.feature.FeatureService;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Feed 安全过滤层：从已融合的候选集中剔除用户不想看到的内容。
 * 目前过滤两类：拉黑作者的帖子、主动标记"不感兴趣"的帖子。
 */
@Service
public class FeedFilterService {

    private final UserBlockMapper userBlockMapper;
    private final PostNegativeFeedbackMapper postNegativeFeedbackMapper;
    private final FeatureService featureService;

    public FeedFilterService(UserBlockMapper userBlockMapper,
                             PostNegativeFeedbackMapper postNegativeFeedbackMapper,
                             FeatureService featureService) {
        this.userBlockMapper = userBlockMapper;
        this.postNegativeFeedbackMapper = postNegativeFeedbackMapper;
        this.featureService = featureService;
    }

    /**
     * 原地修改 merged，移除不合规帖子。
     * 未登录用户（userId == null）跳过过滤。
     */
    public void applySafetyFilters(Map<Long, RankedPost> merged, Long userId) {
        if (userId == null) {
            return;
        }
        Set<Long> blockedAuthorIds = userBlockMapper
                .selectList(new LambdaQueryWrapper<UserBlock>().eq(UserBlock::getUserId, userId))
                .stream().map(UserBlock::getBlockedUserId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        Set<Long> negativeFeedbackPostIds = postNegativeFeedbackMapper
                .selectList(new LambdaQueryWrapper<PostNegativeFeedback>()
                        .eq(PostNegativeFeedback::getUserId, userId)
                        .orderByDesc(PostNegativeFeedback::getCreatedAt)
                        .last("limit 200"))
                .stream().map(PostNegativeFeedback::getPostId)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        negativeFeedbackPostIds.addAll(featureService.getUserNegativeSignals(userId));

        merged.entrySet().removeIf(entry ->
                blockedAuthorIds.contains(entry.getValue().post().getAuthorId())
                        || negativeFeedbackPostIds.contains(entry.getKey()));
    }
}
