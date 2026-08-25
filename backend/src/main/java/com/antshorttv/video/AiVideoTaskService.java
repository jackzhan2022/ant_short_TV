package com.antshorttv.video;

import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiUsageAccountingService;
import com.antshorttv.accounting.AiUsageCommand;
import com.antshorttv.accounting.AiUsageContext;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.ai.AiCapability;
import com.antshorttv.ai.AiGatewayException;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiModelRoute;
import com.antshorttv.ai.AiModelRouter;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionAttemptEntity;
import com.antshorttv.execution.AiExecutionAttemptMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.material.VideoMaterialEntity;
import com.antshorttv.material.VideoMaterialMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectOperationLogEntity;
import com.antshorttv.project.ProjectOperationLogMapper;
import com.antshorttv.script.StoryboardEntity;
import com.antshorttv.script.StoryboardMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiVideoTaskService {
    private static final List<String> CANCELABLE_STATUSES = List.of("PENDING", "SUBMITTING", "GENERATING");
    private static final List<Integer> SUPPORTED_DURATIONS = List.of(5, 8, 10);
    private static final List<String> SUPPORTED_ASPECT_RATIOS = List.of("9:16", "16:9", "1:1");

    private final ProjectAccessResolver projectAccessResolver;
    private final StoryboardMapper storyboardMapper;
    private final AiVideoTaskMapper aiVideoTaskMapper;
    private final AiVideoResultMapper aiVideoResultMapper;
    private final VideoMaterialMapper materialMapper;
    private final ProjectOperationLogMapper projectOperationLogMapper;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;
    private final MaterialFileAccessService materialFileAccessService;
    private final ObjectStorageService objectStorageService;
    private final AiTaskExecutionSupport executionSupport;
    private final AiVideoProviderAdapter providerAdapter;
    private final com.antshorttv.ai.AiInvocationService invocationService;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiModelRouter aiModelRouter;
    private final AiExecutionService executionService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final AiExecutionAttemptMapper executionAttemptMapper;
    private final AiUsageAccountingService usageAccountingService;
    private final ObjectMapper objectMapper;
    private final AiPointReservationMapper reservationMapper;
    private final AiPointSettlementService pointSettlementService;
    private final int maxConcurrentPerTenant;
    private final int taskTimeoutMinutes;
    private final int pollRetryLimit;
    private final int dueTaskBatchSize;
    private final Path storageRoot;
    private final boolean mockProviderEnabled;

    public AiVideoTaskService(
        ProjectAccessResolver projectAccessResolver,
        StoryboardMapper storyboardMapper,
        AiVideoTaskMapper aiVideoTaskMapper,
        AiVideoResultMapper aiVideoResultMapper,
        VideoMaterialMapper materialMapper,
        ProjectOperationLogMapper projectOperationLogMapper,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService,
        MaterialFileAccessService materialFileAccessService,
        ObjectStorageService objectStorageService,
        AiTaskExecutionSupport executionSupport,
        AiVideoProviderAdapter providerAdapter,
        com.antshorttv.ai.AiInvocationService invocationService,
        ProjectAiConfigService projectAiConfigService,
        AiModelRouter aiModelRouter,
        AiExecutionService executionService,
        AiExecutionTaskMapper executionTaskMapper,
        AiExecutionAttemptMapper executionAttemptMapper,
        AiUsageAccountingService usageAccountingService,
        ObjectMapper objectMapper,
        AiPointReservationMapper reservationMapper,
        AiPointSettlementService pointSettlementService,
        @Value("${ai.video.max-concurrent-per-tenant:3}") int maxConcurrentPerTenant,
        @Value("${ai.video.task-timeout-minutes:20}") int taskTimeoutMinutes,
        @Value("${ai.video.poll-retry-limit:3}") int pollRetryLimit,
        @Value("${ai.video.due-task-batch-size:20}") int dueTaskBatchSize,
        @Value("${ai.video.storage-root:storage}") String storageRoot,
        @Value("${ai.testing.mock-provider-enabled:false}") boolean mockProviderEnabled
    ) {
        this.projectAccessResolver = projectAccessResolver;
        this.storyboardMapper = storyboardMapper;
        this.aiVideoTaskMapper = aiVideoTaskMapper;
        this.aiVideoResultMapper = aiVideoResultMapper;
        this.materialMapper = materialMapper;
        this.projectOperationLogMapper = projectOperationLogMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
        this.materialFileAccessService = materialFileAccessService;
        this.objectStorageService = objectStorageService;
        this.executionSupport = executionSupport;
        this.providerAdapter = providerAdapter;
        this.invocationService = invocationService;
        this.projectAiConfigService = projectAiConfigService;
        this.aiModelRouter = aiModelRouter;
        this.executionService = executionService;
        this.executionTaskMapper = executionTaskMapper;
        this.executionAttemptMapper = executionAttemptMapper;
        this.usageAccountingService = usageAccountingService;
        this.objectMapper = objectMapper;
        this.reservationMapper = reservationMapper;
        this.pointSettlementService = pointSettlementService;
        this.maxConcurrentPerTenant = maxConcurrentPerTenant;
        this.taskTimeoutMinutes = taskTimeoutMinutes;
        this.pollRetryLimit = pollRetryLimit;
        this.dueTaskBatchSize = dueTaskBatchSize;
        this.storageRoot = Path.of(storageRoot);
        this.mockProviderEnabled = mockProviderEnabled;
    }

    public List<AiVideoTaskResponse> list(Long tenantId, Long projectId, String status, Long storyboardId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        return aiVideoTaskMapper.selectByProject(tenantId, projectId, status, storyboardId)
            .stream()
            .map(task -> AiVideoTaskResponse.from(task, aiVideoResultMapper.selectByTask(tenantId, projectId, task.id)))
            .toList();
    }

    public AiVideoTaskResponse detail(Long tenantId, Long projectId, Long taskId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        return response(task);
    }

    @Transactional
    public AiVideoTaskResponse create(
        Long tenantId,
        Long projectId,
        CreateAiVideoTaskRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        validateCreateRequest(request);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        String firstFrameUrl = resolveFirstFrameUrl(storyboard, request);
        AiModelRoute route = resolveVideoModel(tenantId, projectId, request.modelId());
        String requestHash = requestHash(request, route.model().getId(), firstFrameUrl);
        AiVideoTaskEntity duplicate = aiVideoTaskMapper.selectActiveDuplicate(tenantId, projectId, requestHash);
        if (duplicate != null) {
            return response(duplicate);
        }
        if (aiVideoTaskMapper.countActiveByTenant(tenantId) >= maxConcurrentPerTenant) {
            throw new BusinessException(ErrorCode.AI_VIDEO_CONCURRENCY_LIMIT_EXCEEDED, "当前团队视频生成并发已达上限，请稍后再试。");
        }
        LocalDateTime now = LocalDateTime.now();

        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.tenantId = tenantId;
        task.projectId = projectId;
        task.storyboardId = storyboard.id;
        task.modelId = route.model().getId();
        task.providerCode = route.provider().getCode();
        task.model = route.model().getModelCode();
        task.prompt = request.prompt().trim();
        task.negativePrompt = blankToNull(request.negativePrompt());
        task.firstFrameImageId = request.firstFrameImageId() == null ? storyboard.firstFrameImageId : request.firstFrameImageId();
        task.firstFrameUrl = materialFileAccessService.publicUrl(firstFrameUrl);
        task.lastFrameImageId = request.lastFrameImageId();
        task.lastFrameUrl = blankToNull(request.lastFrameUrl());
        task.durationSeconds = request.durationSeconds() == null ? 5 : request.durationSeconds();
        task.aspectRatio = request.aspectRatio();
        task.resolution = request.resolution() == null || request.resolution().isBlank() ? "STANDARD" : request.resolution();
        task.motionStrength = request.motionStrength() == null || request.motionStrength().isBlank() ? "MEDIUM" : request.motionStrength();
        task.cameraMovement = blankToNull(request.cameraMovement());
        task.randomSeed = request.randomSeed();
        task.requestHash = requestHash;
        task.pollRetryCount = 0;
        task.status = AiVideoTaskStatus.PENDING.name();
        task.createdBy = context.userId();
        task.createdAt = now;
        task.updatedAt = now;
        task.nextPollAt = now;
        aiVideoTaskMapper.insert(task);

        AiExecutionTaskEntity execution = executionService.createWithReservation(
            new AiExecutionCreateCommand(
                tenantId,
                context.userId(),
                projectId,
                "ai_video_generate",
                "VIDEO",
                "AI_VIDEO_TASK",
                task.id,
                task.modelId,
                "VIDEO_SUBMIT",
                requestHash,
                UUID.randomUUID().toString(),
                true,
                "{\"storyboardId\":%d}".formatted(task.storyboardId)
            ),
            Map.of(AiUsageMetric.VIDEO_SECOND, BigDecimal.valueOf(task.durationSeconds)),
            Map.of("resolution", task.resolution, "aspectRatio", task.aspectRatio)
        );
        task.executionId = execution.id;
        aiVideoTaskMapper.updateById(task);
        markExecutionRunning(task);

        submitTask(task);
        aiVideoTaskMapper.updateById(task);
        recordOperation(context, projectId, "CREATE_AI_VIDEO_TASK", task.id, servletRequest);
        recordProjectLog(context, projectId, "AI_VIDEO_TASK_CREATE", "AI_VIDEO_TASK", task.id, null, task.status, servletRequest);
        return response(task);
    }

    @Transactional
    public AiVideoTaskResponse poll(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        processTask(task, context.userId());
        recordOperation(context, projectId, "POLL_AI_VIDEO_TASK", task.id, servletRequest);
        return response(task);
    }

    @Transactional
    public void pollDueTasks() {
        List<AiVideoTaskEntity> dueTasks = aiVideoTaskMapper.selectDueTasks(LocalDateTime.now(), dueTaskBatchSize);
        for (AiVideoTaskEntity task : dueTasks) {
            processTask(task, task.createdBy);
        }
    }

    @Transactional
    public AiVideoTaskResponse cancel(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        if (!CANCELABLE_STATUSES.contains(task.status)) {
            throw new BusinessException(ErrorCode.AI_VIDEO_TASK_STATUS_INVALID, "当前任务状态不可取消。");
        }
        task.status = AiVideoTaskStatus.CANCELED.name();
        task.externalStatus = "CANCELED";
        task.completedAt = LocalDateTime.now();
        task.updatedAt = task.completedAt;
        aiVideoTaskMapper.updateById(task);
        settleExecution(task, AiSettlementOutcome.PRE_CALL_CANCELED, null, null, "CANCELED");
        recordOperation(context, projectId, "CANCEL_AI_VIDEO_TASK", task.id, servletRequest);
        return response(task);
    }

    @Transactional
    public AiVideoTaskResponse regenerate(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        AiVideoTaskEntity source = requireTask(tenantId, projectId, taskId);
        CreateAiVideoTaskRequest request = new CreateAiVideoTaskRequest(
            source.storyboardId,
            source.modelId,
            source.prompt,
            source.negativePrompt,
            source.firstFrameImageId,
            source.firstFrameUrl,
            source.lastFrameImageId,
            source.lastFrameUrl,
            source.durationSeconds,
            source.aspectRatio,
            source.resolution,
            source.cameraMovement,
            source.motionStrength,
            source.randomSeed
        );
        AiVideoTaskResponse response = create(tenantId, projectId, request, servletRequest);
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        recordProjectLog(context, projectId, "AI_VIDEO_TASK_REGENERATE", "AI_VIDEO_TASK", response.id(), String.valueOf(taskId), response.status(), servletRequest);
        return response;
    }

    @Transactional
    public void deleteTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        task.deletedAt = LocalDateTime.now();
        task.updatedAt = task.deletedAt;
        aiVideoTaskMapper.updateById(task);
        recordOperation(context, projectId, "DELETE_AI_VIDEO_TASK", task.id, servletRequest);
    }

    public List<AiVideoResultResponse> results(Long tenantId, Long projectId, Long taskId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        requireTask(tenantId, projectId, taskId);
        return aiVideoResultMapper.selectByTask(tenantId, projectId, taskId)
            .stream()
            .map(AiVideoResultResponse::from)
            .toList();
    }

    @Transactional
    public AiVideoResultResponse saveMaterial(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoResultEntity result = requireResult(tenantId, projectId, resultId);
        if (result.materialId != null) {
            return AiVideoResultResponse.from(result);
        }
        LocalDateTime now = LocalDateTime.now();
        VideoMaterialEntity material = new VideoMaterialEntity();
        material.setTenantId(tenantId);
        material.setProjectId(projectId);
        material.setMaterialType("VIDEO");
        material.setSourceType("AI_GENERATED");
        material.setSourceTaskId(result.taskId);
        material.setSourceResultId(result.id);
        material.setName("分镜视频-" + result.storyboardId + "-" + result.id);
        material.setUrl(result.videoUrl);
        material.setCoverUrl(result.coverUrl);
        material.setDurationSeconds(result.durationSeconds);
        material.setWidth(result.width);
        material.setHeight(result.height);
        material.setFormat(result.format);
        material.setFileSize(result.fileSize);
        material.setStatus("ACTIVE");
        material.setCreatedBy(context.userId());
        material.setCreatedAt(now);
        material.setUpdatedAt(now);
        materialMapper.insert(material);

        result.materialId = material.getId();
        result.updatedAt = now;
        aiVideoResultMapper.updateById(result);
        recordOperation(context, projectId, "SAVE_AI_VIDEO_RESULT_MATERIAL", result.id, servletRequest);
        return AiVideoResultResponse.from(result);
    }

    @Transactional
    public AiVideoResultResponse bindStoryboard(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoResultEntity result = requireResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        LocalDateTime now = LocalDateTime.now();
        for (AiVideoResultEntity item : aiVideoResultMapper.selectByTask(tenantId, projectId, result.taskId)) {
            if (Boolean.TRUE.equals(item.isSelected)) {
                item.isSelected = false;
                item.updatedAt = now;
                aiVideoResultMapper.updateById(item);
            }
        }
        result.isSelected = true;
        result.updatedAt = now;
        aiVideoResultMapper.updateById(result);

        storyboard.currentVideoResultId = result.id;
        storyboard.currentVideoMaterialId = result.materialId;
        storyboard.currentVideoUrl = result.videoUrl;
        storyboard.updatedAt = now;
        storyboardMapper.updateById(storyboard);
        recordOperation(context, projectId, "BIND_AI_VIDEO_RESULT_STORYBOARD", result.id, servletRequest);
        return AiVideoResultResponse.from(result);
    }

    @Transactional
    public void deleteResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoResultEntity result = requireResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        if (result.id.equals(storyboard.currentVideoResultId)) {
            throw new BusinessException(ErrorCode.AI_VIDEO_RESULT_IN_USE, "当前视频已被分镜引用，不能静默删除。");
        }
        result.status = AiVideoResultStatus.DELETED.name();
        result.updatedAt = LocalDateTime.now();
        aiVideoResultMapper.updateById(result);
        recordOperation(context, projectId, "DELETE_AI_VIDEO_RESULT", result.id, servletRequest);
    }

    public AiVideoResultResponse download(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoResultEntity result = requireResult(tenantId, projectId, resultId);
        recordOperation(context, projectId, "DOWNLOAD_AI_VIDEO_RESULT", result.id, servletRequest);
        return AiVideoResultResponse.from(result);
    }

    private AiVideoTaskResponse response(AiVideoTaskEntity task) {
        return AiVideoTaskResponse.from(task, aiVideoResultMapper.selectByTask(task.tenantId, task.projectId, task.id));
    }

    private void validateCreateRequest(CreateAiVideoTaskRequest request) {
        int duration = request.durationSeconds() == null ? 5 : request.durationSeconds();
        if (!SUPPORTED_DURATIONS.contains(duration)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择正确的视频时长。");
        }
        if (!SUPPORTED_ASPECT_RATIOS.contains(request.aspectRatio())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择视频比例。");
        }
    }

    private String resolveFirstFrameUrl(StoryboardEntity storyboard, CreateAiVideoTaskRequest request) {
        String firstFrameUrl = blankToNull(request.firstFrameUrl());
        if (firstFrameUrl == null) {
            firstFrameUrl = blankToNull(storyboard.firstFrameUrl);
        }
        if (firstFrameUrl == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED, "请先生成或上传分镜首帧图。");
        }
        return firstFrameUrl;
    }

    private AiModelRoute resolveVideoModel(Long tenantId, Long projectId, Long requestedModelId) {
        Long modelId = requestedModelId == null
            ? projectAiConfigService.resolveModelId(tenantId, projectId, "VIDEO")
            : requestedModelId;
        return aiModelRouter.route(modelId, AiCapability.VIDEO);
    }

    private void submitTask(AiVideoTaskEntity task) {
        LocalDateTime now = LocalDateTime.now();
        task.status = AiVideoTaskStatus.SUBMITTING.name();
        task.updatedAt = now;
        task.submittedAt = now;
        AiExecutionAttemptEntity attempt = startExecutionAttempt(task, "VIDEO_SUBMIT");
        try {
            AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation = invocationService.invokeProviderNative(
                invocationRequest(task, attempt),
                "taskId=%d,storyboardId=%d".formatted(task.id, task.storyboardId),
                route -> shouldUseLocalMock(route.providerConfig())
                    ? com.antshorttv.ai.AiProviderExecutionOutcome.accepted(
                        "mock-submit-" + task.id,
                        "mock-video-" + UUID.randomUUID(),
                        Duration.ofSeconds(10),
                        com.antshorttv.ai.AiProviderReconciliationStatus.NOT_REQUIRED
                    )
                    : providerAdapter.submit(route.providerConfig(), route.model(), task, attempt.idempotencyKey)
            );
            finishExecutionAttempt(attempt, invocation, "SUCCEEDED", false, null, null);
            task.externalTaskId = invocation.externalTaskId();
            task.externalStatus = "ACCEPTED";
            task.status = AiVideoTaskStatus.GENERATING.name();
            task.startedAt = now;
            task.nextPollAt = now;
            updateExecutionPhase(task.executionId, "VIDEO_QUERY", 10);
        } catch (AiGatewayException exception) {
            finishExecutionAttempt(attempt, exception, "FAILED", false);
            task.status = AiVideoTaskStatus.FAILED.name();
            task.externalStatus = "SUBMIT_FAILED";
            task.errorMessage = "服务商提交失败：" + exception.getMessage();
            task.completedAt = now;
            settleExecution(task, AiSettlementOutcome.PROVIDER_REJECTION, attempt.id, exception.getAiCallLogId(), "FAILED");
        }
        task.updatedAt = now;
    }

    private void processTask(AiVideoTaskEntity task, Long userId) {
        if (!isActiveTask(task)) {
            return;
        }
        AiTaskExecutionSupport.ClaimResult claim = executionSupport.claimAiVideoTask(
            task.id,
            task.status,
            "VIDEO_QUERY",
            Duration.ofMinutes(taskTimeoutMinutes)
        );
        if (!claim.claimed()) {
            return;
        }
        task.executionToken = claim.executionToken();
        task.executionPhase = "VIDEO_QUERY";
        task.executionVersion = claim.executionVersion();
        AiExecutionAttemptEntity attempt = startExecutionAttempt(task, "VIDEO_QUERY");
        LocalDateTime now = LocalDateTime.now();
        if (isTimedOut(task, now)) {
            failTask(task, "视频生成超时。", userId, now);
            finishExecutionAttempt(attempt, null, "TIMED_OUT", false, "AI_PROVIDER_TIMEOUT", "视频生成超时。");
            settleExecution(task, AiSettlementOutcome.TIMED_OUT, attempt.id, null, "TIMED_OUT");
            executionSupport.clearAiVideoTaskClaim(task.id, false);
            return;
        }
        AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation = null;
        try {
            invocation = queryProviderTask(task, attempt);
            task.lastPollAt = now;
            if (invocation.response() != null && "SUCCEEDED".equals(invocation.response().status())) {
                if (aiVideoResultMapper.selectByTask(task.tenantId, task.projectId, task.id).isEmpty()) {
                    createGeneratedResult(task, invocation.response().videoUrl());
                }
                task.status = AiVideoTaskStatus.SUCCEEDED.name();
                task.externalStatus = "SUCCEEDED";
                task.completedAt = now;
                task.updatedAt = now;
                task.nextPollAt = null;
                aiVideoTaskMapper.updateById(task);
                finishExecutionAttempt(attempt, invocation, "SUCCEEDED", false, null, null);
                recordUsageAndCost(task, attempt.id, invocation);
                settleExecution(task, AiSettlementOutcome.SUCCESS, attempt.id, invocation.aiCallLogId(), "SUCCEEDED");
                executionSupport.clearAiVideoTaskClaim(task.id, false);
                return;
            }
            if (invocation.response() != null && "FAILED".equals(invocation.response().status())) {
                failTask(task, invocation.response().errorMessage() == null ? "视频生成失败。" : invocation.response().errorMessage(), userId, now);
                invocationService.markBusinessFailure(invocation.aiCallLogId(), ErrorCode.AI_PROVIDER_ERROR, task.errorMessage);
                finishExecutionAttempt(attempt, invocation, "FAILED", false, "AI_PROVIDER_ERROR", task.errorMessage);
                settleExecution(task, AiSettlementOutcome.PROVIDER_BILLED_FAILURE, attempt.id, invocation.aiCallLogId(), "FAILED");
                executionSupport.clearAiVideoTaskClaim(task.id, false);
                return;
            }
            task.externalStatus = "RUNNING";
            task.nextPollAt = now.plusSeconds(10);
            task.updatedAt = now;
            aiVideoTaskMapper.updateById(task);
            finishExecutionAttempt(attempt, invocation, "SUCCEEDED", false, null, null);
            executionSupport.clearAiVideoTaskClaim(task.id, false);
        } catch (AiGatewayException exception) {
            int nextRetryCount = task.pollRetryCount == null ? 1 : task.pollRetryCount + 1;
            task.pollRetryCount = nextRetryCount;
            task.lastPollAt = now;
            task.updatedAt = now;
            if (nextRetryCount >= pollRetryLimit) {
                failTask(task, "外部任务查询失败：" + exception.getMessage(), userId, now);
                finishExecutionAttempt(attempt, exception, "FAILED", false);
                settleExecution(task, AiSettlementOutcome.PROVIDER_BILLED_FAILURE, attempt.id, exception.getAiCallLogId(), "FAILED");
                executionSupport.clearAiVideoTaskClaim(task.id, false);
                return;
            }
            task.nextPollAt = now.plusSeconds(10L * nextRetryCount);
            aiVideoTaskMapper.updateById(task);
            finishExecutionAttempt(attempt, exception, "FAILED", true);
            executionSupport.clearAiVideoTaskClaim(task.id, true);
        } catch (Exception exception) {
            if (invocation != null) {
                invocationService.markBusinessFailure(invocation.aiCallLogId(), ErrorCode.AI_RESPONSE_INVALID, exception.getMessage());
                finishExecutionAttempt(attempt, invocation, "FAILED", true, "AI_RESPONSE_INVALID", exception.getMessage());
            } else {
                finishExecutionAttempt(attempt, null, "FAILED", true, "AI_RESPONSE_INVALID", exception.getMessage());
            }
            int nextRetryCount = task.pollRetryCount == null ? 1 : task.pollRetryCount + 1;
            task.pollRetryCount = nextRetryCount;
            task.lastPollAt = now;
            task.updatedAt = now;
            if (nextRetryCount >= pollRetryLimit) {
                failTask(task, "视频结果处理失败：" + exception.getMessage(), userId, now);
                settleExecution(task, AiSettlementOutcome.BUSINESS_FAILURE, attempt.id,
                    invocation == null ? null : invocation.aiCallLogId(), "FAILED");
                executionSupport.clearAiVideoTaskClaim(task.id, false);
                return;
            }
            task.nextPollAt = now.plusSeconds(10L * nextRetryCount);
            aiVideoTaskMapper.updateById(task);
            executionSupport.clearAiVideoTaskClaim(task.id, true);
        }
    }

    private AiInvocationResult<AiVideoProviderAdapter.VideoResult> queryProviderTask(
        AiVideoTaskEntity task,
        AiExecutionAttemptEntity attempt
    ) {
        return invocationService.invokeProviderNative(
            invocationRequest(task, attempt),
            "externalTaskId=" + task.externalTaskId,
            route -> mockProviderEnabled && (task.externalTaskId == null || task.externalTaskId.startsWith("mock-video-"))
                ? com.antshorttv.ai.AiProviderExecutionOutcome.completed(
                    new AiVideoProviderAdapter.VideoResult("SUCCEEDED", null, null),
                    "mock-query-" + task.id
                )
                : providerAdapter.poll(route.providerConfig(), route.model(), task.externalTaskId, attempt.idempotencyKey)
        );
    }

    private void failTask(AiVideoTaskEntity task, String errorMessage, Long userId, LocalDateTime now) {
        task.status = AiVideoTaskStatus.FAILED.name();
        task.externalStatus = "FAILED";
        task.errorMessage = errorMessage;
        task.completedAt = now;
        task.updatedAt = now;
        task.nextPollAt = null;
        aiVideoTaskMapper.updateById(task);
    }

    private void createGeneratedResult(AiVideoTaskEntity task, String externalVideoUrl) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AiVideoResultEntity result = new AiVideoResultEntity();
        result.tenantId = task.tenantId;
        result.projectId = task.projectId;
        result.taskId = task.id;
        result.executionId = task.executionId;
        result.storyboardId = task.storyboardId;
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(now);
        result.storagePath = "/materials/%d/%d/videos/%s/%d.mp4".formatted(task.tenantId, task.projectId, day, task.id);
        long fileSize = writeVideoFile(result.storagePath, externalVideoUrl);
        result.videoUrl = materialFileAccessService.publicUrl(result.storagePath);
        result.coverUrl = task.firstFrameUrl == null ? null : materialFileAccessService.publicUrl(task.firstFrameUrl);
        result.durationSeconds = BigDecimal.valueOf(task.durationSeconds == null ? 5 : task.durationSeconds);
        result.width = "16:9".equals(task.aspectRatio) ? 1280 : 720;
        result.height = "16:9".equals(task.aspectRatio) ? 720 : 1280;
        result.fileSize = fileSize;
        result.format = "mp4";
        result.isSelected = false;
        result.status = AiVideoResultStatus.ACTIVE.name();
        result.createdAt = now;
        result.updatedAt = now;
        aiVideoResultMapper.insert(result);
    }

    private long writeVideoFile(String storagePath, String externalVideoUrl) throws Exception {
        byte[] bytes = externalVideoUrl == null || externalVideoUrl.isBlank()
            ? placeholderMp4Bytes()
            : providerAdapter.download(externalVideoUrl);
        if (objectStorageService.enabled()) {
            objectStorageService.upload(storagePath, bytes, "video/mp4");
            return bytes.length;
        }
        Path file = storageRoot.resolve(storagePath.substring(1));
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return Files.size(file);
    }

    private byte[] placeholderMp4Bytes() {
        return new byte[] {
            0, 0, 0, 24, 102, 116, 121, 112, 109, 112, 52, 50, 0, 0, 0, 0,
            109, 112, 52, 50, 105, 115, 111, 109, 0, 0, 0, 8, 109, 100, 97, 116
        };
    }

    private String requestHash(CreateAiVideoTaskRequest request, Long modelId, String firstFrameUrl) {
        String source = String.join("|",
            String.valueOf(modelId),
            String.valueOf(request.storyboardId()),
            normalizeHashPart(request.prompt()),
            normalizeHashPart(request.negativePrompt()),
            normalizeHashPart(firstFrameUrl),
            normalizeHashPart(request.lastFrameUrl()),
            String.valueOf(request.durationSeconds() == null ? 5 : request.durationSeconds()),
            normalizeHashPart(request.aspectRatio()),
            normalizeHashPart(request.resolution()),
            normalizeHashPart(request.cameraMovement()),
            normalizeHashPart(request.motionStrength()),
            String.valueOf(request.randomSeed())
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String normalizeHashPart(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean shouldUseLocalMock(com.antshorttv.ai.AiProviderConfigEntity providerConfig) {
        if (!mockProviderEnabled) {
            return false;
        }
        String baseUrl = blankToNull(providerConfig.getBaseUrl());
        return baseUrl == null || baseUrl.startsWith("mock://") || baseUrl.contains("example.com");
    }

    private boolean isActiveTask(AiVideoTaskEntity task) {
        return CANCELABLE_STATUSES.contains(task.status);
    }

    private boolean isTimedOut(AiVideoTaskEntity task, LocalDateTime now) {
        LocalDateTime startedAt = task.submittedAt == null ? task.createdAt : task.submittedAt;
        return startedAt != null && Duration.between(startedAt, now).toMinutes() >= taskTimeoutMinutes;
    }

    private ProjectEntity requireProjectAccess(TenantContext context, Long projectId) {
        return projectAccessResolver.requireView(context.tenantId(), projectId).project();
    }

    private StoryboardEntity requireStoryboard(Long tenantId, Long projectId, Long storyboardId) {
        StoryboardEntity storyboard = storyboardMapper.selectActive(tenantId, projectId, storyboardId);
        if (storyboard == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_STORYBOARD_NOT_FOUND, "分镜不存在。");
        }
        return storyboard;
    }

    private AiVideoTaskEntity requireTask(Long tenantId, Long projectId, Long taskId) {
        AiVideoTaskEntity task = aiVideoTaskMapper.selectActive(tenantId, projectId, taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_TASK_NOT_FOUND, "视频任务不存在。");
        }
        return task;
    }

    private AiVideoResultEntity requireResult(Long tenantId, Long projectId, Long resultId) {
        AiVideoResultEntity result = aiVideoResultMapper.selectActive(tenantId, projectId, resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_RESULT_NOT_FOUND, "视频结果不存在。");
        }
        return result;
    }

    private AiExecutionAttemptEntity startExecutionAttempt(AiVideoTaskEntity task, String phase) {
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(task.executionId);
        int executionVersion = execution.executionVersion == null ? 1 : execution.executionVersion;
        Long count = executionAttemptMapper.selectCount(new QueryWrapper<AiExecutionAttemptEntity>()
            .eq("execution_id", task.executionId)
            .eq("phase", phase)
            .eq("execution_version", executionVersion));
        int attemptNo = count.intValue() + 1;
        AiExecutionAttemptEntity attempt = new AiExecutionAttemptEntity();
        attempt.executionId = task.executionId;
        attempt.executionVersion = executionVersion;
        attempt.phase = phase;
        attempt.attemptNo = attemptNo;
        attempt.status = "STARTED";
        attempt.idempotencyKey = "execution:%d:v%d:%s:%d".formatted(
            task.executionId, executionVersion, phase, attemptNo
        );
        attempt.providerContacted = false;
        attempt.retryable = false;
        attempt.retryCount = Math.max(0, attemptNo - 1);
        attempt.startedAt = LocalDateTime.now();
        executionAttemptMapper.insert(attempt);
        return attempt;
    }

    private AiInvocationRequest invocationRequest(AiVideoTaskEntity task, AiExecutionAttemptEntity attempt) {
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(task.executionId);
        return AiInvocationRequest.capability(AiCapability.VIDEO)
            .tenantId(task.tenantId)
            .userId(task.createdBy)
            .projectId(task.projectId)
            .taskId(task.id)
            .modelId(task.modelId)
            .businessSceneCode("ai_video_generate")
            .traceId(execution.traceId)
            .executionId(task.executionId)
            .attemptId(attempt.id)
            .executionVersion(attempt.executionVersion)
            .phase(attempt.phase)
            .idempotencyKey(attempt.idempotencyKey)
            .requestSummary("taskId=%d,storyboardId=%d".formatted(task.id, task.storyboardId))
            .build();
    }

    private void finishExecutionAttempt(
        AiExecutionAttemptEntity attempt,
        AiInvocationResult<?> invocation,
        String status,
        boolean retryable,
        String errorCode,
        String errorMessage
    ) {
        UpdateWrapper<AiExecutionAttemptEntity> update = new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", status)
            .set("provider_contacted", invocation != null)
            .set("provider_contacted_at", invocation == null ? null : LocalDateTime.now())
            .set("provider_id", invocation == null ? null : invocation.providerId())
            .set("model_id", invocation == null ? null : invocation.resolvedModelId())
            .set("provider_request_id", invocation == null ? null : invocation.providerRequestId())
            .set("external_task_id", invocation == null ? null : invocation.externalTaskId())
            .set("ai_call_log_id", invocation == null ? null : invocation.aiCallLogId())
            .set("transport_outcome", invocation == null ? null : invocation.transportOutcome())
            .set("business_outcome", invocation == null ? null : invocation.businessOutcome())
            .set("retryable", retryable)
            .set("error_code", errorCode)
            .set("error_message", errorMessage)
            .set("finished_at", LocalDateTime.now())
            .eq("id", attempt.id);
        executionAttemptMapper.update(null, update);
    }

    private void finishExecutionAttempt(
        AiExecutionAttemptEntity attempt,
        AiGatewayException exception,
        String status,
        boolean retryable
    ) {
        executionAttemptMapper.update(null, new UpdateWrapper<AiExecutionAttemptEntity>()
            .set("status", status)
            .set("provider_contacted", exception.getAiCallLogId() != null)
            .set("ai_call_log_id", exception.getAiCallLogId())
            .set("transport_outcome", "FAILED")
            .set("business_outcome", "NOT_REACHED")
            .set("retryable", retryable)
            .set("error_code", exception.getErrorCode().name())
            .set("error_message", exception.getMessage())
            .set("finished_at", LocalDateTime.now())
            .eq("id", attempt.id));
    }

    private void recordUsageAndCost(
        AiVideoTaskEntity task,
        Long attemptId,
        AiInvocationResult<?> invocation
    ) {
        LocalDateTime observedAt = LocalDateTime.now();
        AiUsageContext context = new AiUsageContext(
            task.tenantId,
            task.executionId,
            attemptId,
            invocation.aiCallLogId(),
            invocation.resolvedModelId()
        );
        usageAccountingService.record(AiUsageCommand.requestDerived(
            context, AiUsageMetric.CALL, "1", Map.of(), observedAt
        ));
        usageAccountingService.record(AiUsageCommand.resultMeasured(
            context,
            AiUsageMetric.VIDEO_SECOND,
            String.valueOf(task.durationSeconds),
            Map.of("resolution", task.resolution, "aspectRatio", task.aspectRatio),
            observedAt
        ));
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(
            task.executionId,
            Set.of(AiUsageMetric.CALL, AiUsageMetric.VIDEO_SECOND)
        );
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", task.executionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist video cost summary.", exception);
        }
    }

    private void updateExecutionPhase(Long executionId, String phase, int progress) {
        executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("phase", phase)
            .set("progress", progress)
            .set("updated_at", LocalDateTime.now())
            .eq("id", executionId));
    }

    private void markExecutionRunning(AiVideoTaskEntity task) {
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(task.executionId);
        execution.status = "RUNNING";
        execution.phase = "VIDEO_SUBMIT";
        execution.progress = 5;
        execution.startedAt = LocalDateTime.now();
        execution.updatedAt = execution.startedAt;
        executionTaskMapper.updateById(execution);
    }

    private void settleExecution(
        AiVideoTaskEntity task,
        AiSettlementOutcome outcome,
        Long attemptId,
        Long callLogId,
        String status
    ) {
        if (task.executionId == null) return;
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(task.executionId);
        if (reservation != null && "RESERVED".equals(reservation.status)) {
            pointSettlementService.finalizeOutcome(
                reservation.id,
                outcome,
                outcome == AiSettlementOutcome.SUCCESS
                    ? Map.of(AiUsageMetric.VIDEO_SECOND, BigDecimal.valueOf(task.durationSeconds))
                    : Map.of(),
                attemptId,
                callLogId,
                "execution:%d:v1:%s".formatted(task.executionId, outcome.name().toLowerCase())
            );
        }
        AiExecutionTaskEntity execution = executionTaskMapper.selectById(task.executionId);
        execution.status = status;
        if ("SUCCEEDED".equals(status)) execution.progress = 100;
        execution.resultType = "AI_VIDEO_TASK";
        execution.resultId = task.id;
        execution.completedAt = LocalDateTime.now();
        execution.updatedAt = execution.completedAt;
        AiPointReservationEntity settled = reservationMapper.selectByExecutionId(task.executionId);
        if (settled != null) {
            execution.pointSettlementStatus = settled.status;
            execution.reservedPoints = settled.reservedPoints;
            execution.settledPoints = settled.settledPoints;
            execution.releasedPoints = settled.releasedPoints;
        }
        executionTaskMapper.updateById(execution);
    }

    private void recordOperation(TenantContext context, Long projectId, String operation, Long resourceId, HttpServletRequest request) {
        operationLogService.record(context.userId(), context.tenantId(), operation, resourceId, OperationResult.SUCCESS, request);
        recordProjectLog(context, projectId, operation, "AI_VIDEO", resourceId, null, null, request);
    }

    private void recordProjectLog(
        TenantContext context,
        Long projectId,
        String operationType,
        String resourceType,
        Long resourceId,
        String beforeData,
        String afterData,
        HttpServletRequest request
    ) {
        ProjectOperationLogEntity log = new ProjectOperationLogEntity();
        log.tenantId = context.tenantId();
        log.projectId = projectId;
        log.userId = context.userId();
        log.operationType = operationType;
        log.resourceType = resourceType;
        log.resourceId = resourceId;
        log.beforeData = beforeData;
        log.afterData = afterData;
        log.ip = request == null ? null : request.getRemoteAddr();
        log.userAgent = request == null ? null : request.getHeader("User-Agent");
        log.createdAt = LocalDateTime.now();
        projectOperationLogMapper.insert(log);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
