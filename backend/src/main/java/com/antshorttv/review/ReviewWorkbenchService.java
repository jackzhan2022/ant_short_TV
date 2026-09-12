package com.antshorttv.review;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiProviderMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponse;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.scriptcontent.ScriptContentParser;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReviewWorkbenchService {
    private static final Set<String> REVIEW_MODES = Set.of("QUICK", "DEEP");
    private static final Set<String> REVIEW_SCOPE_TYPES = Set.of("ALL", "EPISODES", "SCENES");
    private static final List<String> DEFAULT_DIMENSIONS = List.of(
        "剧情逻辑与因果",
        "台词合理性",
        "人物关系一致性",
        "人物认知一致性",
        "人物动机",
        "时间线连续性",
        "场景连续性",
        "道具连续性",
        "视觉连续性",
        "分镜可执行性",
        "情绪递进",
        "悬念与反转铺垫",
        "伏笔回收"
    );

    private final TenantContextResolver tenantContextResolver;
    private final ReviewAccessGuard reviewAccessGuard;
    private final RbacPermissionService permissionService;
    private final ReviewProjectMapper projectMapper;
    private final ReviewScriptVersionMapper versionMapper;
    private final ReviewTaskMapper taskMapper;
    private final ReviewExportRecordMapper exportMapper;
    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final ObjectMapper objectMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper executionResponseMapper;
    private final AiPointReservationMapper pointReservationMapper;
    private final AiPointSettlementService pointSettlementService;
    private final ReviewContentService reviewContentService;
    private final ReviewFanoutSnapshotMapper fanoutSnapshotMapper;
    private final ReviewFanoutUnitMapper fanoutUnitMapper;
    private final ReviewQuickAgentAdapter reviewQuickAgentAdapter;
    private final ReviewDeepAgentCoordinator reviewDeepAgentCoordinator;
    private final ReviewObservabilityRepository reviewObservabilityRepository;
    private final ScriptContentParser scriptContentParser;
    private final int quickSafeCharacters;
    private final Path exportRoot;

    public ReviewWorkbenchService(
        TenantContextResolver tenantContextResolver,
        ReviewAccessGuard reviewAccessGuard,
        RbacPermissionService permissionService,
        ReviewProjectMapper projectMapper,
        ReviewScriptVersionMapper versionMapper,
        ReviewTaskMapper taskMapper,
        ReviewExportRecordMapper exportMapper,
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        ObjectMapper objectMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper executionResponseMapper,
        AiPointReservationMapper pointReservationMapper,
        AiPointSettlementService pointSettlementService,
        ReviewContentService reviewContentService,
        ReviewFanoutSnapshotMapper fanoutSnapshotMapper,
        ReviewFanoutUnitMapper fanoutUnitMapper,
        ReviewQuickAgentAdapter reviewQuickAgentAdapter,
        ReviewDeepAgentCoordinator reviewDeepAgentCoordinator,
        ReviewObservabilityRepository reviewObservabilityRepository,
        ScriptContentParser scriptContentParser,
        @Value("${review.workflow.quick-safe-characters:80000}") int quickSafeCharacters,
        @Value("${review.export-root:storage/review-exports}") String exportRoot
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.reviewAccessGuard = reviewAccessGuard;
        this.permissionService = permissionService;
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.exportMapper = exportMapper;
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.executionResponseMapper = executionResponseMapper;
        this.pointReservationMapper = pointReservationMapper;
        this.pointSettlementService = pointSettlementService;
        this.reviewContentService = reviewContentService;
        this.fanoutSnapshotMapper = fanoutSnapshotMapper;
        this.fanoutUnitMapper = fanoutUnitMapper;
        this.reviewQuickAgentAdapter = reviewQuickAgentAdapter;
        this.reviewDeepAgentCoordinator = reviewDeepAgentCoordinator;
        this.reviewObservabilityRepository = reviewObservabilityRepository;
        this.scriptContentParser = scriptContentParser;
        this.quickSafeCharacters = Math.min(50000, quickSafeCharacters);
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public List<ReviewProjectSummaryResponse> listProjects(Long tenantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        List<ReviewProjectEntity> projects = projectMapper.selectActive(context.tenantId()).stream()
            .filter(project -> reviewAccessGuard.canView(context, project))
            .toList();
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(ReviewProjectEntity::getId).toList();
        Map<Long, Integer> versionCounts = versionMapper.selectByProjects(context.tenantId(), projectIds).stream()
            .collect(Collectors.groupingBy(ReviewScriptVersionEntity::getProjectId,
                Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));
        Map<Long, ReviewTaskEntity> latestTasks = new LinkedHashMap<>();
        for (ReviewTaskEntity task : taskMapper.selectByProjects(context.tenantId(), projectIds)) {
            latestTasks.putIfAbsent(task.getProjectId(), task);
        }
        return projects.stream()
            .map(project -> toProjectSummary(project, versionCounts.getOrDefault(project.getId(), 0),
                latestTasks.get(project.getId())))
            .toList();
    }

    @Transactional
    public ReviewProjectDetailResponse importProject(
        Long tenantId,
        Long mainProjectId,
        String name,
        MultipartFile file,
        String content
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        LocalDateTime now = LocalDateTime.now();
        String resolvedContent = resolveImportedContent(file, content);
        if (resolvedContent.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入或上传剧本内容。");
        }
        ReviewProjectEntity project = new ReviewProjectEntity();
        project.setTenantId(context.tenantId());
        project.setMainProjectId(mainProjectId);
        project.setName(blankToNull(name) == null ? inferProjectName(file, resolvedContent) : name.trim());
        project.setSourceFileName(file == null ? null : blankToNull(file.getOriginalFilename()));
        project.setSourceType(resolveSourceType(file));
        project.setOriginalContent(resolvedContent);
        project.setStatus("ACTIVE");
        project.setCreatedBy(context.userId());
        project.setCreatedAt(now);
        project.setUpdatedAt(now);
        if (mainProjectId != null) {
            project.setMainProjectId(null);
            reviewAccessGuard.requireBinding(context, project, mainProjectId);
            project.setMainProjectId(mainProjectId);
        }
        projectMapper.insert(project);

        ReviewScriptVersionEntity version = createVersion(context, project.getId(), 1, "IMPORT", project.getSourceFileName(), resolvedContent, now);
        project.setCurrentVersionId(version.getId());
        projectMapper.updateById(project);
        return detailProject(tenantId, project.getId());
    }

    public ReviewProjectDetailResponse detailProject(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "PROJECT:VIEW");
        return new ReviewProjectDetailResponse(
            toProjectSummary(project, context.tenantId()),
            versionMapper.selectByProject(context.tenantId(), projectId).stream().map(this::toVersionResponse).toList(),
            listTasksForProject(context.tenantId(), projectId).stream().map(task -> toTaskResponse(task, false)).toList()
        );
    }

    public ReviewProjectReviewHistoryResponse reviewHistory(
        Long tenantId, Long projectId, int requestedPage, int requestedPageSize
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "PROJECT:VIEW");
        int page = Math.max(1, requestedPage);
        int pageSize = Math.min(100, Math.max(1, requestedPageSize));
        long total = taskMapper.countByProject(context.tenantId(), projectId);
        int offset = Math.toIntExact(Math.min(Integer.MAX_VALUE, (long) (page - 1) * pageSize));
        List<ReviewTaskEntity> tasks = taskMapper.selectHistoryPage(
            context.tenantId(), projectId, offset, pageSize);
        return new ReviewProjectReviewHistoryResponse(
            toProjectSummary(project, context.tenantId()),
            versionMapper.selectByProject(context.tenantId(), projectId).stream()
                .map(this::toVersionMetadataResponse).toList(),
            tasks.stream()
                .map(this::toHistoryTaskResponse)
                .toList(),
            page, pageSize, total);
    }

    @Transactional
    public ReviewVersionResponse saveVersion(Long tenantId, Long projectId, SaveReviewVersionRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "SCRIPT:EDIT");
        if (request.content() == null || request.content().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧本内容不能为空。");
        }
        int nextVersionNo = nextVersionNo(context.tenantId(), projectId);
        LocalDateTime now = LocalDateTime.now();
        ReviewScriptVersionEntity version = createVersion(
            context,
            projectId,
            nextVersionNo,
            blankToNull(request.sourceType()) == null ? "MANUAL_EDIT" : request.sourceType().trim().toUpperCase(Locale.ROOT),
            blankToNull(request.fileName()),
            request.content().trim(),
            now
        );
        project.setCurrentVersionId(version.getId());
        project.setOriginalContent(project.getOriginalContent() == null ? request.content().trim() : project.getOriginalContent());
        project.setUpdatedAt(now);
        projectMapper.updateById(project);
        return toVersionResponse(version);
    }

    @Transactional
    public AiExecutionResponse createTask(Long tenantId, Long projectId, CreateReviewTaskRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "AI_SERVICE:USE");
        List<String> dimensions = normalizeDimensions(request.selectedDimensions());
        if (dimensions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择至少一个审核维度。");
        }
        String reviewMode = normalizeMode(request.reviewMode());
        String scopeType = normalizeScopeType(request.reviewScopeType());
        ReviewScriptVersionEntity version = request.versionId() == null
            ? requireCurrentVersion(project)
            : requireVersion(context.tenantId(), projectId, request.versionId());
        String scopeJson = serialize(request.reviewScope() == null ? Map.of() : request.reviewScope());
        String idempotencyKey = buildIdempotencyKey(projectId, version.getId(), reviewMode, scopeType, dimensions, scopeJson);
        ReviewTaskEntity existing = taskMapper.selectByIdempotencyKey(context.tenantId(), idempotencyKey);
        if (existing != null) {
            return executionResponseMapper.toResponse(executionService.requireTask(existing.getExecutionId()));
        }
        Integer roundNo = nextRoundNo(context.tenantId(), projectId);
        LocalDateTime now = LocalDateTime.now();
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTenantId(context.tenantId());
        task.setProjectId(projectId);
        task.setScriptVersionId(version.getId());
        task.setRoundNo(roundNo);
        task.setReviewMode(reviewMode);
        task.setSelectedDimensionsJson(serialize(dimensions));
        task.setReviewScopeType(scopeType);
        task.setReviewScopeJson(scopeJson);
        ReviewContentService.FrozenReview frozen = reviewContentService.freeze(
            version.getContent(), scopeType, request.reviewScope() == null ? Map.of() : request.reviewScope(), dimensions);
        if ("QUICK".equals(reviewMode)) {
            reviewContentService.requireQuickBudget(frozen, quickSafeCharacters);
        }
        task.setVersionHash(frozen.versionHash());
        task.setScopeHash(frozen.scopeHash());
        task.setDimensionsHash(frozen.dimensionsHash());
        task.setStatus("PENDING");
        task.setCurrentStage("AI_REVIEW");
        task.setOverallProgress(0);
        task.setCurrentAction("等待开始审核");
        task.setIdempotencyKey(idempotencyKey);
        task.setCreatedBy(context.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        Long modelId = resolveDefaultTextModelId(context.tenantId());
        AiExecutionTaskEntity execution = executionService.createWithReservation(
            new AiExecutionCreateCommand(
                context.tenantId(), context.userId(), project.getMainProjectId(),
                AiBusinessScene.SCRIPT_REVIEW.code(), "TEXT", "REVIEW_TASK", task.getId(),
                modelId, "AI_REVIEW", idempotencyKey, "script-review-" + task.getId(), true,
                "{\"reviewTaskId\":" + task.getId() + "}"
            ),
            Map.of(AiUsageMetric.CALL, BigDecimal.ONE),
            Map.of("reviewMode", reviewMode)
        );
        task.setExecutionId(execution.id);
        taskMapper.updateById(task);
        project.setLastTaskId(task.getId());
        project.setUpdatedAt(now);
        projectMapper.updateById(project);
        return executionResponseMapper.toResponse(execution);
    }

    public List<ReviewTaskResponse> listTasks(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireAccessibleProject(context, projectId, "PROJECT:VIEW");
        return listTasksForProject(context.tenantId(), projectId).stream()
            .map(task -> toTaskResponse(task, false))
            .toList();
    }

    public ReviewTaskResponse taskDetail(Long tenantId, Long taskId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "PROJECT:VIEW");
        return toTaskResponse(task, true);
    }

    public ReviewTaskResponse detailTask(Long tenantId, Long taskId) {
        return taskDetail(tenantId, taskId);
    }

    @Transactional
    public AiExecutionResponse cancelTask(Long tenantId, Long taskId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "AI_SERVICE:USE");
        if (!List.of("PENDING", "RUNNING").contains(task.getStatus())) {
            return executionResponseMapper.toResponse(executionService.requireTask(task.getExecutionId()));
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("CANCELED");
        task.setCurrentAction("任务已取消");
        task.setCanceledAt(now);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        if (task.getFanoutSnapshotId() != null) {
            fanoutUnitMapper.update(null, new UpdateWrapper<ReviewFanoutUnitEntity>()
                .set("status", "CANCELED").set("updated_at", now)
                .eq("snapshot_id", task.getFanoutSnapshotId()).in("status", "PENDING", "RUNNING"));
            fanoutSnapshotMapper.update(null, new UpdateWrapper<ReviewFanoutSnapshotEntity>()
                .set("status", "CANCELED").set("aggregation_status", "CANCELED")
                .set("canceled_at", now).set("updated_at", now).eq("id", task.getFanoutSnapshotId()));
        }
        AiExecutionTaskEntity execution = executionService.cancel(task.getExecutionId());
        AiPointReservationEntity reservation = pointReservationMapper.selectByExecutionId(execution.id);
        if (reservation != null && "RESERVED".equals(reservation.status)) {
            reservation = pointSettlementService.finalizeOutcome(
                reservation.id,
                AiSettlementOutcome.PRE_CALL_CANCELED,
                Map.of(),
                null,
                null,
                "execution:%d:v%d:cancel".formatted(execution.id, execution.executionVersion)
            );
            executionService.updateSettlementSummary(reservation);
        }
        return executionResponseMapper.toResponse(executionService.requireTask(execution.id));
    }

    @Transactional
    public AiExecutionResponse retryTask(Long tenantId, Long taskId) {
        return retryTask(tenantId, taskId, false);
    }

    @Transactional
    public AiExecutionResponse retryTask(Long tenantId, Long taskId, boolean fullRegeneration) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "AI_SERVICE:USE");
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只有失败任务可以重试。");
        }
        LocalDateTime now = LocalDateTime.now();
        String retryKind = fullRegeneration ? "FULL_REGENERATION" : "WHOLE_TASK";
        if ("DEEP".equals(task.getReviewMode()) && task.getFanoutSnapshotId() != null) {
            ReviewFanoutSnapshotEntity snapshot = fanoutSnapshotMapper.selectById(task.getFanoutSnapshotId());
            if (fullRegeneration) {
                fanoutSnapshotMapper.update(null, new UpdateWrapper<ReviewFanoutSnapshotEntity>()
                    .set("status", "STALE").set("updated_at", now).eq("id", task.getFanoutSnapshotId()));
                taskMapper.update(null, new UpdateWrapper<ReviewTaskEntity>()
                    .set("fanout_snapshot_id", null)
                    .set("aggregation_run_id", null)
                    .eq("id", task.getId()));
                task.setFanoutSnapshotId(null);
                task.setAggregationRunId(null);
            } else {
                boolean unitsComplete = snapshot != null && fanoutUnitMapper.selectOrdered(snapshot.getId()).stream()
                    .allMatch(unit -> "SUCCEEDED".equals(unit.getStatus()) && Boolean.TRUE.equals(unit.getReportSaved()));
                retryKind = unitsComplete ? "AGGREGATION_ONLY" : "FAILED_UNITS";
            }
        }
        task.setStatus("PENDING");
        task.setRetryKind(retryKind);
        task.setCurrentStage("GLOBAL_INDEX");
        task.setCurrentAction("等待重新开始");
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setOverallProgress(0);
        task.setCompletedAt(null);
        task.setCanceledAt(null);
        task.setUpdatedAt(now);
        taskMapper.updateById(task);
        AiExecutionTaskEntity execution = executionService.requireTask(task.getExecutionId());
        if ("FAILED".equals(execution.status) || "TIMED_OUT".equals(execution.status)) {
            execution = executionService.retry(execution.id);
        }
        return executionResponseMapper.toResponse(execution);
    }

    @Transactional
    public ReviewTaskResponse updateTaskConfig(Long tenantId, Long taskId, UpdateReviewTaskRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "AI_SERVICE:USE");
        if ("RUNNING".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "任务运行中后不可修改配置。");
        }
        List<String> dimensions = request.selectedDimensions() == null || request.selectedDimensions().isEmpty()
            ? deserializeStringList(task.getSelectedDimensionsJson())
            : normalizeDimensions(request.selectedDimensions());
        if (dimensions.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择至少一个审核维度。");
        }
        String reviewMode = blankToNull(request.reviewMode()) == null
            ? task.getReviewMode()
            : normalizeMode(request.reviewMode());
        String scopeType = blankToNull(request.reviewScopeType()) == null
            ? task.getReviewScopeType()
            : normalizeScopeType(request.reviewScopeType());
        Map<String, Object> scope = request.reviewScope() == null || request.reviewScope().isEmpty()
            ? deserializeObject(task.getReviewScopeJson())
            : request.reviewScope();
        String scopeJson = serialize(scope);
        String idempotencyKey = buildIdempotencyKey(task.getProjectId(), task.getScriptVersionId(), reviewMode, scopeType, dimensions, scopeJson);
        ReviewTaskEntity existing = taskMapper.selectByIdempotencyKey(context.tenantId(), idempotencyKey);
        if (existing != null && !existing.getId().equals(task.getId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前配置已存在相同任务。");
        }
        task.setReviewMode(reviewMode);
        task.setSelectedDimensionsJson(serialize(dimensions));
        task.setReviewScopeType(scopeType);
        task.setReviewScopeJson(scopeJson);
        ReviewScriptVersionEntity version = requireVersion(
            context.tenantId(), task.getProjectId(), task.getScriptVersionId());
        ReviewContentService.FrozenReview frozen = reviewContentService.freeze(
            version.getContent(), scopeType, scope, dimensions);
        if ("QUICK".equals(reviewMode)) {
            reviewContentService.requireQuickBudget(frozen, quickSafeCharacters);
        }
        task.setVersionHash(frozen.versionHash());
        task.setScopeHash(frozen.scopeHash());
        task.setDimensionsHash(frozen.dimensionsHash());
        task.setStale(false);
        task.setIdempotencyKey(idempotencyKey);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskResponse(task, true);
    }

    @Transactional
    public ReviewVersionResponse rollbackVersion(Long tenantId, Long projectId, RollbackReviewVersionRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireAccessibleProject(context, projectId, "SCRIPT:EDIT");
        ReviewScriptVersionEntity source = requireVersion(context.tenantId(), projectId, request.versionId());
        int nextVersionNo = nextVersionNo(context.tenantId(), projectId);
        ReviewScriptVersionEntity copy = createVersion(
            context,
            projectId,
            nextVersionNo,
            "ROLLBACK",
            source.getFileName(),
            source.getContent(),
            LocalDateTime.now()
        );
        ReviewProjectEntity project = requireProject(context.tenantId(), projectId);
        project.setCurrentVersionId(copy.getId());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return toVersionResponse(copy);
    }

    @Transactional
    public ReviewExportRecordResponse exportReport(Long tenantId, Long projectId, ExportReviewReportRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "PROJECT:VIEW");
        ReviewScriptVersionEntity version = requireVersion(context.tenantId(), projectId, request.versionId());
        LambdaQueryWrapper<ReviewTaskEntity> taskQuery = new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, context.tenantId())
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .eq(ReviewTaskEntity::getScriptVersionId, version.getId());
        if (request.taskId() != null) taskQuery.eq(ReviewTaskEntity::getId, request.taskId());
        else taskQuery.orderByDesc(ReviewTaskEntity::getRoundNo).last("limit 1");
        ReviewTaskEntity task = taskMapper.selectOne(taskQuery);
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前版本暂无审核记录。");
        }

        String exportType = "MARKDOWN";
        String fileName = buildExportFileName(project.getName(), version.getVersionNo(), exportType);
        String text = task.getReportMarkdown();
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前任务没有可导出的 Markdown 报告。");
        }
        try {
            Files.createDirectories(exportRoot);
            Path target = exportRoot.resolve(fileName).normalize();
            if (!target.startsWith(exportRoot)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出路径不合法。");
            }
            Files.writeString(target, text, StandardCharsets.UTF_8);
            ReviewExportRecordEntity record = new ReviewExportRecordEntity();
            record.setTenantId(context.tenantId());
            record.setProjectId(projectId);
            record.setVersionId(version.getId());
            record.setTaskId(task.getId());
            record.setExportType(exportType);
            record.setExportStatus("SUCCEEDED");
            record.setFileName(fileName);
            record.setFileSize(Files.size(target));
            record.setDownloadUrl("/api/script-review/exports/" + fileName);
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            exportMapper.insert(record);
            return toExportRecordResponse(record);
        } catch (Exception exception) {
            ReviewExportRecordEntity record = new ReviewExportRecordEntity();
            record.setTenantId(context.tenantId());
            record.setProjectId(projectId);
            record.setVersionId(version.getId());
            record.setTaskId(task.getId());
            record.setExportType(exportType);
            record.setExportStatus("FAILED");
            record.setErrorMessage(exception.getMessage());
            record.setCreatedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            exportMapper.insert(record);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出失败：" + exception.getMessage());
        }
    }

    public ReviewVersionHistoryResponse versionHistory(Long tenantId, Long projectId, Long versionId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireAccessibleProject(context, projectId, "PROJECT:VIEW");
        ReviewScriptVersionEntity version = requireVersion(context.tenantId(), projectId, versionId);
        List<ReviewScriptVersionEntity> versions = versionMapper.selectByProject(context.tenantId(), projectId);
        return new ReviewVersionHistoryResponse(
            toProjectSummary(project, context.tenantId()),
            toVersionResponse(version),
            versions.stream().map(this::toVersionResponse).toList(),
            buildVersionDiff(version, versions),
            buildRoundHistory(context.tenantId(), projectId, versionId)
        );
    }

    public void executeTask(Long taskId) {
        try {
            executeTask(taskId, null);
        } catch (RuntimeException ignored) {
        }
    }

    ReviewExecutionOutcome executeTask(Long taskId, AiExecutionContext executionContext) {
        ReviewTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || "CANCELED".equals(task.getStatus())) {
            return ReviewExecutionOutcome.empty();
        }
        if ("COMPLETED".equals(task.getStatus())) {
            if (reviewDeepAgentCoordinator.canRecoverCommittedAggregation(task)) {
                ReviewDeepAgentCoordinator.Execution recovered =
                    reviewDeepAgentCoordinator.recoverCommittedAggregation(task);
                return new ReviewExecutionOutcome(recovered.modelCalls());
            }
            return ReviewExecutionOutcome.empty();
        }
        try {
            requireVersion(task.getTenantId(), task.getProjectId(), task.getScriptVersionId());
            requireProject(task.getTenantId(), task.getProjectId());
            LocalDateTime now = LocalDateTime.now();
            task.setStatus("RUNNING");
            task.setCurrentStage("GLOBAL_INDEX");
            task.setCurrentAction("正在准备审核报告");
            task.setOverallProgress(10);
            task.setUpdatedAt(now);
            taskMapper.updateById(task);

            if ("CANCELED".equals(taskMapper.selectById(taskId).getStatus())) {
                return ReviewExecutionOutcome.empty();
            }
            if ("QUICK".equals(task.getReviewMode())) {
                ReviewQuickAgentAdapter.Execution agent = reviewQuickAgentAdapter.execute(
                    task, executionContext, resolveDefaultTextModelId(task.getTenantId()));
                return new ReviewExecutionOutcome(agent.modelCalls());
            }
            if ("DEEP".equals(task.getReviewMode())) {
                ReviewDeepAgentCoordinator.Execution agent = reviewDeepAgentCoordinator.execute(
                    task, executionContext, resolveDefaultTextModelId(task.getTenantId()));
                return new ReviewExecutionOutcome(agent.modelCalls());
            }
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "不支持的审核模式。");
        } catch (Exception exception) {
            ReviewTaskEntity failed = taskMapper.selectById(taskId);
            if (failed != null && !List.of("COMPLETED", "CANCELED").contains(failed.getStatus())) {
                failed.setStatus("FAILED");
                failed.setCurrentStage("FAILED");
                failed.setCurrentAction("审核失败");
                if (failed.getErrorCode() == null) {
                    failed.setErrorCode(exception instanceof BusinessException business
                        ? business.getErrorCode().name() : ErrorCode.WORKFLOW_AGENT_TOOL_INVALID.name());
                }
                failed.setErrorMessage(trimError(exception.getMessage()));
                failed.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(failed);
            }
            if (exception instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(exception);
        }
    }

    public Resource downloadExport(Long tenantId, String fileName) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        String safeFileName = blankToNull(fileName);
        if (safeFileName == null || safeFileName.contains("/") || safeFileName.contains("\\")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出文件名不合法。");
        }
        ReviewExportRecordEntity record = exportMapper.selectOne(new LambdaQueryWrapper<ReviewExportRecordEntity>()
            .eq(ReviewExportRecordEntity::getTenantId, context.tenantId())
            .eq(ReviewExportRecordEntity::getFileName, safeFileName)
            .eq(ReviewExportRecordEntity::getExportStatus, "SUCCEEDED")
            .last("limit 1"));
        if (record == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出文件不存在。");
        }
        requireAccessibleProject(context, record.getProjectId(), "PROJECT:VIEW");
        Path target = exportRoot.resolve(safeFileName).normalize();
        if (!target.startsWith(exportRoot) || !Files.exists(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出文件不存在。");
        }
        return new FileSystemResource(target);
    }

    private ReviewTaskEntity requireTask(Long tenantId, Long taskId) {
        ReviewTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getId, taskId));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审核任务不存在。");
        }
        return task;
    }

    private ReviewProjectEntity requireProject(Long tenantId, Long projectId) {
        ReviewProjectEntity project = projectMapper.selectByTenantAndId(tenantId, projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审稿项目不存在。");
        }
        return project;
    }

    private ReviewProjectEntity requireAccessibleProject(
        TenantContext context,
        Long reviewProjectId,
        String permissionCode
    ) {
        ReviewProjectEntity project = requireProject(context.tenantId(), reviewProjectId);
        reviewAccessGuard.require(context, project, permissionCode);
        return project;
    }

    @Transactional
    public ReviewProjectDetailResponse bindProject(
        Long tenantId,
        Long reviewProjectId,
        BindReviewProjectRequest request
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewProjectEntity project = requireProject(context.tenantId(), reviewProjectId);
        reviewAccessGuard.requireBinding(context, project, request.mainProjectId());
        project.setMainProjectId(request.mainProjectId());
        project.setUpdatedAt(LocalDateTime.now());
        projectMapper.updateById(project);
        return detailProject(tenantId, reviewProjectId);
    }

    private ReviewScriptVersionEntity requireVersion(Long tenantId, Long projectId, Long versionId) {
        ReviewScriptVersionEntity version = versionMapper.selectById(versionId);
        if (version == null || !tenantId.equals(version.getTenantId()) || !projectId.equals(version.getProjectId())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审稿版本不存在。");
        }
        return version;
    }

    private ReviewScriptVersionEntity requireCurrentVersion(ReviewProjectEntity project) {
        if (project.getCurrentVersionId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目没有可审核的版本。");
        }
        return requireVersion(project.getTenantId(), project.getId(), project.getCurrentVersionId());
    }

    private ReviewTaskResponse toTaskResponse(ReviewTaskEntity task, boolean includeBoundVersion) {
        ReviewFanoutProgressResponse fanout = null;
        ReviewObservabilityResponse observability = null;
        if (task.getFanoutSnapshotId() != null) {
            ReviewFanoutSnapshotEntity snapshot = fanoutSnapshotMapper.selectById(task.getFanoutSnapshotId());
            if (snapshot != null) {
                List<ReviewFanoutUnitEntity> fanoutUnits = fanoutUnitMapper.selectOrdered(snapshot.getId());
                Map<Long, ReviewCacheUsageResponse> cacheUsageByRun = reviewObservabilityRepository.cacheUsageByRunIds(fanoutUnits.stream()
                        .map(ReviewFanoutUnitEntity::getChildRunId).filter(Objects::nonNull).toList());
                fanout = new ReviewFanoutProgressResponse(snapshot.getStatus(), snapshot.getTotalUnits(),
                    snapshot.getCompletedUnits(), snapshot.getFailedUnits(), snapshot.getCurrentUnitId(),
                    snapshot.getAggregationStatus(), fanoutUnits.stream()
                        .map(unit -> new ReviewUnitProgressResponse(unit.getId(), unit.getUnitNo(), unit.getUnitKey(),
                            unit.getStageType(), unit.getDimension(), unit.getStatus(), unit.getChildRunId(),
                            unit.getAttemptNo(), unit.getReportSaved(), unit.getErrorCode(), unit.getErrorMessage(),
                            unit.getChildRunId() == null ? null : cacheUsageByRun.get(unit.getChildRunId())))
                        .toList());
                List<Long> runIds = new ArrayList<>(fanoutUnits.stream()
                    .map(ReviewFanoutUnitEntity::getChildRunId).filter(Objects::nonNull).toList());
                if (task.getAggregationRunId() != null) runIds.add(task.getAggregationRunId());
                observability = reviewObservabilityRepository.load(runIds);
            }
        }
        return new ReviewTaskResponse(
            task.getId(),
            task.getProjectId(),
            task.getScriptVersionId(),
            task.getRoundNo(),
            task.getReviewMode(),
            deserializeStringList(task.getSelectedDimensionsJson()),
            task.getReviewScopeType(),
            deserializeObject(task.getReviewScopeJson()),
            task.getReportMarkdown(),
            task.getStatus(),
            task.getCurrentStage(),
            task.getOverallProgress(),
            task.getCurrentAction(),
            task.getErrorCode(),
            task.getErrorMessage(),
            task.getWorkflowAgentCode(),
            task.getWorkflowAgentRevision(),
            task.getWorkflowAgentRunId(),
            task.getWorkflowPhase(),
            task.getWorkflowAttemptNo(),
            task.getFanoutSnapshotId(),
            task.getAggregationRunId(),
            task.getRetryKind(),
            task.getStale(),
            fanout,
            observability,
            task.getCompletedAt(),
            task.getCanceledAt(),
            includeBoundVersion ? toVersionResponse(versionMapper.selectById(task.getScriptVersionId())) : null
        );
    }

    private ReviewVersionMetadataResponse toVersionMetadataResponse(ReviewScriptVersionEntity version) {
        return new ReviewVersionMetadataResponse(version.getId(), version.getProjectId(), version.getVersionNo(),
            version.getSourceType(), version.getFileName(), version.getCreatedAt());
    }

    private ReviewHistoryTaskResponse toHistoryTaskResponse(ReviewTaskEntity task) {
        return new ReviewHistoryTaskResponse(task.getId(), task.getScriptVersionId(), task.getRoundNo(),
            task.getReviewMode(), deserializeStringList(task.getSelectedDimensionsJson()), task.getReviewScopeType(),
            task.getReportMarkdown(),
            task.getStatus(), task.getOverallProgress(), task.getCreatedBy(),
            task.getCreatedAt(), task.getCompletedAt(), task.getCanceledAt(), task.getErrorMessage());
    }

    private List<ReviewVersionDiffResponse> buildVersionDiff(ReviewScriptVersionEntity selectedVersion, List<ReviewScriptVersionEntity> versions) {
        if (selectedVersion == null || versions == null || versions.isEmpty()) {
            return List.of();
        }
        ReviewScriptVersionEntity previous = versions.stream()
            .filter(version -> !Objects.equals(version.getId(), selectedVersion.getId()))
            .filter(version -> version.getVersionNo() != null && selectedVersion.getVersionNo() != null && version.getVersionNo() < selectedVersion.getVersionNo())
            .findFirst()
            .orElse(null);
        if (previous == null) {
            return List.of();
        }
        List<String> beforeLines = splitLines(previous.getContent());
        List<String> afterLines = splitLines(selectedVersion.getContent());
        int max = Math.max(beforeLines.size(), afterLines.size());
        List<ReviewVersionDiffLineResponse> lines = new ArrayList<>();
        int added = 0;
        int removed = 0;
        for (int index = 0; index < max; index++) {
            String before = index < beforeLines.size() ? beforeLines.get(index) : null;
            String after = index < afterLines.size() ? afterLines.get(index) : null;
            String type;
            if (before == null) {
                type = "ADDED";
                added++;
            } else if (after == null) {
                type = "REMOVED";
                removed++;
            } else if (Objects.equals(before, after)) {
                type = "UNCHANGED";
            } else {
                type = "CHANGED";
                added++;
                removed++;
            }
            lines.add(new ReviewVersionDiffLineResponse(type, index + 1, before, after));
        }
        return List.of(new ReviewVersionDiffResponse(previous.getId(), selectedVersion.getId(), added, removed, lines));
    }

    private List<ReviewRoundHistoryResponse> buildRoundHistory(Long tenantId, Long projectId, Long versionId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .eq(ReviewTaskEntity::getScriptVersionId, versionId)
            .orderByAsc(ReviewTaskEntity::getRoundNo)).stream()
            .map(task -> new ReviewRoundHistoryResponse(task.getId(), task.getRoundNo(),
                task.getStatus(), task.getReviewMode(), task.getCompletedAt()))
            .toList();
    }

    private ReviewProjectSummaryResponse toProjectSummary(ReviewProjectEntity project, Long tenantId) {
        List<ReviewScriptVersionEntity> versions = versionMapper.selectByProject(tenantId, project.getId());
        List<ReviewTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, project.getId())
            .orderByDesc(ReviewTaskEntity::getRoundNo));
        return toProjectSummary(project, versions.size(), tasks.isEmpty() ? null : tasks.get(0));
    }

    private ReviewProjectSummaryResponse toProjectSummary(
        ReviewProjectEntity project,
        int versionCount,
        ReviewTaskEntity latestTask
    ) {
        String reviewState = "NOT_REVIEWED";
        String actionLabel = "发起审核";
        if (latestTask != null) {
            if (List.of("PENDING", "RUNNING").contains(latestTask.getStatus())) {
                reviewState = "RUNNING";
                actionLabel = "查看进度";
            } else if ("COMPLETED".equals(latestTask.getStatus())
                && latestTask.getReportMarkdown() != null && !latestTask.getReportMarkdown().isBlank()) {
                reviewState = "COMPLETED";
                actionLabel = "查看报告";
            } else {
                actionLabel = "重试审核";
            }
        }
        return new ReviewProjectSummaryResponse(
            project.getId(),
            project.getMainProjectId(),
            project.getMainProjectId() == null ? "PERSONAL_DRAFT" : "PROJECT",
            project.getName(),
            project.getSourceFileName(),
            project.getSourceType(),
            project.getCurrentVersionId(),
            project.getLastTaskId(),
            project.getStatus(),
            versionCount,
            latestTask == null ? 0 : latestTask.getRoundNo(),
            reviewState,
            actionLabel,
            project.getCreatedAt(),
            project.getUpdatedAt()
        );
    }

    private ReviewVersionResponse toVersionResponse(ReviewScriptVersionEntity version) {
        return new ReviewVersionResponse(
            version.getId(),
            version.getProjectId(),
            version.getVersionNo(),
            version.getSourceType(),
            version.getFileName(),
            version.getContent(),
            version.getCreatedAt()
        );
    }

    private List<String> splitLines(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        return content.lines().map(line -> line == null ? "" : line).toList();
    }

    private ReviewExportRecordResponse toExportRecordResponse(ReviewExportRecordEntity record) {
        return new ReviewExportRecordResponse(
            record.getId(),
            record.getProjectId(),
            record.getVersionId(),
            record.getTaskId(),
            record.getExportType(),
            record.getExportStatus(),
            record.getFileName(),
            record.getFileSize(),
            record.getDownloadUrl(),
            record.getErrorMessage(),
            record.getCreatedAt()
        );
    }

    private ReviewTaskEntity createTaskRow(
        TenantContext context,
        Long projectId,
        Long versionId,
        Integer roundNo,
        String reviewMode,
        List<String> dimensions,
        String scopeType,
        String scopeJson,
        LocalDateTime now
    ) {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setTenantId(context.tenantId());
        task.setProjectId(projectId);
        task.setScriptVersionId(versionId);
        task.setRoundNo(roundNo);
        task.setReviewMode(reviewMode);
        task.setSelectedDimensionsJson(serialize(dimensions));
        task.setReviewScopeType(scopeType);
        task.setReviewScopeJson(scopeJson);
        task.setStatus("PENDING");
        task.setCurrentStage("GLOBAL_INDEX");
        task.setOverallProgress(0);
        task.setCurrentAction("等待开始审核");
        task.setCreatedBy(context.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        return task;
    }

    public List<ReviewProjectListSummaryResponse> listProjectSummaries(Long tenantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        return visibleProjectRows(context).stream().map(this::toProjectListSummary).toList();
    }

    public List<ReviewProjectMetricsResponse> listProjectMetrics(Long tenantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        List<ReviewProjectListRow> projects = visibleProjectRows(context);
        if (projects.isEmpty()) return List.of();
        List<Long> projectIds = projects.stream().map(ReviewProjectListRow::getId).toList();
        Map<Long, Integer> versionCounts = versionMapper.selectCountsByProjects(context.tenantId(), projectIds).stream()
            .collect(Collectors.toMap(ReviewProjectCountRow::getProjectId, ReviewProjectCountRow::getVersionCount));
        Map<Long, ReviewProjectLatestTaskRow> latestTasks = taskMapper.selectLatestRowsByProjects(
            context.tenantId(), projectIds).stream()
            .collect(Collectors.toMap(ReviewProjectLatestTaskRow::getProjectId, task -> task));
        return projects.stream().map(project -> {
            ReviewProjectLatestTaskRow task = latestTasks.get(project.getId());
            return toProjectMetrics(project.getId(), versionCounts.getOrDefault(project.getId(), 0), task);
        }).toList();
    }

    private List<ReviewProjectListRow> visibleProjectRows(TenantContext context) {
        boolean tenantWide = permissionService.permissionCodes(context).contains("PROJECT:VIEW_ALL");
        return projectMapper.selectVisibleListRows(context.tenantId(), context.userId(), tenantWide);
    }

    private ReviewProjectListSummaryResponse toProjectListSummary(ReviewProjectListRow project) {
        return new ReviewProjectListSummaryResponse(
            project.getId(), project.getMainProjectId(),
            project.getMainProjectId() == null ? "PERSONAL_DRAFT" : "PROJECT",
            project.getName(), project.getSourceFileName(), project.getSourceType(),
            project.getCurrentVersionId(), project.getStatus(), project.getCreatedAt(), project.getUpdatedAt()
        );
    }

    private ReviewProjectMetricsResponse toProjectMetrics(
        Long projectId,
        int versionCount,
        ReviewProjectLatestTaskRow task
    ) {
        if (task == null) {
            return new ReviewProjectMetricsResponse(projectId, versionCount, 0,
                "NOT_REVIEWED", "发起审核");
        }
        String reviewState = "NOT_REVIEWED";
        String actionLabel = "重试审核";
        if (List.of("PENDING", "RUNNING").contains(task.getStatus())) {
            reviewState = "RUNNING";
            actionLabel = "查看进度";
        } else if ("COMPLETED".equals(task.getStatus()) && Boolean.TRUE.equals(task.getHasReportMarkdown())) {
            reviewState = "COMPLETED";
            actionLabel = "查看报告";
        }
        return new ReviewProjectMetricsResponse(projectId, versionCount, task.getRoundNo(),
            reviewState, actionLabel);
    }

    private ReviewScriptVersionEntity createVersion(
        TenantContext context,
        Long projectId,
        Integer versionNo,
        String sourceType,
        String fileName,
        String content,
        LocalDateTime now
    ) {
        ReviewScriptVersionEntity version = new ReviewScriptVersionEntity();
        version.setTenantId(context.tenantId());
        version.setProjectId(projectId);
        version.setVersionNo(versionNo);
        version.setSourceType(sourceType);
        version.setFileName(fileName);
        version.setContent(content);
        version.setCreatedBy(context.userId());
        version.setCreatedAt(now);
        version.setUpdatedAt(now);
        versionMapper.insert(version);
        return version;
    }

    private int nextVersionNo(Long tenantId, Long projectId) {
        List<ReviewScriptVersionEntity> versions = versionMapper.selectByProject(tenantId, projectId);
        return versions.isEmpty() ? 1 : versions.get(0).getVersionNo() + 1;
    }

    private Integer nextRoundNo(Long tenantId, Long projectId) {
        List<ReviewTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .orderByDesc(ReviewTaskEntity::getRoundNo)
            .last("limit 1"));
        return tasks.isEmpty() ? 1 : tasks.get(0).getRoundNo() + 1;
    }

    private String buildIdempotencyKey(
        Long projectId,
        Long versionId,
        String reviewMode,
        String scopeType,
        List<String> dimensions,
        String scopeJson
    ) {
        return "%d:%d:%s:%s:%s:%s".formatted(projectId, versionId, reviewMode, scopeType, normalize(String.join(",", dimensions)), normalize(scopeJson));
    }

    private String normalizeMode(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!REVIEW_MODES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择审核模式。");
        }
        return normalized;
    }

    private String normalizeScopeType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!REVIEW_SCOPE_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择审核范围。");
        }
        return normalized;
    }

    private List<String> normalizeDimensions(List<String> dimensions) {
        if (dimensions == null) {
            return List.of();
        }
        List<String> normalized = dimensions.stream()
            .map(item -> item == null ? "" : item.trim())
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
        return ReviewDimension.parseAll(normalized).stream().map(ReviewDimension::label).toList();
    }

    private String resolveImportedContent(MultipartFile file, String content) {
        if (file != null && !file.isEmpty()) {
            return scriptContentParser.parse(file);
        }
        return content == null ? "" : content.trim();
    }

    private String inferProjectName(MultipartFile file, String content) {
        if (file != null && blankToNull(file.getOriginalFilename()) != null) {
            return file.getOriginalFilename().trim();
        }
        String firstLine = content == null ? "" : content.lines().findFirst().orElse("独立剧本");
        return firstLine.length() > 60 ? firstLine.substring(0, 60) : firstLine;
    }

    private String resolveSourceType(MultipartFile file) {
        if (file == null || blankToNull(file.getOriginalFilename()) == null) {
            return "TEXT";
        }
        String name = file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        if (name.endsWith(".docx") || name.endsWith(".doc")) {
            return "WORD";
        }
        if (name.endsWith(".md") || name.endsWith(".markdown")) {
            return "MARKDOWN";
        }
        return "TEXT";
    }

    private List<ReviewTaskEntity> listTasksForProject(Long tenantId, Long projectId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .orderByDesc(ReviewTaskEntity::getRoundNo)
            .orderByDesc(ReviewTaskEntity::getCreatedAt));
    }

    private Long resolveDefaultTextModelId(Long tenantId) {
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, "TEXT")
            .eq(AiModelEntity::getStatus, "ENABLED")
            .eq(AiModelEntity::getIsDefault, true)
            .orderByDesc(AiModelEntity::getSort)
            .last("limit 1"));
        if (model != null && providerAvailable(model)) {
            return model.getId();
        }
        model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, "TEXT")
            .eq(AiModelEntity::getStatus, "ENABLED")
            .orderByDesc(AiModelEntity::getSort)
            .last("limit 1"));
        if (model != null && providerAvailable(model)) {
            return model.getId();
        }
        throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "暂无可用的文本模型。");
    }

    private boolean providerAvailable(AiModelEntity model) {
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        return provider != null && "ENABLED".equals(provider.getStatus());
    }

    private String buildExportFileName(String projectName, Integer versionNo, String exportType) {
        String safeName = projectName == null ? "review" : projectName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String extension = "md";
        return "%s_v%d_%s.%s".formatted(
            safeName,
            versionNo == null ? 1 : versionNo,
            exportType.toLowerCase(Locale.ROOT),
            extension
        );
    }

    private List<String> deserializeStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception exception) {
            return List.of();
        }
    }

    private Map<String, Object> deserializeObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        return deserializeObject(json);
    }

    private List<Integer> extractIntegerList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Number number) {
                result.add(number.intValue());
            } else if (item != null) {
                try {
                    result.add(Integer.parseInt(item.toString()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private String extractJson(String rawJson) {
        if (rawJson == null) {
            return "{}";
        }
        String trimmed = rawJson.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (node == null || !node.hasNonNull(field)) {
            return fallback;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String trimError(String value) {
        if (value == null || value.isBlank()) {
            return "审核执行失败。";
        }
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private String resolveSourceType(MultipartFile file, String content) {
        return file == null || blankToNull(file.getOriginalFilename()) == null
            ? "TEXT"
            : resolveSourceType(file);
    }

}
