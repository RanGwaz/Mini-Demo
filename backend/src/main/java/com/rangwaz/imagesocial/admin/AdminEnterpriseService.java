package com.rangwaz.imagesocial.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rangwaz.imagesocial.common.api.PageResponse;
import com.rangwaz.imagesocial.common.exception.BusinessException;
import com.rangwaz.imagesocial.domain.entity.AccountModerationAction;
import com.rangwaz.imagesocial.domain.entity.CommercialContentProfile;
import com.rangwaz.imagesocial.domain.entity.ContentModerationCase;
import com.rangwaz.imagesocial.domain.entity.CreatorProfile;
import com.rangwaz.imagesocial.domain.entity.ModelVersion;
import com.rangwaz.imagesocial.domain.entity.OfflineEvalReport;
import com.rangwaz.imagesocial.domain.entity.Post;
import com.rangwaz.imagesocial.domain.entity.TrainingDataset;
import com.rangwaz.imagesocial.domain.entity.User;
import com.rangwaz.imagesocial.domain.mapper.AccountModerationActionMapper;
import com.rangwaz.imagesocial.domain.mapper.CommercialContentProfileMapper;
import com.rangwaz.imagesocial.domain.mapper.ContentModerationCaseMapper;
import com.rangwaz.imagesocial.domain.mapper.CreatorProfileMapper;
import com.rangwaz.imagesocial.domain.mapper.ModelVersionMapper;
import com.rangwaz.imagesocial.domain.mapper.OfflineEvalReportMapper;
import com.rangwaz.imagesocial.domain.mapper.PostMapper;
import com.rangwaz.imagesocial.domain.mapper.TrainingDatasetMapper;
import com.rangwaz.imagesocial.domain.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminEnterpriseService {
    private static final int MAX_PAGE_SIZE = 100;

    private final TrainingDatasetMapper trainingDatasetMapper;
    private final ModelVersionMapper modelVersionMapper;
    private final OfflineEvalReportMapper offlineEvalReportMapper;
    private final ContentModerationCaseMapper moderationCaseMapper;
    private final AccountModerationActionMapper accountActionMapper;
    private final CreatorProfileMapper creatorProfileMapper;
    private final CommercialContentProfileMapper commercialContentMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AdminEnterpriseService(TrainingDatasetMapper trainingDatasetMapper,
                                  ModelVersionMapper modelVersionMapper,
                                  OfflineEvalReportMapper offlineEvalReportMapper,
                                  ContentModerationCaseMapper moderationCaseMapper,
                                  AccountModerationActionMapper accountActionMapper,
                                  CreatorProfileMapper creatorProfileMapper,
                                  CommercialContentProfileMapper commercialContentMapper,
                                  PostMapper postMapper,
                                  UserMapper userMapper,
                                  ObjectMapper objectMapper) {
        this.trainingDatasetMapper = trainingDatasetMapper;
        this.modelVersionMapper = modelVersionMapper;
        this.offlineEvalReportMapper = offlineEvalReportMapper;
        this.moderationCaseMapper = moderationCaseMapper;
        this.accountActionMapper = accountActionMapper;
        this.creatorProfileMapper = creatorProfileMapper;
        this.commercialContentMapper = commercialContentMapper;
        this.postMapper = postMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    public EnterpriseOverview overview() {
        long datasets = trainingDatasetMapper.selectCount(new LambdaQueryWrapper<TrainingDataset>());
        long models = modelVersionMapper.selectCount(new LambdaQueryWrapper<ModelVersion>());
        long openCases = moderationCaseMapper.selectCount(new LambdaQueryWrapper<ContentModerationCase>().eq(ContentModerationCase::getStatus, "OPEN"));
        long creators = creatorProfileMapper.selectCount(new LambdaQueryWrapper<CreatorProfile>());
        long commercialPosts = commercialContentMapper.selectCount(new LambdaQueryWrapper<CommercialContentProfile>());
        return new EnterpriseOverview(datasets, models, openCases, creators, commercialPosts);
    }

    public PageResponse<TrainingDataset> listDatasets(String status, int page, int size) {
        LambdaQueryWrapper<TrainingDataset> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) wrapper.eq(TrainingDataset::getStatus, status.trim().toUpperCase());
        long total = trainingDatasetMapper.selectCount(wrapper);
        return new PageResponse<>(trainingDatasetMapper.selectList(wrapper.orderByDesc(TrainingDataset::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public TrainingDataset createDataset(Long operatorId, DatasetMutation request) {
        TrainingDataset dataset = new TrainingDataset();
        applyDataset(dataset, operatorId, request);
        trainingDatasetMapper.insert(dataset);
        return trainingDatasetMapper.selectById(dataset.getId());
    }

    @Transactional
    public TrainingDataset updateDataset(Long id, Long operatorId, DatasetMutation request) {
        TrainingDataset dataset = requireDataset(id);
        applyDataset(dataset, operatorId, request);
        trainingDatasetMapper.updateById(dataset);
        return trainingDatasetMapper.selectById(id);
    }

    public PageResponse<ModelVersion> listModels(String status, int page, int size) {
        LambdaQueryWrapper<ModelVersion> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) wrapper.eq(ModelVersion::getStatus, status.trim().toUpperCase());
        long total = modelVersionMapper.selectCount(wrapper);
        return new PageResponse<>(modelVersionMapper.selectList(wrapper.orderByDesc(ModelVersion::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public ModelVersion createModel(Long operatorId, ModelMutation request) {
        ModelVersion model = new ModelVersion();
        applyModel(model, operatorId, request);
        modelVersionMapper.insert(model);
        return modelVersionMapper.selectById(model.getId());
    }

    @Transactional
    public ModelVersion updateModel(Long id, Long operatorId, ModelMutation request) {
        ModelVersion model = requireModel(id);
        applyModel(model, operatorId, request);
        modelVersionMapper.updateById(model);
        return modelVersionMapper.selectById(id);
    }

    public PageResponse<OfflineEvalReport> listEvalReports(Long modelVersionId, Long datasetId, int page, int size) {
        LambdaQueryWrapper<OfflineEvalReport> wrapper = new LambdaQueryWrapper<>();
        if (modelVersionId != null) wrapper.eq(OfflineEvalReport::getModelVersionId, modelVersionId);
        if (datasetId != null) wrapper.eq(OfflineEvalReport::getDatasetId, datasetId);
        long total = offlineEvalReportMapper.selectCount(wrapper);
        return new PageResponse<>(offlineEvalReportMapper.selectList(wrapper.orderByDesc(OfflineEvalReport::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public OfflineEvalReport createEvalReport(EvalReportMutation request) {
        OfflineEvalReport report = new OfflineEvalReport();
        report.setModelVersionId(request.modelVersionId());
        report.setDatasetId(request.datasetId());
        report.setAuc(request.auc());
        report.setNdcg(request.ndcg());
        report.setRecallScore(request.recallScore());
        report.setPrecisionScore(request.precisionScore());
        report.setMetricsJson(toJson(request.metrics()));
        report.setReportPath(firstNonBlank(request.reportPath(), ""));
        report.setStatus(firstNonBlank(request.status(), "READY").toUpperCase());
        offlineEvalReportMapper.insert(report);
        return offlineEvalReportMapper.selectById(report.getId());
    }

    public PageResponse<ContentModerationCase> listModerationCases(String status, String priority, int page, int size) {
        LambdaQueryWrapper<ContentModerationCase> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) wrapper.eq(ContentModerationCase::getStatus, status.trim().toUpperCase());
        if (hasText(priority)) wrapper.eq(ContentModerationCase::getPriority, priority.trim().toUpperCase());
        long total = moderationCaseMapper.selectCount(wrapper);
        return new PageResponse<>(moderationCaseMapper.selectList(wrapper.orderByAsc(ContentModerationCase::getStatus).orderByDesc(ContentModerationCase::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public ContentModerationCase createModerationCase(Long operatorId, ModerationCaseMutation request) {
        requirePost(request.postId());
        ContentModerationCase moderationCase = new ContentModerationCase();
        moderationCase.setPostId(request.postId());
        moderationCase.setReporterId(request.reporterId());
        moderationCase.setReason(firstNonBlank(request.reason(), ""));
        moderationCase.setStatus(firstNonBlank(request.status(), "OPEN").toUpperCase());
        moderationCase.setPriority(firstNonBlank(request.priority(), "NORMAL").toUpperCase());
        moderationCase.setRiskLevel(firstNonBlank(request.riskLevel(), "NORMAL").toUpperCase());
        moderationCase.setAssignedTo(request.assignedTo());
        moderationCase.setOperatorId(operatorId);
        moderationCaseMapper.insert(moderationCase);
        return moderationCaseMapper.selectById(moderationCase.getId());
    }

    @Transactional
    public ContentModerationCase resolveModerationCase(Long id, Long operatorId, ModerationResolveRequest request) {
        ContentModerationCase moderationCase = requireModerationCase(id);
        String decision = firstNonBlank(request.decision(), "NO_ACTION").toUpperCase();
        moderationCase.setDecision(decision);
        moderationCase.setActionNote(firstNonBlank(request.actionNote(), ""));
        moderationCase.setStatus(firstNonBlank(request.status(), "RESOLVED").toUpperCase());
        moderationCase.setOperatorId(operatorId);
        moderationCase.setResolvedAt(LocalDateTime.now());
        applyPostDecision(moderationCase.getPostId(), decision);
        moderationCaseMapper.updateById(moderationCase);
        return moderationCaseMapper.selectById(id);
    }

    public PageResponse<AccountModerationAction> listAccountActions(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<AccountModerationAction> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) wrapper.eq(AccountModerationAction::getUserId, userId);
        if (hasText(status)) wrapper.eq(AccountModerationAction::getStatus, status.trim().toUpperCase());
        long total = accountActionMapper.selectCount(wrapper);
        return new PageResponse<>(accountActionMapper.selectList(wrapper.orderByDesc(AccountModerationAction::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public AccountModerationAction createAccountAction(Long operatorId, AccountActionMutation request) {
        User user = userMapper.selectById(request.userId());
        if (user == null) throw new BusinessException("用户不存在");
        AccountModerationAction action = new AccountModerationAction();
        action.setUserId(request.userId());
        action.setOperatorId(operatorId);
        action.setActionType(firstNonBlank(request.actionType(), "WARN").toUpperCase());
        action.setReason(firstNonBlank(request.reason(), ""));
        action.setStatus("ACTIVE");
        action.setExpiresAt(request.expiresAt());
        accountActionMapper.insert(action);
        user.setStatus(statusForAction(action.getActionType()));
        userMapper.updateById(user);
        return accountActionMapper.selectById(action.getId());
    }

    public PageResponse<CreatorProfile> listCreators(String level, int page, int size) {
        LambdaQueryWrapper<CreatorProfile> wrapper = new LambdaQueryWrapper<>();
        if (hasText(level)) wrapper.eq(CreatorProfile::getCreatorLevel, level.trim().toUpperCase());
        long total = creatorProfileMapper.selectCount(wrapper);
        return new PageResponse<>(creatorProfileMapper.selectList(wrapper.orderByDesc(CreatorProfile::getQualityScore).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public CreatorProfile upsertCreator(Long operatorId, CreatorMutation request) {
        if (userMapper.selectById(request.userId()) == null) throw new BusinessException("用户不存在");
        CreatorProfile profile = creatorProfileMapper.selectOne(new LambdaQueryWrapper<CreatorProfile>().eq(CreatorProfile::getUserId, request.userId()));
        if (profile == null) {
            profile = new CreatorProfile();
            profile.setUserId(request.userId());
        }
        profile.setDomainTags(firstNonBlank(request.domainTags(), ""));
        profile.setCreatorLevel(firstNonBlank(request.creatorLevel(), "SEED").toUpperCase());
        profile.setQualityScore(request.qualityScore() == null ? BigDecimal.ZERO : request.qualityScore());
        profile.setViolationStatus(firstNonBlank(request.violationStatus(), "NORMAL").toUpperCase());
        profile.setMonetizationStatus(firstNonBlank(request.monetizationStatus(), "DISABLED").toUpperCase());
        profile.setCommercialStatus(firstNonBlank(request.commercialStatus(), "NORMAL").toUpperCase());
        profile.setOperatorId(operatorId);
        if (profile.getId() == null) creatorProfileMapper.insert(profile); else creatorProfileMapper.updateById(profile);
        return creatorProfileMapper.selectById(profile.getId());
    }

    public PageResponse<CommercialContentProfile> listCommercialContent(String status, int page, int size) {
        LambdaQueryWrapper<CommercialContentProfile> wrapper = new LambdaQueryWrapper<>();
        if (hasText(status)) wrapper.eq(CommercialContentProfile::getStatus, status.trim().toUpperCase());
        long total = commercialContentMapper.selectCount(wrapper);
        return new PageResponse<>(commercialContentMapper.selectList(wrapper.orderByDesc(CommercialContentProfile::getCreatedAt).last(limitOffset(page, size))), total, normalizePage(page), normalizeSize(size));
    }

    @Transactional
    public CommercialContentProfile upsertCommercialContent(Long operatorId, CommercialContentMutation request) {
        requirePost(request.postId());
        CommercialContentProfile profile = commercialContentMapper.selectOne(new LambdaQueryWrapper<CommercialContentProfile>().eq(CommercialContentProfile::getPostId, request.postId()));
        if (profile == null) {
            profile = new CommercialContentProfile();
            profile.setPostId(request.postId());
        }
        profile.setBrandName(firstNonBlank(request.brandName(), ""));
        profile.setDisclosureType(firstNonBlank(request.disclosureType(), "NONE").toUpperCase());
        profile.setCampaignCode(firstNonBlank(request.campaignCode(), ""));
        profile.setStatus(firstNonBlank(request.status(), "DRAFT").toUpperCase());
        profile.setBidType(firstNonBlank(request.bidType(), ""));
        profile.setBudgetCents(request.budgetCents() == null ? 0L : request.budgetCents());
        profile.setLandingUrl(firstNonBlank(request.landingUrl(), ""));
        profile.setConfigJson(toJson(request.config()));
        profile.setOperatorId(operatorId);
        if (profile.getId() == null) commercialContentMapper.insert(profile); else commercialContentMapper.updateById(profile);
        return commercialContentMapper.selectById(profile.getId());
    }

    private void applyDataset(TrainingDataset dataset, Long operatorId, DatasetMutation request) {
        dataset.setName(requireText(request.name(), "训练数据集名称不能为空"));
        dataset.setDatasetType(firstNonBlank(request.datasetType(), "RANKING").toUpperCase());
        dataset.setStatus(firstNonBlank(request.status(), "DRAFT").toUpperCase());
        dataset.setSplitStrategy(firstNonBlank(request.splitStrategy(), "time_8_1_1"));
        dataset.setSourceWindowStart(request.sourceWindowStart());
        dataset.setSourceWindowEnd(request.sourceWindowEnd());
        dataset.setRowCount(request.rowCount() == null ? 0L : request.rowCount());
        dataset.setPositiveCount(request.positiveCount() == null ? 0L : request.positiveCount());
        dataset.setNegativeCount(request.negativeCount() == null ? 0L : request.negativeCount());
        dataset.setFilePath(firstNonBlank(request.filePath(), ""));
        dataset.setMetricsJson(toJson(request.metrics()));
        dataset.setOperatorId(operatorId);
    }

    private void applyModel(ModelVersion model, Long operatorId, ModelMutation request) {
        model.setModelName(requireText(request.modelName(), "模型名称不能为空"));
        model.setVersion(requireText(request.version(), "模型版本不能为空"));
        model.setModelType(firstNonBlank(request.modelType(), "RANKING").toUpperCase());
        model.setStatus(firstNonBlank(request.status(), "DRAFT").toUpperCase());
        model.setDatasetId(request.datasetId());
        model.setArtifactUri(firstNonBlank(request.artifactUri(), ""));
        model.setShadowEnabled(Boolean.TRUE.equals(request.shadowEnabled()));
        model.setOnlineEnabled(Boolean.TRUE.equals(request.onlineEnabled()));
        model.setTrafficPercent(request.trafficPercent() == null ? BigDecimal.ZERO : request.trafficPercent());
        model.setGuardrailJson(toJson(request.guardrail()));
        model.setOperatorId(operatorId);
    }

    private void applyPostDecision(Long postId, String decision) {
        Post post = requirePost(postId);
        if ("APPROVE".equals(decision)) {
            post.setVisibility("PUBLIC");
            post.setAuditStatus("APPROVED");
        } else if ("REJECT".equals(decision) || "REMOVE".equals(decision) || "HIDE".equals(decision)) {
            post.setVisibility("HIDDEN");
            post.setAuditStatus("REJECTED");
        } else if ("PENDING_REVIEW".equals(decision)) {
            post.setAuditStatus("PENDING_REVIEW");
        }
        postMapper.updateById(post);
    }

    private String statusForAction(String actionType) {
        return switch (actionType) {
            case "BAN" -> "BANNED";
            case "MUTE" -> "MUTED";
            case "LIMIT" -> "LIMITED";
            case "RESTORE" -> "ACTIVE";
            default -> "ACTIVE";
        };
    }

    private TrainingDataset requireDataset(Long id) {
        TrainingDataset dataset = trainingDatasetMapper.selectById(id);
        if (dataset == null) throw new BusinessException("训练数据集不存在");
        return dataset;
    }

    private ModelVersion requireModel(Long id) {
        ModelVersion model = modelVersionMapper.selectById(id);
        if (model == null) throw new BusinessException("模型版本不存在");
        return model;
    }

    private ContentModerationCase requireModerationCase(Long id) {
        ContentModerationCase moderationCase = moderationCaseMapper.selectById(id);
        if (moderationCase == null) throw new BusinessException("审核案件不存在");
        return moderationCase;
    }

    private Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) throw new BusinessException("内容不存在");
        return post;
    }

    private int normalizePage(int page) {
        return Math.max(1, page);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(MAX_PAGE_SIZE, size));
    }

    private String limitOffset(int page, int size) {
        int safeSize = normalizeSize(size);
        return "LIMIT " + safeSize + " OFFSET " + ((normalizePage(page) - 1) * safeSize);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String requireText(String value, String message) {
        if (!hasText(value)) throw new BusinessException(message);
        return value.trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    public record EnterpriseOverview(long datasets, long models, long openModerationCases, long creators, long commercialPosts) {}

    public record DatasetMutation(String name, String datasetType, String status, String splitStrategy,
                                  LocalDateTime sourceWindowStart, LocalDateTime sourceWindowEnd,
                                  Long rowCount, Long positiveCount, Long negativeCount,
                                  String filePath, Map<String, Object> metrics) {}

    public record ModelMutation(String modelName, String version, String modelType, String status,
                                Long datasetId, String artifactUri, Boolean shadowEnabled,
                                Boolean onlineEnabled, BigDecimal trafficPercent,
                                Map<String, Object> guardrail) {}

    public record EvalReportMutation(Long modelVersionId, Long datasetId, BigDecimal auc, BigDecimal ndcg,
                                     BigDecimal recallScore, BigDecimal precisionScore,
                                     Map<String, Object> metrics, String reportPath, String status) {}

    public record ModerationCaseMutation(Long postId, Long reporterId, String reason, String status,
                                         String priority, String riskLevel, Long assignedTo) {}

    public record ModerationResolveRequest(String decision, String status, String actionNote) {}

    public record AccountActionMutation(Long userId, String actionType, String reason, LocalDateTime expiresAt) {}

    public record CreatorMutation(Long userId, String domainTags, String creatorLevel, BigDecimal qualityScore,
                                  String violationStatus, String monetizationStatus, String commercialStatus) {}

    public record CommercialContentMutation(Long postId, String brandName, String disclosureType,
                                            String campaignCode, String status, String bidType,
                                            Long budgetCents, String landingUrl, Map<String, Object> config) {}
}
