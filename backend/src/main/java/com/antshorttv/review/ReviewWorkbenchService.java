package com.antshorttv.review;

import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
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
import com.antshorttv.points.TeamPointService;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.script.ScriptEpisodeParser;
import com.antshorttv.script.ScriptEpisodeResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
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
    private final ReviewProjectMapper projectMapper;
    private final ReviewScriptVersionMapper versionMapper;
    private final ReviewTaskMapper taskMapper;
    private final ReviewIssueMapper issueMapper;
    private final ReviewIssueHitMapper hitMapper;
    private final ReviewIssueEventMapper eventMapper;
    private final ReviewBatchRepairMapper repairMapper;
    private final ReviewExportRecordMapper exportMapper;
    private final AiInvocationService aiInvocationService;
    private final TeamPointService teamPointService;
    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final ObjectMapper objectMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper executionResponseMapper;
    private final AiPointReservationMapper pointReservationMapper;
    private final AiPointSettlementService pointSettlementService;
    private final Path exportRoot;

    public ReviewWorkbenchService(
        TenantContextResolver tenantContextResolver,
        ReviewAccessGuard reviewAccessGuard,
        ReviewProjectMapper projectMapper,
        ReviewScriptVersionMapper versionMapper,
        ReviewTaskMapper taskMapper,
        ReviewIssueMapper issueMapper,
        ReviewIssueHitMapper hitMapper,
        ReviewIssueEventMapper eventMapper,
        ReviewBatchRepairMapper repairMapper,
        ReviewExportRecordMapper exportMapper,
        AiInvocationService aiInvocationService,
        TeamPointService teamPointService,
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        ObjectMapper objectMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper executionResponseMapper,
        AiPointReservationMapper pointReservationMapper,
        AiPointSettlementService pointSettlementService,
        @Value("${review.export-root:storage/review-exports}") String exportRoot
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.reviewAccessGuard = reviewAccessGuard;
        this.projectMapper = projectMapper;
        this.versionMapper = versionMapper;
        this.taskMapper = taskMapper;
        this.issueMapper = issueMapper;
        this.hitMapper = hitMapper;
        this.eventMapper = eventMapper;
        this.repairMapper = repairMapper;
        this.exportMapper = exportMapper;
        this.aiInvocationService = aiInvocationService;
        this.teamPointService = teamPointService;
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.objectMapper = objectMapper;
        this.executionService = executionService;
        this.executionResponseMapper = executionResponseMapper;
        this.pointReservationMapper = pointReservationMapper;
        this.pointSettlementService = pointSettlementService;
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public List<ReviewProjectSummaryResponse> listProjects(Long tenantId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        return projectMapper.selectActive(context.tenantId()).stream()
            .filter(project -> reviewAccessGuard.canView(context, project))
            .map(project -> toProjectSummary(project, context.tenantId()))
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
        task.setStatus("PENDING");
        task.setCurrentStage("GLOBAL_INDEX");
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
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "AI_SERVICE:USE");
        if (!"FAILED".equals(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "只有失败任务可以重试。");
        }
        LocalDateTime now = LocalDateTime.now();
        task.setStatus("PENDING");
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
        task.setIdempotencyKey(idempotencyKey);
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return toTaskResponse(task, true);
    }

    @Transactional
    public ReviewIssueResponse markIssueResolved(Long tenantId, Long issueId, MarkReviewIssueResolvedRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewIssueEntity issue = requireIssue(context.tenantId(), issueId);
        requireAccessibleProject(context, issue.getProjectId(), "SCRIPT:EDIT");
        if (Boolean.TRUE.equals(issue.getManuallyResolved())) {
            return toIssueResponse(issue);
        }
        LocalDateTime now = LocalDateTime.now();
        issue.setManuallyResolved(true);
        issue.setManuallyResolvedAt(now);
        issue.setManuallyResolvedBy(context.userId());
        issue.setUpdatedAt(now);
        issueMapper.updateById(issue);

        ReviewIssueEventEntity event = new ReviewIssueEventEntity();
        event.setTenantId(context.tenantId());
        event.setProjectId(issue.getProjectId());
        event.setTaskId(issue.getTaskId());
        event.setIssueId(issue.getId());
        event.setEventType("MANUAL_RESOLVE");
        event.setPreviousStatus(issue.getStatus());
        event.setNewStatus(issue.getStatus());
        Map<String, Object> resolvePayload = new LinkedHashMap<>();
        resolvePayload.put("note", request == null ? null : request.note());
        event.setPayloadJson(serialize(resolvePayload));
        event.setCreatedBy(context.userId());
        event.setCreatedAt(now);
        eventMapper.insert(event);
        return toIssueResponse(issue);
    }

    @Transactional
    public ReviewTaskResponse batchRepair(Long tenantId, Long taskId, BatchRepairReviewRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ReviewTaskEntity task = requireTask(context.tenantId(), taskId);
        requireAccessibleProject(context, task.getProjectId(), "SCRIPT:EDIT");
        ReviewIssueEntity targetIssue = null;
        if (request.selectedHitIds() != null && !request.selectedHitIds().isEmpty()) {
            targetIssue = issueMapper.selectById(request.selectedHitIds().get(0));
        }
        String actionType = normalizeActionType(request.actionType());
        ReviewScriptVersionEntity currentVersion = requireVersion(context.tenantId(), task.getProjectId(), task.getScriptVersionId());
        String revisedContent = applyBatchRepairToContent(currentVersion.getContent(), request, task);
        ReviewVersionResponse newVersion = saveVersion(
            tenantId,
            task.getProjectId(),
            new SaveReviewVersionRequest(revisedContent, currentVersion.getFileName(), "BATCH_REPAIR")
        );
        ReviewBatchRepairEntity repair = new ReviewBatchRepairEntity();
        repair.setTenantId(context.tenantId());
        repair.setProjectId(task.getProjectId());
        repair.setTaskId(task.getId());
        repair.setIssueId(targetIssue == null ? null : targetIssue.getId());
        repair.setActionType(actionType);
        repair.setReplacementFrom(blankToNull(request.replacementFrom()));
        repair.setReplacementTo(blankToNull(request.replacementTo()));
        repair.setInsertionText(blankToNull(request.insertionText()));
        repair.setDeletionText(blankToNull(request.deletionText()));
        repair.setSelectedHitIds(serialize(request.selectedHitIds() == null ? List.of() : request.selectedHitIds()));
        repair.setStatus("APPLIED");
        repair.setCreatedBy(context.userId());
        repair.setCreatedAt(LocalDateTime.now());
        repair.setUpdatedAt(LocalDateTime.now());
        repair.setAppliedAt(LocalDateTime.now());
        repairMapper.insert(repair);
        return detailTask(tenantId, task.getId());
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
        ReviewTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, context.tenantId())
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .eq(ReviewTaskEntity::getScriptVersionId, version.getId())
            .orderByDesc(ReviewTaskEntity::getRoundNo)
            .last("limit 1"));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前版本暂无审核记录。");
        }

        ReviewTaskResponse response = toTaskResponse(task, true);
        String exportType = normalizeExportType(request.exportType());
        String fileName = buildExportFileName(project.getName(), version.getVersionNo(), exportType);
        String text = buildExportDocument(project, version, response);
        try {
            Files.createDirectories(exportRoot);
            Path target = exportRoot.resolve(fileName).normalize();
            if (!target.startsWith(exportRoot)) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "导出路径不合法。");
            }
            writeExportFile(target, exportType, project, version, response, text);
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
            buildRoundHistory(context.tenantId(), projectId, versionId),
            buildIssueMappings(context.tenantId(), projectId, versionId)
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
        if (task == null || List.of("COMPLETED", "CANCELED").contains(task.getStatus())) {
            return ReviewExecutionOutcome.empty();
        }
        try {
            ReviewScriptVersionEntity version = requireVersion(task.getTenantId(), task.getProjectId(), task.getScriptVersionId());
            ReviewProjectEntity project = requireProject(task.getTenantId(), task.getProjectId());
            LocalDateTime now = LocalDateTime.now();
            task.setStatus("RUNNING");
            task.setCurrentStage("GLOBAL_INDEX");
            task.setCurrentAction("正在构建全局审核索引");
            task.setOverallProgress(10);
            task.setUpdatedAt(now);
            taskMapper.updateById(task);

            String scopedContent = scopeContent(version.getContent(), task);
            Map<String, Object> globalIndex = buildGlobalIndex(scopedContent, task);
            task.setGlobalIndexJson(serialize(globalIndex));
            task.setCurrentStage("AI_REVIEW");
            task.setCurrentAction("正在生成审核问题");
            task.setOverallProgress(35);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            if ("CANCELED".equals(taskMapper.selectById(taskId).getStatus())) {
                return ReviewExecutionOutcome.empty();
            }
            ReviewInvocationOutcome review = invokeAiReview(
                task, version, globalIndex, scopedContent, executionContext
            );
            ReviewAiResult aiResult = review.result();
            task.setResultJson(aiResult.rawJson());
            task.setCurrentStage("MATCHING");
            task.setCurrentAction("正在匹配历史问题并生成轮次结果");
            task.setOverallProgress(75);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            persistRoundIssues(task, aiResult, project, version, scopedContent);
            if (!"CANCELED".equals(task.getStatus())) {
                task.setStatus("COMPLETED");
                task.setCurrentStage(null);
                task.setCurrentAction("审核已完成");
                task.setOverallProgress(100);
                task.setCompletedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
            }
            return new ReviewExecutionOutcome(review.invocation());
        } catch (Exception exception) {
            ReviewTaskEntity failed = taskMapper.selectById(taskId);
            if (failed != null && !"CANCELED".equals(failed.getStatus())) {
                failed.setStatus("FAILED");
                failed.setCurrentStage("FAILED");
                failed.setCurrentAction("审核失败");
                failed.setErrorCode(exception.getClass().getSimpleName());
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

    private ReviewInvocationOutcome invokeAiReview(
        ReviewTaskEntity task,
        ReviewScriptVersionEntity version,
        Map<String, Object> globalIndex,
        String scopedContent,
        AiExecutionContext executionContext
    ) {
        if (executionContext == null) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "AI 调用必须先创建执行和积分预占。");
        }
        Long modelId = resolveDefaultTextModelId(task.getTenantId());
        List<ReviewIssueEntity> previousIssues = latestIssuesBefore(task.getTenantId(), task.getProjectId(), task.getRoundNo());
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("scriptTitle", version.getFileName() == null ? "独立剧本" : version.getFileName());
        variables.put("scriptContent", scopedContent);
        variables.put("reviewMode", task.getReviewMode());
        variables.put("selectedDimensions", deserializeStringList(task.getSelectedDimensionsJson()));
        variables.put("reviewScope", deserializeObject(task.getReviewScopeJson()));
        variables.put("previousIssues", previousIssues.stream().map(this::issueBrief).toList());
        variables.put("globalIndex", globalIndex);
        AiInvocationRequest.Builder request = AiInvocationRequest.text()
                .tenantId(task.getTenantId())
                .userId(task.getCreatedBy())
                .projectId(task.getProjectId())
                .taskId(task.getId())
                .modelId(modelId)
                .scene(AiBusinessScene.SCRIPT_REVIEW)
                .promptTemplateId(AiBusinessScene.SCRIPT_REVIEW.agentCode())
                .templateVariables(variables)
                .requestSummary("script-review:round-%d".formatted(task.getRoundNo()))
                .traceId("script-review-%d".formatted(task.getId()));
        if (executionContext != null) {
            request.executionId(executionContext.task().id)
                .attemptId(executionContext.claim().attemptId())
                .executionVersion(executionContext.task().executionVersion)
                .phase("AI_REVIEW")
                .idempotencyKey("execution:%d:v%d:AI_REVIEW".formatted(
                    executionContext.task().id,
                    executionContext.task().executionVersion
                ));
        }
        AiInvocationResult<com.antshorttv.ai.AiTextResponse> invocation = aiInvocationService.invokeText(request.build());
        try {
            return new ReviewInvocationOutcome(parseReviewResult(invocation.content()), invocation);
        } catch (RuntimeException exception) {
            aiInvocationService.markBusinessFailure(
                invocation.aiCallLogId(),
                exception instanceof BusinessException businessException ? businessException.getErrorCode() : ErrorCode.AI_RESPONSE_INVALID,
                exception.getMessage()
            );
            throw new ReviewInvocationException(exception, invocation);
        }
    }

    private void persistRoundIssues(
        ReviewTaskEntity task,
        ReviewAiResult aiResult,
        ReviewProjectEntity project,
        ReviewScriptVersionEntity version,
        String scopedContent
    ) {
        List<ReviewIssueEntity> previousIssues = latestIssuesBefore(task.getTenantId(), task.getProjectId(), task.getRoundNo());
        Map<String, ReviewIssueEntity> previousBySignature = previousIssues.stream()
            .collect(Collectors.toMap(this::issueSignature, issue -> issue, (left, right) -> left, LinkedHashMap::new));
        Set<String> matchedPrevious = new LinkedHashSet<>();
        LocalDateTime now = LocalDateTime.now();
        int nextIssueIndex = 1;

        for (ReviewDraftIssue draft : aiResult.issues()) {
            ReviewIssueEntity issue = new ReviewIssueEntity();
            issue.setTenantId(task.getTenantId());
            issue.setProjectId(task.getProjectId());
            issue.setTaskId(task.getId());
            issue.setScriptVersionId(version.getId());
            issue.setRoundNo(task.getRoundNo());
            issue.setIssueNo("R%d-%02d".formatted(task.getRoundNo(), nextIssueIndex++));
            issue.setDimension(draft.dimension());
            issue.setSeverity(draft.severity());
            issue.setTitle(draft.title());
            issue.setPositionJson(serialize(draft.position()));
            issue.setExcerpt(draft.excerpt());
            issue.setProblem(draft.problem());
            issue.setEvidenceJson(serialize(draft.evidence()));
            issue.setSuggestion(draft.suggestion());
            issue.setManuallyResolved(false);
            issue.setCreatedAt(now);
            issue.setUpdatedAt(now);
            ReviewIssueEntity previous = previousBySignature.get(draft.signature());
            if (previous != null) {
                matchedPrevious.add(previous.getIssueNo());
                issue.setRelatedIssueNo(previous.getIssueNo());
                issue.setStatus(resolveRoundStatus(previous, draft));
            } else {
                issue.setStatus("new");
            }
            issueMapper.insert(issue);
            persistHits(task, issue, draft.hits());
            persistIssueEvent(task, issue, previous == null ? null : previous.getStatus(), issue.getStatus(), "ROUND_RESULT", draft);
        }

        for (ReviewIssueEntity previous : previousIssues) {
            if (matchedPrevious.contains(previous.getIssueNo())) {
                continue;
            }
            ReviewIssueEntity fixed = new ReviewIssueEntity();
            fixed.setTenantId(task.getTenantId());
            fixed.setProjectId(task.getProjectId());
            fixed.setTaskId(task.getId());
            fixed.setScriptVersionId(version.getId());
            fixed.setRoundNo(task.getRoundNo());
            fixed.setIssueNo("R%d-%02d".formatted(task.getRoundNo(), nextIssueIndex++));
            fixed.setDimension(previous.getDimension());
            fixed.setSeverity(previous.getSeverity());
            fixed.setTitle(previous.getTitle());
            fixed.setPositionJson(previous.getPositionJson());
            fixed.setExcerpt(previous.getExcerpt());
            fixed.setProblem(previous.getProblem());
            fixed.setEvidenceJson(previous.getEvidenceJson());
            fixed.setSuggestion(previous.getSuggestion());
            fixed.setStatus("fixed");
            fixed.setRelatedIssueNo(previous.getIssueNo());
            fixed.setManuallyResolved(false);
            fixed.setCreatedAt(now);
            fixed.setUpdatedAt(now);
            issueMapper.insert(fixed);
            persistIssueEvent(task, fixed, previous.getStatus(), "fixed", "ROUND_RESULT", Map.of("fixedFrom", previous.getIssueNo()));
        }
    }

    private void persistHits(ReviewTaskEntity task, ReviewIssueEntity issue, List<ReviewDraftHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        int index = 1;
        for (ReviewDraftHit draftHit : hits) {
            ReviewIssueHitEntity hit = new ReviewIssueHitEntity();
            hit.setTenantId(task.getTenantId());
            hit.setProjectId(task.getProjectId());
            hit.setTaskId(task.getId());
            hit.setIssueId(issue.getId());
            hit.setHitNo(index++);
            hit.setEpisodeNo(draftHit.episodeNo());
            hit.setSceneNo(draftHit.sceneNo());
            hit.setShotNo(draftHit.shotNo());
            hit.setLineNo(draftHit.lineNo());
            hit.setAnchorLabel(draftHit.anchorLabel());
            hit.setExcerpt(draftHit.excerpt());
            hit.setEntityName(draftHit.entity());
            hit.setSelected(true);
            hit.setReplacementText(draftHit.replacementText());
            hit.setCreatedAt(now);
            hit.setUpdatedAt(now);
            hitMapper.insert(hit);
        }
    }

    private void persistIssueEvent(
        ReviewTaskEntity task,
        ReviewIssueEntity issue,
        String previousStatus,
        String newStatus,
        String eventType,
        Object payload
    ) {
        ReviewIssueEventEntity event = new ReviewIssueEventEntity();
        event.setTenantId(task.getTenantId());
        event.setProjectId(task.getProjectId());
        event.setTaskId(task.getId());
        event.setIssueId(issue.getId());
        event.setEventType(eventType);
        event.setPreviousStatus(previousStatus);
        event.setNewStatus(newStatus);
        event.setPayloadJson(serialize(payload));
        event.setCreatedBy(task.getCreatedBy());
        event.setCreatedAt(LocalDateTime.now());
        eventMapper.insert(event);
    }

    private String resolveRoundStatus(ReviewIssueEntity previous, ReviewDraftIssue draft) {
        String previousStatus = previous.getStatus() == null ? "new" : previous.getStatus();
        return ReviewIssueMatcher.classify(
            new ReviewIssueMatcher.IssueSnapshot(
                previous.getDimension(),
                previous.getTitle(),
                deserializeObject(previous.getPositionJson()),
                previous.getExcerpt(),
                previous.getProblem()
            ),
            new ReviewIssueMatcher.IssueSnapshot(
                draft.dimension(),
                draft.title(),
                draft.position(),
                draft.excerpt(),
                draft.problem()
            ),
            previousStatus
        );
    }

    private ReviewAiResult parseReviewResult(String rawJson) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawJson));
            int overallScore = root.path("overallScore").asInt(0);
            String overallConclusion = text(root, "overallConclusion", text(root, "conclusion", "PASS"));
            String summary = text(root, "summary", "");
            List<ReviewDraftIssue> issues = new ArrayList<>();
            JsonNode issuesNode = root.path("issues");
            if (issuesNode.isArray()) {
                for (JsonNode node : issuesNode) {
                    issues.add(parseDraftIssue(node));
                }
            }
            return new ReviewAiResult(overallScore, overallConclusion, summary, issues, extractJson(rawJson));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.AI_RESPONSE_INVALID, "剧本审核结果不是有效 JSON。");
        }
    }

    private ReviewDraftIssue parseDraftIssue(JsonNode node) {
        Map<String, Object> position = deserializeMap(node.path("position").toString());
        List<String> evidence = new ArrayList<>();
        JsonNode evidenceNode = node.path("evidence");
        if (evidenceNode.isArray()) {
            evidenceNode.forEach(item -> evidence.add(item.asText()));
        }
        List<ReviewDraftHit> hits = new ArrayList<>();
        JsonNode hitsNode = node.path("hits");
        if (hitsNode.isArray()) {
            hitsNode.forEach(hitNode -> hits.add(new ReviewDraftHit(
                hitNode.path("episode").isMissingNode() ? null : hitNode.path("episode").asInt(),
                text(hitNode, "scene", null),
                hitNode.path("shot").isMissingNode() ? null : hitNode.path("shot").asInt(),
                hitNode.path("line").isMissingNode() ? null : hitNode.path("line").asInt(),
                text(hitNode, "anchor", null),
                text(hitNode, "excerpt", ""),
                text(hitNode, "entity", null),
                text(hitNode, "replacementText", null)
            )));
        }
        return new ReviewDraftIssue(
            text(node, "issueNo", null),
            text(node, "dimension", "剧情逻辑与因果"),
            text(node, "severity", "P2"),
            text(node, "title", "未命名问题"),
            position,
            text(node, "excerpt", ""),
            text(node, "problem", ""),
            evidence,
            text(node, "suggestion", ""),
            text(node, "status", "new"),
            text(node, "relatedIssueNo", null),
            hits
        );
    }

    private List<ReviewIssueEntity> latestIssuesBefore(Long tenantId, Long projectId, Integer roundNo) {
        if (roundNo == null || roundNo <= 1) {
            return List.of();
        }
        ReviewTaskEntity latest = taskMapper.selectOne(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, projectId)
            .lt(ReviewTaskEntity::getRoundNo, roundNo)
            .orderByDesc(ReviewTaskEntity::getRoundNo)
            .last("limit 1"));
        if (latest == null) {
            return List.of();
        }
        return issueMapper.selectByTask(latest.getId());
    }

    private String issueSignature(ReviewIssueEntity issue) {
        return ReviewIssueMatcher.signature(new ReviewIssueMatcher.IssueSnapshot(
            issue.getDimension(),
            issue.getTitle(),
            deserializeObject(issue.getPositionJson()),
            issue.getExcerpt(),
            issue.getProblem()
        ));
    }

    private String issueSignature(ReviewDraftIssue issue) {
        return ReviewIssueMatcher.signature(new ReviewIssueMatcher.IssueSnapshot(
            issue.dimension(),
            issue.title(),
            issue.position(),
            issue.excerpt(),
            issue.problem()
        ));
    }

    private Map<String, Object> buildGlobalIndex(String content, ReviewTaskEntity task) {
        Map<String, Object> index = new LinkedHashMap<>();
        List<ScriptEpisodeResponse> episodes = ScriptEpisodeParser.parse(content);
        index.put("episodeCount", episodes.size());
        index.put("episodes", episodes.stream().map(episode -> Map.of(
            "episodeNo", episode.episodeNo(),
            "title", episode.title(),
            "contentLength", episode.content() == null ? 0 : episode.content().length(),
            "leadingText", leadingText(episode.content())
        )).toList());
        index.put("keywords", scanKeywords(content));
        index.put("reviewMode", task.getReviewMode());
        index.put("selectedDimensions", deserializeStringList(task.getSelectedDimensionsJson()));
        return index;
    }

    private String leadingText(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private List<String> scanKeywords(String content) {
        if (content == null) {
            return List.of();
        }
        List<String> keywords = List.of("手机", "文件", "戒指", "录音笔", "协议", "结婚", "离婚", "钱", "DNA", "监控", "车钥匙", "账本");
        return keywords.stream().filter(content::contains).toList();
    }

    private String scopeContent(String content, ReviewTaskEntity task) {
        Map<String, Object> scope = deserializeObject(task.getReviewScopeJson());
        if (scope.isEmpty()) {
            return content;
        }
        if ("EPISODES".equals(task.getReviewScopeType())) {
            List<Integer> episodeNos = extractIntegerList(scope.get("episodeNos"));
            if (episodeNos.isEmpty()) {
                return content;
            }
            Set<Integer> allowed = new LinkedHashSet<>(episodeNos);
            return ScriptEpisodeParser.parse(content).stream()
                .filter(item -> allowed.contains(item.episodeNo()))
                .map(item -> "第%d集\n%s".formatted(item.episodeNo(), item.content()))
                .collect(Collectors.joining("\n\n"));
        }
        return content;
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

    private ReviewIssueEntity requireIssue(Long tenantId, Long issueId) {
        ReviewIssueEntity issue = issueMapper.selectOne(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTenantId, tenantId)
            .eq(ReviewIssueEntity::getId, issueId));
        if (issue == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "审核问题不存在。");
        }
        return issue;
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

    private ReviewTaskResponse toTaskResponse(ReviewTaskEntity task, boolean includeIssues) {
        ReviewReviewSummaryResponse summary = null;
        if (task.getResultJson() != null && !task.getResultJson().isBlank()) {
            summary = readSummary(task.getResultJson());
        }
        List<ReviewIssueResponse> issues = includeIssues ? issueMapper.selectByTask(task.getId()).stream().map(this::toIssueResponse).toList() : List.of();
        return new ReviewTaskResponse(
            task.getId(),
            task.getProjectId(),
            task.getScriptVersionId(),
            task.getRoundNo(),
            task.getReviewMode(),
            deserializeStringList(task.getSelectedDimensionsJson()),
            task.getReviewScopeType(),
            deserializeObject(task.getReviewScopeJson()),
            task.getStatus(),
            task.getCurrentStage(),
            task.getOverallProgress(),
            task.getCurrentAction(),
            task.getErrorCode(),
            task.getErrorMessage(),
            task.getCompletedAt(),
            task.getCanceledAt(),
            summary,
            issues
        );
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
            .orderByAsc(ReviewTaskEntity::getRoundNo))
            .stream()
            .map(task -> {
                List<ReviewIssueEntity> issues = issueMapper.selectByTask(task.getId());
                long processedCount = issues.stream().filter(issue -> Boolean.TRUE.equals(issue.getManuallyResolved())).count();
                return new ReviewRoundHistoryResponse(
                    task.getId(),
                    task.getRoundNo(),
                    task.getStatus(),
                    task.getReviewMode(),
                    issues.size(),
                    (int) processedCount,
                    task.getResultJson() == null ? null : readSummary(task.getResultJson()),
                    task.getCompletedAt()
                );
            })
            .toList();
    }

    private List<ReviewIssueMappingResponse> buildIssueMappings(Long tenantId, Long projectId, Long versionId) {
        return issueMapper.selectList(new LambdaQueryWrapper<ReviewIssueEntity>()
            .eq(ReviewIssueEntity::getTenantId, tenantId)
            .eq(ReviewIssueEntity::getProjectId, projectId)
            .eq(ReviewIssueEntity::getScriptVersionId, versionId)
            .orderByAsc(ReviewIssueEntity::getRoundNo)
            .orderByAsc(ReviewIssueEntity::getIssueNo))
            .stream()
            .map(issue -> {
                List<ReviewIssueHitEntity> hits = hitMapper.selectByIssue(issue.getId());
                return new ReviewIssueMappingResponse(
                    issue.getId(),
                    issue.getIssueNo(),
                    issue.getRoundNo(),
                    issue.getStatus(),
                    issue.getRelatedIssueNo(),
                    issue.getDimension(),
                    issue.getTitle(),
                    hits.size(),
                    hits.stream().map(ReviewIssueHitEntity::getId).toList()
                );
            })
            .toList();
    }

    private ReviewIssueResponse toIssueResponse(ReviewIssueEntity issue) {
        return new ReviewIssueResponse(
            issue.getId(),
            issue.getTaskId(),
            issue.getScriptVersionId(),
            issue.getRoundNo(),
            issue.getIssueNo(),
            issue.getDimension(),
            issue.getSeverity(),
            issue.getTitle(),
            deserializeObject(issue.getPositionJson()),
            issue.getExcerpt(),
            issue.getProblem(),
            deserializeStringList(issue.getEvidenceJson()),
            issue.getSuggestion(),
            issue.getStatus(),
            issue.getRelatedIssueNo(),
            issue.getManuallyResolved(),
            issue.getManuallyResolvedAt(),
            issue.getManuallyResolvedBy(),
            hitMapper.selectByIssue(issue.getId()).stream().map(this::toHitResponse).toList()
        );
    }

    private ReviewIssueHitResponse toHitResponse(ReviewIssueHitEntity hit) {
        return new ReviewIssueHitResponse(
            hit.getId(),
            hit.getIssueId(),
            hit.getHitNo(),
            hit.getEpisodeNo(),
            hit.getSceneNo(),
            hit.getShotNo(),
            hit.getLineNo(),
            hit.getAnchorLabel(),
            hit.getExcerpt(),
            hit.getEntityName(),
            hit.getSelected(),
            hit.getReplacementText()
        );
    }

    private ReviewProjectSummaryResponse toProjectSummary(ReviewProjectEntity project, Long tenantId) {
        List<ReviewScriptVersionEntity> versions = versionMapper.selectByProject(tenantId, project.getId());
        List<ReviewTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getTenantId, tenantId)
            .eq(ReviewTaskEntity::getProjectId, project.getId())
            .orderByDesc(ReviewTaskEntity::getRoundNo));
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
            versions.size(),
            tasks.isEmpty() ? 0 : tasks.get(0).getRoundNo(),
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

    private ReviewReviewSummaryResponse readSummary(String resultJson) {
        try {
            JsonNode root = objectMapper.readTree(resultJson);
            return new ReviewReviewSummaryResponse(
                text(root, "overallConclusion", text(root, "conclusion", "PASS")),
                root.path("overallScore").asInt(0),
                text(root, "summary", "")
            );
        } catch (Exception exception) {
            return new ReviewReviewSummaryResponse("PASS", 0, "");
        }
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
        return dimensions.stream()
            .map(item -> item == null ? "" : item.trim())
            .filter(item -> !item.isBlank())
            .distinct()
            .toList();
    }

    private String resolveImportedContent(MultipartFile file, String content) {
        if (file != null && !file.isEmpty()) {
            return extractFileContent(file);
        }
        return content == null ? "" : content.trim();
    }

    private String extractFileContent(MultipartFile file) {
        String name = blankToNull(file.getOriginalFilename()) == null ? "" : file.getOriginalFilename().trim().toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".docx")) {
                try (InputStream input = new ByteArrayInputStream(file.getBytes()); XWPFDocument document = new XWPFDocument(input)) {
                    return document.getParagraphs().stream()
                        .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                        .filter(text -> !text.isBlank())
                        .collect(Collectors.joining("\n"));
                }
            }
            if (name.endsWith(".doc")) {
                try (InputStream input = new ByteArrayInputStream(file.getBytes()); HWPFDocument document = new HWPFDocument(input); WordExtractor extractor = new WordExtractor(document)) {
                    return extractor.getText();
                }
            }
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "文件解析失败：" + exception.getMessage());
        }
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

    private String applyBatchRepairToContent(String content, BatchRepairReviewRequest request, ReviewTaskEntity task) {
        String current = content == null ? "" : content;
        String actionType = normalizeActionType(request.actionType());
        if ("GLOBAL_REPLACE".equals(actionType)) {
            String from = blankToNull(request.replacementFrom());
            String to = request.replacementTo() == null ? "" : request.replacementTo();
            if (from == null) {
                return current;
            }
            if (request.selectedHitIds() != null && !request.selectedHitIds().isEmpty()) {
                List<ReviewIssueHitEntity> selectedHits = selectHits(task, request.selectedHitIds());
                for (ReviewIssueHitEntity hit : selectedHits) {
                    if (hit.getExcerpt() != null && !hit.getExcerpt().isBlank()) {
                        current = current.replaceFirst(
                            java.util.regex.Pattern.quote(hit.getExcerpt()),
                            java.util.regex.Matcher.quoteReplacement(to)
                        );
                    }
                }
                return current;
            }
            return current.replace(from, to);
        }
        List<ReviewIssueHitEntity> selectedHits = selectHits(task, request.selectedHitIds());
        if ("BATCH_DELETE".equals(actionType)) {
            for (ReviewIssueHitEntity hit : selectedHits) {
                if (hit.getExcerpt() != null && !hit.getExcerpt().isBlank()) {
                    current = current.replaceFirst(java.util.regex.Pattern.quote(hit.getExcerpt()), "");
                }
            }
            String deletionText = blankToNull(request.deletionText());
            if (deletionText != null) {
                current = current.replace(deletionText, "");
            }
            return current;
        }
        if ("BATCH_INSERT".equals(actionType)) {
            String insertion = blankToNull(request.insertionText());
            if (insertion == null) {
                return current;
            }
            if (selectedHits.isEmpty()) {
                return current + "\n" + insertion;
            }
            for (ReviewIssueHitEntity hit : selectedHits) {
                if (hit.getExcerpt() != null && !hit.getExcerpt().isBlank()) {
                    current = current.replaceFirst(java.util.regex.Pattern.quote(hit.getExcerpt()), java.util.regex.Matcher.quoteReplacement(hit.getExcerpt() + "\n" + insertion));
                }
            }
            return current;
        }
        return current;
    }

    private List<ReviewIssueHitEntity> selectHits(ReviewTaskEntity task, List<Long> hitIds) {
        if (task.getId() == null || hitIds == null || hitIds.isEmpty()) {
            return List.of();
        }
        return hitMapper.selectList(new LambdaQueryWrapper<ReviewIssueHitEntity>()
            .eq(ReviewIssueHitEntity::getTaskId, task.getId())
            .in(ReviewIssueHitEntity::getId, hitIds));
    }

    private String normalizeActionType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("GLOBAL_REPLACE", "BATCH_INSERT", "BATCH_DELETE").contains(normalized)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择基础批量修复类型。");
        }
        return normalized;
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

    private String buildExportDocument(ReviewProjectEntity project, ReviewScriptVersionEntity version, ReviewTaskResponse task) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(project.getName()).append("\n\n");
        builder.append("- 版本: ").append(version.getVersionNo()).append("\n");
        builder.append("- 审核轮次: ").append(task.roundNo()).append("\n");
        builder.append("- 结果: ").append(task.summary() == null ? "-" : task.summary().overallConclusion()).append("\n");
        builder.append("- 评分: ").append(task.summary() == null ? 0 : task.summary().overallScore()).append("\n\n");
        builder.append("## 问题\n");
        for (ReviewIssueResponse issue : task.issues()) {
            builder.append("\n### ").append(issue.issueNo()).append(" ").append(issue.title()).append("\n");
            builder.append("- 维度: ").append(issue.dimension()).append("\n");
            builder.append("- 严重度: ").append(issue.severity()).append("\n");
            builder.append("- 状态: ").append(issue.status()).append("\n");
            builder.append("- 位置: ").append(serialize(issue.position())).append("\n");
            builder.append("- 原文: ").append(issue.excerpt()).append("\n");
            builder.append("- 原因: ").append(issue.problem()).append("\n");
            builder.append("- 建议: ").append(issue.suggestion()).append("\n");
        }
        return builder.toString();
    }

    private void writeExportFile(
        Path target,
        String exportType,
        ReviewProjectEntity project,
        ReviewScriptVersionEntity version,
        ReviewTaskResponse task,
        String markdown
    ) throws Exception {
        switch (exportType) {
            case "WORD" -> writeWord(target, project, version, task);
            case "EXCEL" -> writeExcel(target, project, version, task);
            case "PDF" -> writePdf(target, project, version, task);
            default -> Files.writeString(target, markdown, StandardCharsets.UTF_8);
        }
    }

    private void writeWord(
        Path target,
        ReviewProjectEntity project,
        ReviewScriptVersionEntity version,
        ReviewTaskResponse task
    ) throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            addWordParagraph(document, project.getName());
            addWordParagraph(document, "版本 V" + version.getVersionNo() + " · 审核轮次 " + task.roundNo());
            if (task.summary() != null) {
                addWordParagraph(document, "结论：" + task.summary().overallConclusion());
                addWordParagraph(document, "评分：" + task.summary().overallScore());
                addWordParagraph(document, task.summary().summary());
            }
            for (ReviewIssueResponse issue : task.issues()) {
                addWordParagraph(document, issue.issueNo() + " " + issue.title());
                addWordParagraph(document, "维度：" + issue.dimension() + " · 状态：" + issue.status());
                addWordParagraph(document, "问题：" + issue.problem());
                addWordParagraph(document, "原文：" + issue.excerpt());
                addWordParagraph(document, "建议：" + issue.suggestion());
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                document.write(output);
                Files.write(target, output.toByteArray());
            }
        }
    }

    private void addWordParagraph(XWPFDocument document, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(value == null ? "" : value);
    }

    private void writeExcel(
        Path target,
        ReviewProjectEntity project,
        ReviewScriptVersionEntity version,
        ReviewTaskResponse task
    ) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("审核问题");
            var header = sheet.createRow(0);
            String[] headers = {"剧本", "版本", "轮次", "问题编号", "维度", "严重度", "状态", "标题", "位置", "原文", "问题", "建议"};
            for (int index = 0; index < headers.length; index++) {
                header.createCell(index).setCellValue(headers[index]);
            }
            int rowIndex = 1;
            for (ReviewIssueResponse issue : task.issues()) {
                var row = sheet.createRow(rowIndex++);
                String[] values = {
                    project.getName(),
                    "V" + version.getVersionNo(),
                    String.valueOf(task.roundNo()),
                    issue.issueNo(),
                    issue.dimension(),
                    issue.severity(),
                    issue.status(),
                    issue.title(),
                    serialize(issue.position()),
                    issue.excerpt(),
                    issue.problem(),
                    issue.suggestion()
                };
                for (int index = 0; index < values.length; index++) {
                    row.createCell(index).setCellValue(values[index] == null ? "" : values[index]);
                }
            }
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                workbook.write(output);
                Files.write(target, output.toByteArray());
            }
        }
    }

    private void writePdf(
        Path target,
        ReviewProjectEntity project,
        ReviewScriptVersionEntity version,
        ReviewTaskResponse task
    ) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 10);
                stream.setLeading(14);
                stream.newLineAtOffset(40, 800);
                writePdfLine(stream, project.getName());
                writePdfLine(stream, "Version V" + version.getVersionNo() + " / Round " + task.roundNo());
                if (task.summary() != null) {
                    writePdfLine(stream, "Conclusion: " + ascii(task.summary().overallConclusion()));
                    writePdfLine(stream, "Score: " + task.summary().overallScore());
                    writePdfLine(stream, ascii(task.summary().summary()));
                }
                for (ReviewIssueResponse issue : task.issues()) {
                    writePdfLine(stream, issue.issueNo() + " " + ascii(issue.title()));
                    writePdfLine(stream, "Dimension: " + ascii(issue.dimension()) + " / Status: " + issue.status());
                    writePdfLine(stream, "Problem: " + ascii(issue.problem()));
                    writePdfLine(stream, "Suggestion: " + ascii(issue.suggestion()));
                    stream.newLine();
                }
                stream.endText();
            }
            document.save(target.toFile());
        }
    }

    private void writePdfLine(PDPageContentStream stream, String value) throws Exception {
        stream.showText(ascii(value));
        stream.newLine();
    }

    private String ascii(String value) {
        if (value == null) {
            return "";
        }
        return value.chars()
            .mapToObj(character -> character < 128 ? String.valueOf((char) character) : "?")
            .collect(Collectors.joining());
    }

    private String buildExportFileName(String projectName, Integer versionNo, String exportType) {
        String safeName = projectName == null ? "review" : projectName.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        String extension = switch (exportType) {
            case "WORD" -> "docx";
            case "PDF" -> "pdf";
            case "EXCEL" -> "xlsx";
            default -> "md";
        };
        return "%s_v%d_%s.%s".formatted(
            safeName,
            versionNo == null ? 1 : versionNo,
            exportType.toLowerCase(Locale.ROOT),
            extension
        );
    }

    private String normalizeExportType(String exportType) {
        String value = exportType == null ? "" : exportType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WORD", "PDF", "EXCEL", "MARKDOWN").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择导出格式。");
        }
        return value;
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

    private String issueBrief(ReviewIssueEntity issue) {
        return "%s|%s|%s".formatted(issue.getDimension(), issue.getTitle(), issue.getStatus());
    }

    private RecordReviewTaskMatcher matcherFor(ReviewTaskEntity task) {
        return new RecordReviewTaskMatcher(task.getId(), task.getRoundNo());
    }

    private record ReviewAiResult(
        int overallScore,
        String overallConclusion,
        String summary,
        List<ReviewDraftIssue> issues,
        String rawJson
    ) {
    }

    private record ReviewInvocationOutcome(
        ReviewAiResult result,
        AiInvocationResult<com.antshorttv.ai.AiTextResponse> invocation
    ) {
    }

    private record ReviewDraftIssue(
        String issueNo,
        String dimension,
        String severity,
        String title,
        Map<String, Object> position,
        String excerpt,
        String problem,
        List<String> evidence,
        String suggestion,
        String status,
        String relatedIssueNo,
        List<ReviewDraftHit> hits
    ) {
        String signature() {
            return ReviewIssueMatcher.signature(new ReviewIssueMatcher.IssueSnapshot(
                dimension,
                title,
                position,
                excerpt,
                problem
            ));
        }
    }

    private record ReviewDraftHit(
        Integer episodeNo,
        String sceneNo,
        Integer shotNo,
        Integer lineNo,
        String anchorLabel,
        String excerpt,
        String entity,
        String replacementText
    ) {
    }

    private record RecordReviewTaskMatcher(Long taskId, Integer roundNo) {
    }
}
