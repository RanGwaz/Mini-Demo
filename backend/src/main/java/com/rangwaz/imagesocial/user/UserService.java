package com.rangwaz.imagesocial.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.ErrorCode;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.User;
import com.rangwaz.imagesocial.domain.mapper.UserMapper;
import com.rangwaz.imagesocial.user.dto.UpdateProfileRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User requireById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public User requireByUsername(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public boolean existsByUsername(String username) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0;
    }

    public boolean existsByUserNo(String userNo) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUserNo, userNo)) > 0;
    }

    public User findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, "phone_" + phone));
    }

    public boolean existsByPhone(String phone, Long excludeUserId) {
        User existing = findByPhone(phone);
        return existing != null && !existing.getId().equals(excludeUserId);
    }

    public void save(User user) {
        userMapper.insert(user);
    }

    public void update(User user) {
        userMapper.updateById(user);
    }

    public UserSummary updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireById(userId);
        if (request.nickname() != null && !request.nickname().isBlank()) {
            user.setNickname(request.nickname());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl());
        }
        if (request.backgroundUrl() != null) {
            user.setBackgroundUrl(request.backgroundUrl());
        }
        if (request.bio() != null) {
            user.setBio(request.bio());
        }
        userMapper.updateById(user);
        return toSummary(user);
    }

    public UserSummary toSummary(User user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getUserNo(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBackgroundUrl(),
                user.getBio());
    }

    public UserSummary summaryOrPlaceholder(Long userId) {
        if (userId == null) {
            return new UserSummary(
                    -1L,
                    "unknown",
                    null,
                    "账号已注销",
                    null,
                    null,
                    null
            );
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            return new UserSummary(
                    userId,
                    "user_" + userId,
                    null,
                    "账号已注销",
                    null,
                    null,
                    null
            );
        }
        return toSummary(user);
    }

    public List<UserSummary> listByIds(List<Long> userIds) {
        return userMapper.selectBatchIds(userIds).stream().map(this::toSummary).toList();
    }

    public Map<Long, UserSummary> summaryMapByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(userIds).stream()
                .map(this::toSummary)
                .collect(Collectors.toMap(UserSummary::id, item -> item, (left, right) -> left, LinkedHashMap::new));
    }

    public List<UserSummary> searchUsers(String keyword, int limit) {
        return userMapper.selectList(new LambdaQueryWrapper<User>()
                        .and(wrapper -> wrapper.like(User::getUsername, keyword).or().like(User::getNickname, keyword))
                        .last("limit " + limit))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public PageResponse<UserSummary> searchUsersPage(String keyword, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .and(w -> w.like(User::getUsername, keyword).or().like(User::getNickname, keyword));
        long total = userMapper.selectCount(wrapper);
        int offset = (safePage - 1) * safeSize;
        List<User> users = userMapper.selectList(wrapper.last("LIMIT " + safeSize + " OFFSET " + offset));
        List<UserSummary> records = users.stream().map(this::toSummary).toList();
        return new PageResponse<>(records, total, safePage, safeSize);
    }
}
