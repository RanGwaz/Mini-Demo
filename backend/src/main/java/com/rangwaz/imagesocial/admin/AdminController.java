package com.rangwaz.imagesocial.admin;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminService.AdminOverview> overview() {
        return ApiResponse.success(adminService.overview());
    }

    @GetMapping("/channels")
    public ApiResponse<PageResponse<AdminService.AdminChannelView>> channels(@RequestParam(required = false) String keyword,
                                                                             @RequestParam(required = false) String status,
                                                                             @RequestParam(defaultValue = "1") int page,
                                                                             @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminService.listChannels(keyword, status, page, size));
    }

    @PostMapping("/channels")
    public ApiResponse<AdminService.AdminChannelView> createChannel(@RequestBody AdminService.ChannelMutationRequest request) {
        return ApiResponse.success(adminService.createChannel(currentUserId(), request));
    }

    @PutMapping("/channels/{code}")
    public ApiResponse<AdminService.AdminChannelView> updateChannel(@PathVariable String code,
                                                                    @RequestBody AdminService.ChannelMutationRequest request) {
        return ApiResponse.success(adminService.updateChannel(currentUserId(), code, request));
    }

    @PatchMapping("/channels/{code}/status")
    public ApiResponse<Void> updateChannelStatus(@PathVariable String code,
                                                 @RequestBody AdminService.StatusMutationRequest request) {
        adminService.updateChannelStatus(currentUserId(), code, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/channels/reorder")
    public ApiResponse<Void> reorderChannels(@RequestBody AdminService.ReorderRequest request) {
        adminService.reorderChannels(currentUserId(), request);
        return ApiResponse.success(null);
    }

    @GetMapping("/topics")
    public ApiResponse<PageResponse<AdminService.AdminTopicView>> topics(@RequestParam(required = false) String keyword,
                                                                         @RequestParam(required = false) String status,
                                                                         @RequestParam(required = false) String channelCode,
                                                                         @RequestParam(defaultValue = "1") int page,
                                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminService.listTopics(keyword, status, channelCode, page, size));
    }

    @GetMapping("/topics/{id}")
    public ApiResponse<AdminService.AdminTopicDetail> topicDetail(@PathVariable Long id) {
        return ApiResponse.success(adminService.topicDetail(id));
    }

    @PostMapping("/topics")
    public ApiResponse<AdminService.AdminTopicView> createTopic(@RequestBody AdminService.TopicMutationRequest request) {
        return ApiResponse.success(adminService.createTopic(currentUserId(), request));
    }

    @PutMapping("/topics/{id}")
    public ApiResponse<AdminService.AdminTopicView> updateTopic(@PathVariable Long id,
                                                                @RequestBody AdminService.TopicMutationRequest request) {
        return ApiResponse.success(adminService.updateTopic(currentUserId(), id, request));
    }

    @PatchMapping("/topics/{id}/status")
    public ApiResponse<Void> updateTopicStatus(@PathVariable Long id,
                                               @RequestBody AdminService.StatusMutationRequest request) {
        adminService.updateTopicStatus(currentUserId(), id, request);
        return ApiResponse.success(null);
    }

    @PostMapping("/topics/{id}/aliases")
    public ApiResponse<AdminService.TopicAliasView> addTopicAlias(@PathVariable Long id,
                                                                  @RequestBody AdminService.AliasMutationRequest request) {
        return ApiResponse.success(adminService.addTopicAlias(currentUserId(), id, request));
    }

    @DeleteMapping("/topic-aliases/{aliasId}")
    public ApiResponse<Void> deleteTopicAlias(@PathVariable Long aliasId) {
        adminService.deleteTopicAlias(currentUserId(), aliasId);
        return ApiResponse.success(null);
    }

    @PutMapping("/topics/{id}/bindings")
    public ApiResponse<AdminService.TopicBindingView> upsertTopicBinding(@PathVariable Long id,
                                                                         @RequestBody AdminService.BindingMutationRequest request) {
        return ApiResponse.success(adminService.upsertTopicBinding(currentUserId(), id, request));
    }

    @DeleteMapping("/topics/{id}/bindings/{channelCode}")
    public ApiResponse<Void> deleteTopicBinding(@PathVariable Long id,
                                                @PathVariable String channelCode) {
        adminService.deleteTopicBinding(currentUserId(), id, channelCode);
        return ApiResponse.success(null);
    }

    @PostMapping("/topics/merge")
    public ApiResponse<Void> mergeTopics(@RequestBody AdminService.TopicMergeRequest request) {
        adminService.mergeTopics(currentUserId(), request);
        return ApiResponse.success(null);
    }

    @GetMapping("/posts")
    public ApiResponse<PageResponse<AdminService.AdminPostView>> posts(@RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) String channelCode,
                                                                       @RequestParam(required = false) String auditStatus,
                                                                       @RequestParam(required = false) String visibility,
                                                                       @RequestParam(defaultValue = "1") int page,
                                                                       @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(adminService.listPosts(keyword, channelCode, auditStatus, visibility, page, size));
    }

    @PatchMapping("/posts/{id}/moderation")
    public ApiResponse<AdminService.AdminPostView> moderatePost(@PathVariable Long id,
                                                                @RequestBody AdminService.PostModerationRequest request) {
        return ApiResponse.success(adminService.moderatePost(currentUserId(), id, request));
    }

    @GetMapping("/import-batches")
    public ApiResponse<PageResponse<AdminService.ContentImportBatchView>> importBatches(@RequestParam(required = false) String status,
                                                                                        @RequestParam(defaultValue = "1") int page,
                                                                                        @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(adminService.listImportBatches(status, page, size));
    }

    @PostMapping("/import-batches")
    public ApiResponse<AdminService.ContentImportBatchView> createImportBatch(@RequestBody AdminService.ImportBatchMutationRequest request) {
        return ApiResponse.success(adminService.createImportBatch(currentUserId(), request));
    }

    @PatchMapping("/import-batches/{id}/status")
    public ApiResponse<AdminService.ContentImportBatchView> updateImportBatchStatus(@PathVariable Long id,
                                                                                    @RequestBody AdminService.StatusMutationRequest request) {
        return ApiResponse.success(adminService.updateImportBatchStatus(currentUserId(), id, request));
    }

    @PostMapping("/import-batches/{id}/publish")
    public ApiResponse<AdminService.ContentImportBatchView> publishImportBatch(@PathVariable Long id) {
        return ApiResponse.success(adminService.publishImportBatch(currentUserId(), id));
    }

    @PostMapping("/import-batches/{id}/rollback")
    public ApiResponse<AdminService.ContentImportBatchView> rollbackImportBatch(@PathVariable Long id) {
        return ApiResponse.success(adminService.rollbackImportBatch(currentUserId(), id));
    }

    @GetMapping("/import-batches/{id}/items")
    public ApiResponse<PageResponse<AdminService.ContentImportItemView>> importItems(@PathVariable Long id,
                                                                                     @RequestParam(defaultValue = "1") int page,
                                                                                     @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminService.listImportItems(id, page, size));
    }

    @PostMapping("/import-batches/{id}/items")
    public ApiResponse<AdminService.ContentImportItemView> createImportItem(@PathVariable Long id,
                                                                            @RequestBody AdminService.ImportItemMutationRequest request) {
        return ApiResponse.success(adminService.createImportItem(currentUserId(), id, request));
    }

    @PostMapping("/import-items/{id}/publish")
    public ApiResponse<AdminService.ContentImportItemView> publishImportItem(@PathVariable Long id) {
        return ApiResponse.success(adminService.publishImportItem(currentUserId(), id));
    }

    @GetMapping("/rebuild-tasks")
    public ApiResponse<PageResponse<AdminService.RebuildTaskView>> rebuildTasks(@RequestParam(required = false) String taskType,
                                                                                @RequestParam(required = false) String status,
                                                                                @RequestParam(defaultValue = "1") int page,
                                                                                @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(adminService.listRebuildTasks(taskType, status, page, size));
    }

    @PostMapping("/rebuild-tasks")
    public ApiResponse<AdminService.RebuildTaskView> createRebuildTask(@RequestBody AdminService.RebuildTaskMutationRequest request) {
        return ApiResponse.success(adminService.createRebuildTask(currentUserId(), request));
    }

    @PatchMapping("/rebuild-tasks/{id}/status")
    public ApiResponse<AdminService.RebuildTaskView> updateRebuildTaskStatus(@PathVariable Long id,
                                                                             @RequestBody AdminService.RebuildTaskStatusRequest request) {
        return ApiResponse.success(adminService.updateRebuildTaskStatus(currentUserId(), id, request));
    }

    @GetMapping("/feed-requests")
    public ApiResponse<PageResponse<AdminService.FeedRequestLogView>> feedRequests(@RequestParam(required = false) String surface,
                                                                                   @RequestParam(required = false) String experimentId,
                                                                                   @RequestParam(defaultValue = "1") int page,
                                                                                   @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(adminService.listFeedRequests(surface, experimentId, page, size));
    }

    @GetMapping("/feed-impressions")
    public ApiResponse<PageResponse<AdminService.FeedImpressionLogView>> feedImpressions(@RequestParam(required = false) String requestId,
                                                                                         @RequestParam(required = false) Long postId,
                                                                                         @RequestParam(defaultValue = "1") int page,
                                                                                         @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.success(adminService.listFeedImpressions(requestId, postId, page, size));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserIdOrThrow();
    }
}
