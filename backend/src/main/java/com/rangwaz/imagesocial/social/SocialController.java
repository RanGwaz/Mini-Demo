package com.rangwaz.imagesocial.social;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.social.dto.FollowStatusResponse;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/social")
public class SocialController {

    private final SocialService socialService;

    public SocialController(SocialService socialService) {
        this.socialService = socialService;
    }

    @PostMapping("/follow/{followedId}")
    public ApiResponse<Void> follow(@PathVariable Long followedId,
                                    @RequestParam(defaultValue = "unknown") String scene) {
        socialService.follow(SecurityUtils.currentUserIdOrThrow(), followedId, scene);
        return ApiResponse.success(null, "关注成功");
    }

    @DeleteMapping("/follow/{followedId}")
    public ApiResponse<Void> unfollow(@PathVariable Long followedId,
                                      @RequestParam(defaultValue = "unknown") String scene) {
        socialService.unfollow(SecurityUtils.currentUserIdOrThrow(), followedId, scene);
        return ApiResponse.success(null, "取消关注成功");
    }

    @GetMapping("/following/{userId}")
    public ApiResponse<List<UserSummary>> following(@PathVariable Long userId) {
        return ApiResponse.success(socialService.getFollowing(userId));
    }

    @GetMapping("/followers/{userId}")
    public ApiResponse<List<UserSummary>> followers(@PathVariable Long userId) {
        return ApiResponse.success(socialService.getFollowers(userId));
    }

    @GetMapping("/following/{userId}/page")
    public ApiResponse<PageResponse<UserSummary>> followingPaged(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(socialService.getFollowingPaged(userId, page, size));
    }

    @GetMapping("/followers/{userId}/page")
    public ApiResponse<PageResponse<UserSummary>> followersPaged(@PathVariable Long userId,
                                                                 @RequestParam(defaultValue = "1") int page,
                                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(socialService.getFollowersPaged(userId, page, size));
    }

    @GetMapping("/follow-status/{targetUserId}")
    public ApiResponse<FollowStatusResponse> followStatus(@PathVariable Long targetUserId) {
        return ApiResponse.success(socialService.followStatus(SecurityUtils.currentUserIdOrNull(), targetUserId));
    }
}
