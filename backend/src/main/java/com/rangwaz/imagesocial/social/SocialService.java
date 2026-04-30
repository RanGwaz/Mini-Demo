package com.rangwaz.imagesocial.social;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.UserBlock;
import com.rangwaz.imagesocial.domain.entity.UserFollow;
import com.rangwaz.imagesocial.domain.mapper.UserBlockMapper;
import com.rangwaz.imagesocial.domain.mapper.UserFollowMapper;
import com.rangwaz.imagesocial.event.EventService;
import com.rangwaz.imagesocial.social.dto.FollowStatusResponse;
import com.rangwaz.imagesocial.user.UserService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SocialService {

    private final UserFollowMapper userFollowMapper;
    private final UserBlockMapper userBlockMapper;
    private final UserService userService;
    private final EventService eventService;

    public SocialService(UserFollowMapper userFollowMapper,
                         UserBlockMapper userBlockMapper,
                         UserService userService,
                         EventService eventService) {
        this.userFollowMapper = userFollowMapper;
        this.userBlockMapper = userBlockMapper;
        this.userService = userService;
        this.eventService = eventService;
    }

    @Transactional
    public void follow(Long userId, Long followedId, String scene) {
        if (userId.equals(followedId)) {
            throw new BusinessException("不能关注自己");
        }
        userService.requireById(followedId);
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFollowedId, followedId));
        if (count > 0) {
            return;
        }
        UserFollow relation = new UserFollow();
        relation.setFollowerId(userId);
        relation.setFollowedId(followedId);
        userFollowMapper.insert(relation);
        eventService.publish("USER_FOLLOW", userId, "USER", followedId, Map.of(
                "scene", safeScene(scene)
        ));
    }

    @Transactional
    public void unfollow(Long userId, Long followedId, String scene) {
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFollowedId, followedId));
        eventService.publish("USER_UNFOLLOW", userId, "USER", followedId, Map.of(
                "scene", safeScene(scene)
        ));
    }

    public List<UserSummary> getFollowing(Long userId) {
        return userFollowMapper.selectFollowing(userId).stream()
                .map(UserFollow::getFollowedId)
                .map(userService::summaryOrPlaceholder)
                .toList();
    }

    public PageResponse<UserSummary> getFollowingPaged(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        List<UserSummary> records = userFollowMapper.selectFollowingPaged(userId, offset, safeSize).stream()
                .map(UserFollow::getFollowedId)
                .map(userService::summaryOrPlaceholder)
                .toList();
        long total = countFollowing(userId);
        return new PageResponse<>(records, total, safePage, safeSize);
    }

    public List<UserSummary> getFollowers(Long userId) {
        return userFollowMapper.selectFollowers(userId).stream()
                .map(UserFollow::getFollowerId)
                .map(userService::summaryOrPlaceholder)
                .toList();
    }

    public PageResponse<UserSummary> getFollowersPaged(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int offset = (safePage - 1) * safeSize;
        List<UserSummary> records = userFollowMapper.selectFollowersPaged(userId, offset, safeSize).stream()
                .map(UserFollow::getFollowerId)
                .map(userService::summaryOrPlaceholder)
                .toList();
        long total = countFollowers(userId);
        return new PageResponse<>(records, total, safePage, safeSize);
    }

    public long countFollowing(Long userId) {
        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId));
    }

    public long countFollowers(Long userId) {
        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowedId, userId));
    }

    public FollowStatusResponse followStatus(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || currentUserId.equals(targetUserId)) {
            return new FollowStatusResponse(false);
        }
        Long count = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, currentUserId)
                .eq(UserFollow::getFollowedId, targetUserId));
        return new FollowStatusResponse(count > 0);
    }

    @Transactional
    public void block(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BusinessException("不能拉黑自己");
        }
        userService.requireById(blockedUserId);
        Long count = userBlockMapper.selectCount(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId)
                .eq(UserBlock::getBlockedUserId, blockedUserId));
        if (count > 0) {
            return;
        }
        UserBlock block = new UserBlock();
        block.setUserId(userId);
        block.setBlockedUserId(blockedUserId);
        userBlockMapper.insert(block);
        eventService.publish("USER_BLOCK", userId, "USER", blockedUserId, Map.of());
    }

    @Transactional
    public void unblock(Long userId, Long blockedUserId) {
        userBlockMapper.delete(new LambdaQueryWrapper<UserBlock>()
                .eq(UserBlock::getUserId, userId)
                .eq(UserBlock::getBlockedUserId, blockedUserId));
        eventService.publish("USER_UNBLOCK", userId, "USER", blockedUserId, Map.of());
    }

    public List<Long> blockedUserIds(Long userId) {
        return userBlockMapper.selectList(new LambdaQueryWrapper<UserBlock>()
                        .eq(UserBlock::getUserId, userId))
                .stream()
                .map(UserBlock::getBlockedUserId)
                .toList();
    }

    private String safeScene(String scene) {
        return scene == null || scene.isBlank() ? "unknown" : scene;
    }
}
