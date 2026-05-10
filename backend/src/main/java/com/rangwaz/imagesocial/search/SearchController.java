package com.rangwaz.imagesocial.search;

import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.post.dto.PostView;
import com.rangwaz.imagesocial.auth.dto.UserSummary;
import com.rangwaz.imagesocial.channel.dto.ChannelView;
import com.rangwaz.imagesocial.topic.dto.TopicView;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchService.SearchResult> search(@RequestParam @NotBlank String keyword) {
        return ApiResponse.success(searchService.search(keyword));
    }

    @GetMapping("/posts")
    public ApiResponse<List<PostView>> searchPosts(@RequestParam @NotBlank String keyword) {
        return ApiResponse.success(searchService.searchPosts(keyword));
    }

    @GetMapping("/users")
    public ApiResponse<List<UserSummary>> searchUsers(@RequestParam @NotBlank String keyword) {
        return ApiResponse.success(searchService.searchUsers(keyword));
    }

    @GetMapping("/topics")
    public ApiResponse<List<TopicView>> searchTopics(@RequestParam @NotBlank String keyword) {
        return ApiResponse.success(searchService.searchTopics(keyword));
    }

    @GetMapping("/channels")
    public ApiResponse<List<ChannelView>> searchChannels(@RequestParam @NotBlank String keyword) {
        return ApiResponse.success(searchService.searchChannels(keyword));
    }

    @GetMapping("/posts/page")
    public ApiResponse<PageResponse<PostView>> searchPostsPage(@RequestParam @NotBlank String keyword,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(searchService.searchPostsPage(keyword, page, size));
    }

    @GetMapping("/users/page")
    public ApiResponse<PageResponse<UserSummary>> searchUsersPage(@RequestParam @NotBlank String keyword,
                                                                  @RequestParam(defaultValue = "1") int page,
                                                                  @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.success(searchService.searchUsersPage(keyword, page, size));
    }
}
