package com.rangwaz.imagesocial.admin;

import com.rangwaz.imagesocial.auth.SecurityUtils;
import com.rangwaz.imagesocial.common.api.ApiResponse;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.domain.entity.AccountModerationAction;
import com.rangwaz.imagesocial.domain.entity.CommercialContentProfile;
import com.rangwaz.imagesocial.domain.entity.ContentModerationCase;
import com.rangwaz.imagesocial.domain.entity.CreatorProfile;
import com.rangwaz.imagesocial.domain.entity.ModelVersion;
import com.rangwaz.imagesocial.domain.entity.OfflineEvalReport;
import com.rangwaz.imagesocial.domain.entity.TrainingDataset;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/enterprise")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEnterpriseController {

    private final AdminEnterpriseService enterpriseService;

    public AdminEnterpriseController(AdminEnterpriseService enterpriseService) {
        this.enterpriseService = enterpriseService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminEnterpriseService.EnterpriseOverview> overview() {
        return ApiResponse.success(enterpriseService.overview());
    }

    @GetMapping("/datasets")
    public ApiResponse<PageResponse<TrainingDataset>> datasets(@RequestParam(required = false) String status,
                                                               @RequestParam(defaultValue = "1") int page,
                                                               @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listDatasets(status, page, size));
    }

    @PostMapping("/datasets")
    public ApiResponse<TrainingDataset> createDataset(@RequestBody AdminEnterpriseService.DatasetMutation request) {
        return ApiResponse.success(enterpriseService.createDataset(currentUserId(), request));
    }

    @PatchMapping("/datasets/{id}")
    public ApiResponse<TrainingDataset> updateDataset(@PathVariable Long id,
                                                      @RequestBody AdminEnterpriseService.DatasetMutation request) {
        return ApiResponse.success(enterpriseService.updateDataset(id, currentUserId(), request));
    }

    @GetMapping("/models")
    public ApiResponse<PageResponse<ModelVersion>> models(@RequestParam(required = false) String status,
                                                          @RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listModels(status, page, size));
    }

    @PostMapping("/models")
    public ApiResponse<ModelVersion> createModel(@RequestBody AdminEnterpriseService.ModelMutation request) {
        return ApiResponse.success(enterpriseService.createModel(currentUserId(), request));
    }

    @PatchMapping("/models/{id}")
    public ApiResponse<ModelVersion> updateModel(@PathVariable Long id,
                                                 @RequestBody AdminEnterpriseService.ModelMutation request) {
        return ApiResponse.success(enterpriseService.updateModel(id, currentUserId(), request));
    }

    @GetMapping("/eval-reports")
    public ApiResponse<PageResponse<OfflineEvalReport>> evalReports(@RequestParam(required = false) Long modelVersionId,
                                                                    @RequestParam(required = false) Long datasetId,
                                                                    @RequestParam(defaultValue = "1") int page,
                                                                    @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listEvalReports(modelVersionId, datasetId, page, size));
    }

    @PostMapping("/eval-reports")
    public ApiResponse<OfflineEvalReport> createEvalReport(@RequestBody AdminEnterpriseService.EvalReportMutation request) {
        return ApiResponse.success(enterpriseService.createEvalReport(request));
    }

    @GetMapping("/moderation-cases")
    public ApiResponse<PageResponse<ContentModerationCase>> moderationCases(@RequestParam(required = false) String status,
                                                                            @RequestParam(required = false) String priority,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listModerationCases(status, priority, page, size));
    }

    @PostMapping("/moderation-cases")
    public ApiResponse<ContentModerationCase> createModerationCase(@RequestBody AdminEnterpriseService.ModerationCaseMutation request) {
        return ApiResponse.success(enterpriseService.createModerationCase(currentUserId(), request));
    }

    @PatchMapping("/moderation-cases/{id}/resolve")
    public ApiResponse<ContentModerationCase> resolveModerationCase(@PathVariable Long id,
                                                                    @RequestBody AdminEnterpriseService.ModerationResolveRequest request) {
        return ApiResponse.success(enterpriseService.resolveModerationCase(id, currentUserId(), request));
    }

    @GetMapping("/account-actions")
    public ApiResponse<PageResponse<AccountModerationAction>> accountActions(@RequestParam(required = false) Long userId,
                                                                            @RequestParam(required = false) String status,
                                                                            @RequestParam(defaultValue = "1") int page,
                                                                            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listAccountActions(userId, status, page, size));
    }

    @PostMapping("/account-actions")
    public ApiResponse<AccountModerationAction> createAccountAction(@RequestBody AdminEnterpriseService.AccountActionMutation request) {
        return ApiResponse.success(enterpriseService.createAccountAction(currentUserId(), request));
    }

    @GetMapping("/creators")
    public ApiResponse<PageResponse<CreatorProfile>> creators(@RequestParam(required = false) String level,
                                                              @RequestParam(defaultValue = "1") int page,
                                                              @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listCreators(level, page, size));
    }

    @PostMapping("/creators")
    public ApiResponse<CreatorProfile> upsertCreator(@RequestBody AdminEnterpriseService.CreatorMutation request) {
        return ApiResponse.success(enterpriseService.upsertCreator(currentUserId(), request));
    }

    @GetMapping("/commercial-content")
    public ApiResponse<PageResponse<CommercialContentProfile>> commercialContent(@RequestParam(required = false) String status,
                                                                                @RequestParam(defaultValue = "1") int page,
                                                                                @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.success(enterpriseService.listCommercialContent(status, page, size));
    }

    @PostMapping("/commercial-content")
    public ApiResponse<CommercialContentProfile> upsertCommercialContent(@RequestBody AdminEnterpriseService.CommercialContentMutation request) {
        return ApiResponse.success(enterpriseService.upsertCommercialContent(currentUserId(), request));
    }

    private Long currentUserId() {
        return SecurityUtils.currentUserIdOrThrow();
    }
}
