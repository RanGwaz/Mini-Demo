package com.rangwaz.imagesocial.search;

import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.post.PostService;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.user.UserService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    private final UserService userService;
    private final PostService postService;

    public SearchService(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    public SearchResult search(String keyword) {
        return new SearchResult(
                userService.searchUsers(keyword, 10),
                postService.search(keyword, 20));
    }

    public java.util.List<PostView> searchPosts(String keyword) {
        return postService.search(keyword, 30);
    }

    public java.util.List<UserSummary> searchUsers(String keyword) {
        return userService.searchUsers(keyword, 20);
    }

    public PageResponse<PostView> searchPostsPage(String keyword, int page, int size) {
        return postService.searchPostsPage(keyword, page, size);
    }

    public PageResponse<UserSummary> searchUsersPage(String keyword, int page, int size) {
        return userService.searchUsersPage(keyword, page, size);
    }

    public record SearchResult(
            List<UserSummary> users,
            List<PostView> posts
    ) {
    }
}
