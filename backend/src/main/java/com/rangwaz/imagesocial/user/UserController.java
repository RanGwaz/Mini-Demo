package com.rangwaz.imagesocial.user;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.social.SocialService;
import com.rangwaz.imagesocial.user.dto.UpdateProfileRequest;
import com.rangwaz.imagesocial.user.dto.UpdateUserInterestsRequest;
import com.rangwaz.imagesocial.user.dto.UserInterestsResponse;
import com.rangwaz.imagesocial.user.dto.UserStats;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PostService postService;
    private final SocialService socialService;
    private final UserInterestService userInterestService;

    public UserController(UserService userService,
                          PostService postService,
                          SocialService socialService,
                          UserInterestService userInterestService) {
        this.userService = userService;
        this.postService = postService;
        this.socialService = socialService;
        this.userInterestService = userInterestService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserSummary> detail(@PathVariable Long userId) {
        return ApiResponse.success(userService.toSummary(userService.requireById(userId)));
    }

    @PutMapping("/me")
    public ApiResponse<UserSummary> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userService.updateProfile(SecurityUtils.currentUserIdOrThrow(), request), "更新成功");
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<List<PostView>> posts(@PathVariable Long userId,
                                             @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.success(postService.listByAuthor(userId, limit));
    }

    @GetMapping("/{userId}/stats")
    public ApiResponse<UserStats> stats(@PathVariable Long userId) {
        userService.requireById(userId);
        return ApiResponse.success(new UserStats(
                postService.countByAuthorId(userId),
                socialService.countFollowing(userId),
                socialService.countFollowers(userId)
        ));
    }

    @GetMapping("/me/interests")
    public ApiResponse<UserInterestsResponse> myInterests() {
        Long currentUserId = SecurityUtils.currentUserIdOrThrow();
        return ApiResponse.success(userInterestService.getUserInterests(currentUserId));
    }

    @PutMapping("/me/interests")
    public ApiResponse<UserInterestsResponse> replaceMyInterests(@Valid @RequestBody UpdateUserInterestsRequest request) {
        Long currentUserId = SecurityUtils.currentUserIdOrThrow();
        return ApiResponse.success(
                userInterestService.replaceUserInterests(currentUserId, request.facets()),
                "兴趣订阅已更新"
        );
    }
}
