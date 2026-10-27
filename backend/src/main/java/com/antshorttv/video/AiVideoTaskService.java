package com.antshorttv.video;

import com.antshorttv.accounting.AiExecutionCostSummary;
import com.antshorttv.accounting.AiAccountingJson;
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
import com.antshorttv.common.MediaPage;
import com.antshorttv.common.MediaPageQueries;
import com.antshorttv.common.PageBounds;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionClaimService;
import com.antshorttv.execution.AiExecutionPools;
import com.antshorttv.execution.AiExecutionPoolType;
import com.antshorttv.execution.AiExecutionLeaseKeeper;
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
import com.antshorttv.script.StoryboardPromptCompiler;
import com.antshorttv.script.StoryboardAssetReferenceEntity;
import com.antshorttv.script.StoryboardAssetReferenceRepository;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AiVideoTaskService {
    @org.springframework.beans.factory.annotation.Autowired private com.antshorttv.script.PropProductionService propProduction;
    private static final Logger LOGGER = LoggerFactory.getLogger(AiVideoTaskService.class);
    private static final List<String> CANCELABLE_STATUSES = List.of("PENDING", "SUBMITTING", "GENERATING");
    private static final List<Integer> SUPPORTED_DURATIONS = List.of(5, 8, 10);
    private static final List<String> SUPPORTED_ASPECT_RATIOS = List.of("9:16", "16:9", "1:1");

    @org.springframework.beans.factory.annotation.Autowired private VideoTaskPresentation videoPresentation;
    private SeedanceAvatarRecoveryService avatarRecovery;
    private com.antshorttv.avatar.ArkAssetPool avatarAssets;
    private org.springframework.jdbc.core.JdbcTemplate avatarJdbc;
    private com.antshorttv.rbac.RbacPermissionService avatarPermissions;
    private ModelAccessibleVideoUrlResolver avatarUrls;
    @org.springframework.beans.factory.annotation.Autowired
    void setAvatarRecovery(SeedanceAvatarRecoveryService recovery, com.antshorttv.avatar.ArkAssetPool assets,
        org.springframework.jdbc.core.JdbcTemplate jdbc, com.antshorttv.rbac.RbacPermissionService permissions,ModelAccessibleVideoUrlResolver urls) {
        this.avatarRecovery=recovery;this.avatarAssets=assets;this.avatarJdbc=jdbc;this.avatarPermissions=permissions;this.avatarUrls=urls;
    }
    private final ProjectAccessResolver projectAccessResolver;
    private final StoryboardMapper storyboardMapper;
    private final AiVideoTaskMapper aiVideoTaskMapper;
    private final AiVideoTaskReferenceMapper referenceMapper;
    private final AiVideoResultMapper aiVideoResultMapper;
    private final VideoMaterialMapper materialMapper;
    private final ProjectOperationLogMapper projectOperationLogMapper;
    private final TenantContextResolver tenantContextResolver;
    private final OperationLogService operationLogService;
    private final MaterialFileAccessService materialFileAccessService;
    private final ObjectStorageService objectStorageService;
    private final ObjectStorageKeyFactory objectStorageKeyFactory;
    private final AiTaskExecutionSupport executionSupport;
    private final AiVideoProviderAdapter providerAdapter;
    private final SeedanceArkVideoProviderAdapter seedanceArkProviderAdapter;
    private final StoryboardPromptCompiler promptCompiler;
    private final AvailableVideoReferences availableVideoReferences;
    private final AiVideoCoverService videoCoverService;
    private final StoryboardAssetReferenceRepository storyboardReferenceRepository;
    private final VideoTaskReferenceResolver referenceResolver;
    private final CharacterVoiceVideoReferences characterVoiceReferences;
    private final VideoReferenceSubsetSelector referenceSubsetSelector;
    private final SeedanceVideoRequestValidator seedanceValidator;
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
    private final AiExecutionClaimService claimService;
    private final AiExecutionPools executionPools;
    private final AiExecutionLeaseKeeper leaseKeeper;
    private final TransactionTemplate transactions;
    private final Duration claimTimeout;
    private final int taskTimeoutMinutes;
    private final int pollRetryLimit;
    private final int dueTaskBatchSize;
    private final boolean mockProviderEnabled;

    public AiVideoTaskService(
        ProjectAccessResolver projectAccessResolver,
        StoryboardMapper storyboardMapper,
        AiVideoTaskMapper aiVideoTaskMapper,
        AiVideoTaskReferenceMapper referenceMapper,
        AiVideoResultMapper aiVideoResultMapper,
        VideoMaterialMapper materialMapper,
        ProjectOperationLogMapper projectOperationLogMapper,
        TenantContextResolver tenantContextResolver,
        OperationLogService operationLogService,
        MaterialFileAccessService materialFileAccessService,
        ObjectStorageService objectStorageService,
        ObjectStorageKeyFactory objectStorageKeyFactory,
        AiTaskExecutionSupport executionSupport,
        AiVideoProviderAdapter providerAdapter,
        SeedanceArkVideoProviderAdapter seedanceArkProviderAdapter,
        StoryboardPromptCompiler promptCompiler,
        AvailableVideoReferences availableVideoReferences,
        AiVideoCoverService videoCoverService,
        StoryboardAssetReferenceRepository storyboardReferenceRepository,
        VideoTaskReferenceResolver referenceResolver,
        CharacterVoiceVideoReferences characterVoiceReferences,
        VideoReferenceSubsetSelector referenceSubsetSelector,
        SeedanceVideoRequestValidator seedanceValidator,
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
        AiExecutionClaimService claimService,
        AiExecutionPools executionPools,
        AiExecutionLeaseKeeper leaseKeeper,
        PlatformTransactionManager transactionManager,
        @Value("${ai.execution.claim-timeout:PT10M}") Duration claimTimeout,
        @Value("${ai.video.task-timeout-minutes:20}") int taskTimeoutMinutes,
        @Value("${ai.video.poll-retry-limit:3}") int pollRetryLimit,
        @Value("${ai.video.due-task-batch-size:20}") int dueTaskBatchSize,
        @Value("${ai.testing.mock-provider-enabled:false}") boolean mockProviderEnabled
    ) {
        this.projectAccessResolver = projectAccessResolver;
        this.storyboardMapper = storyboardMapper;
        this.aiVideoTaskMapper = aiVideoTaskMapper;
        this.referenceMapper = referenceMapper;
        this.aiVideoResultMapper = aiVideoResultMapper;
        this.materialMapper = materialMapper;
        this.projectOperationLogMapper = projectOperationLogMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.operationLogService = operationLogService;
        this.materialFileAccessService = materialFileAccessService;
        this.objectStorageService = objectStorageService;
        this.objectStorageKeyFactory = objectStorageKeyFactory;
        this.executionSupport = executionSupport;
        this.providerAdapter = providerAdapter;
        this.seedanceArkProviderAdapter = seedanceArkProviderAdapter;
        this.promptCompiler = promptCompiler;
        this.availableVideoReferences = availableVideoReferences;
        this.videoCoverService = videoCoverService;
        this.storyboardReferenceRepository = storyboardReferenceRepository;
        this.referenceResolver = referenceResolver;
        this.characterVoiceReferences = characterVoiceReferences;
        this.referenceSubsetSelector = referenceSubsetSelector;
        this.seedanceValidator = seedanceValidator;
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
        this.claimService = claimService;
        this.executionPools = executionPools;
        this.leaseKeeper = leaseKeeper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.claimTimeout = claimTimeout;
        this.taskTimeoutMinutes = taskTimeoutMinutes;
        this.pollRetryLimit = pollRetryLimit;
        this.dueTaskBatchSize = dueTaskBatchSize;
        this.mockProviderEnabled = mockProviderEnabled;
    }

    public List<AiVideoTaskResponse> list(Long tenantId, Long projectId, String status, Long storyboardId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        return videoViews(aiVideoTaskMapper.selectByProject(tenantId, projectId, null, storyboardId))
            .stream().filter(view -> status==null || status.isBlank() || status.equals(view.task().status))
            .map(view -> AiVideoTaskResponse.from(view.task(), aiVideoResultMapper.selectByTask(tenantId, projectId, view.resultTaskId())))
            .toList();
    }

    public AiVideoTaskResponse detail(Long tenantId, Long projectId, Long taskId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        return previewResponses(tenantId, projectId, List.of(task)).get(0);
    }

    public MediaPage<AiVideoTaskResponse> list(
        Long tenantId, Long projectId, String status, Long storyboardId, Integer current, Integer pageSize
    ) {
        requireProjectAccess(tenantContextResolver.requireActiveMember(tenantId), projectId);
        var query = MediaPageQueries.<AiVideoTaskEntity>projectQuery(tenantId, projectId).isNull("deleted_at")
            .isNull("avatar_recovery_source_task_id");
        if (status != null && !status.isBlank()) query.apply("id in (select t.id from ai_video_task t left join ai_execution_task e on e.id=t.execution_id and e.tenant_id=t.tenant_id "
            +VideoTaskPresentation.JOIN+" where ("+VideoTaskPresentation.DOMAIN_STATE+")={0})",status);
        if (storyboardId != null) query.eq("storyboard_id", storyboardId);
        var page = MediaPageQueries.select(aiVideoTaskMapper, query, PageBounds.of(current, pageSize), q -> q
            .select(AiVideoTaskEntity.class, f -> !Set.of("prompt", "compiled_prompt", "negative_prompt", "first_frame_url",
                "last_frame_url", "reference_images", "provider_result_metadata_json").contains(f.getColumn()))
            .orderByDesc("created_at", "id"));
        return new MediaPage<>(previewResponses(tenantId, projectId, page.data()), page.current(), page.pageSize(), page.total());
    }

    public MediaPage<AiVideoResultResponse> results(
        Long tenantId, Long projectId, Long taskId, Long storyboardId, Integer current, Integer pageSize
    ) {
        requireProjectAccess(tenantContextResolver.requireActiveMember(tenantId), projectId);
        if (taskId != null) taskId=videoViews(List.of(requireTask(tenantId, projectId, taskId))).get(0).resultTaskId();
        var query = MediaPageQueries.<AiVideoResultEntity>projectQuery(tenantId, projectId).eq("status", "ACTIVE");
        if (taskId != null) query.eq("task_id", taskId);
        if (storyboardId != null) query.eq("storyboard_id", storyboardId);
        query.inSql("task_id", "select id from ai_video_task where deleted_at is null");
        return MediaPageQueries.select(aiVideoResultMapper, query, PageBounds.of(current, pageSize)).map(AiVideoResultResponse::fromBrowser);
    }

    private List<AiVideoTaskResponse> previewResponses(Long tenantId, Long projectId, List<AiVideoTaskEntity> tasks) {
        if (tasks.isEmpty()) return List.of();
        var views=videoViews(tasks);
        var ids = views.stream().map(VideoTaskPresentation.View::resultTaskId).toList();
        var representatives = aiVideoResultMapper.selectList(MediaPageQueries.<AiVideoResultEntity>representativeQuery(
            "ai_video_result", tenantId, projectId, ids, "ACTIVE", "is_selected"));
        var grouped = representatives.stream().collect(java.util.stream.Collectors.groupingBy(result -> result.taskId));
        var counts = MediaPageQueries.resultCounts(aiVideoResultMapper, tenantId, projectId, ids, "ACTIVE", "task_id");
        return views.stream().map(view -> AiVideoTaskResponse.from(view.task(),
            grouped.getOrDefault(view.resultTaskId(), List.of()), counts.getOrDefault(view.resultTaskId(), 0L))).toList();
    }

    @Transactional
    public AiVideoTaskResponse create(
        Long tenantId,
        Long projectId,
        CreateAiVideoTaskRequest request,
        HttpServletRequest servletRequest
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = requireProjectAccess(context, projectId);
        if(propProduction!=null) propProduction.lockProject(tenantId,projectId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        AiModelRoute route = resolveVideoModel(tenantId, projectId, request.modelId());
        PreparedVideoRequest prepared = isSeedance(route)
            ? prepareSeedanceRequest(project, storyboard, route, request)
            : prepareLegacyRequest(storyboard, route, request);
        String requestHash = prepared.requestHash();
        AiVideoTaskEntity duplicate = aiVideoTaskMapper.selectActiveDuplicate(tenantId, projectId, requestHash);
        if (duplicate != null) {
            return response(duplicate);
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
        task.compiledPrompt = prepared.compiledPrompt();
        task.negativePrompt = blankToNull(request.negativePrompt());
        task.firstFrameImageId = request.firstFrameImageId() == null ? storyboard.firstFrameImageId : request.firstFrameImageId();
        task.firstFrameUrl = prepared.firstFrameUrl();
        task.lastFrameImageId = request.lastFrameImageId();
        task.lastFrameUrl = blankToNull(request.lastFrameUrl());
        task.durationSeconds = prepared.durationSeconds();
        task.aspectRatio = prepared.aspectRatio();
        task.resolution = prepared.resolution();
        task.generateAudio = prepared.generateAudio();
        task.watermark = prepared.watermark();
        task.requestSnapshotJson = prepared.snapshotJson();
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
        persistReferences(task, prepared.references(), now);
        if(avatarAssets!=null)avatarAssets.pinVideo(task.id,videoSnapshot(task));

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
                "video-task:" + task.id,
                UUID.randomUUID().toString(),
                true,
                "{\"storyboardId\":%d}".formatted(task.storyboardId)
            ),
            videoAuthorizedUsage(task, prepared.billingDurationSeconds()),
            videoDimensions(task)
        );
        task.executionId = execution.id;
        aiVideoTaskMapper.updateById(task);
        recordOperation(context, projectId, "CREATE_AI_VIDEO_TASK", task.id, servletRequest);
        recordProjectLog(context, projectId, "AI_VIDEO_TASK_CREATE", "AI_VIDEO_TASK", task.id, null, task.status, servletRequest);
        return response(task);
    }

    public AiVideoTaskResponse poll(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        scheduleTask(task, context.userId());
        recordOperation(context, projectId, "POLL_AI_VIDEO_TASK", task.id, servletRequest);
        return response(requireTask(tenantId, projectId, taskId));
    }

    public void pollDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        recoverExpiredVideoClaims(now);
        scheduleBatch(aiVideoTaskMapper.selectDueSubmissions(now, dueTaskBatchSize),
            AiExecutionPoolType.VIDEO_SUBMIT, task -> {
                AiExecutionClaim claim = admitSubmission(task);
                if (claim != null) submitTask(task, claim);
            });
        scheduleBatch(aiVideoTaskMapper.selectDueQueries(now, dueTaskBatchSize),
            AiExecutionPoolType.VIDEO_QUERY, task -> runQueryCandidate(task, task.createdBy));
    }

    private void scheduleTask(AiVideoTaskEntity task, Long userId) {
        if (!isActiveTask(task)) return;
        if (blankToNull(task.externalTaskId) == null) {
            if (AiVideoTaskStatus.PENDING.name().equals(task.status)) scheduleSubmission(task);
            return;
        }
        AiExecutionPools.Slot slot = executionPools.tryAcquire(AiExecutionPoolType.VIDEO_QUERY);
        if (slot == null) return;
        try (slot) {
            AiVideoTaskEntity owned = claimQueryTask(task, false);
            if (owned == null) return;
            try {
                slot.submit(() -> {
                    try { processTask(owned, userId); }
                    finally { clearDomainClaim(owned); }
                });
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                clearDomainClaim(owned);
                LOGGER.debug("Video query executor unavailable for task {}", task.id, rejected);
            }
        }
    }

    private void scheduleBatch(List<AiVideoTaskEntity> tasks, AiExecutionPoolType type,
        Consumer<AiVideoTaskEntity> work) {
        var candidates = new ConcurrentLinkedQueue<>(tasks);
        for (int worker = 0; worker < tasks.size() && !candidates.isEmpty(); worker++) {
            AiExecutionPools.Slot slot = executionPools.tryAcquire(type);
            if (slot == null) return;
            try (slot) {
                // Candidates remain unclaimed until a reserved worker reaches them.
                slot.submit(() -> {
                    AiVideoTaskEntity task;
                    while (slot.canContinue() && (task = candidates.poll()) != null) {
                        try {
                            work.accept(task);
                        } catch (RuntimeException exception) {
                            LOGGER.warn("Video {} processing failed for task {}", type, task.id, exception);
                        }
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                LOGGER.debug("Video executor unavailable for {}", type, rejected);
                return;
            }
        }
    }

    private AiVideoTaskEntity claimQueryTask(AiVideoTaskEntity task, boolean respectNextPoll) {
        AiTaskExecutionSupport.ClaimResult claim = executionSupport.claimAiVideoTask(
            task.id, task.status, "VIDEO_QUERY", Duration.ofMinutes(taskTimeoutMinutes));
        if (!claim.claimed()) return null;
        AiVideoTaskEntity owned = requireOwnedTask(task.id, claim);
        if (respectNextPoll && owned.nextPollAt != null && owned.nextPollAt.isAfter(LocalDateTime.now())) {
            clearDomainClaim(owned);
            return null;
        }
        // Move a selected task behind its peers before processing it.
        aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
            .set("next_poll_at", LocalDateTime.now().plusSeconds(10))
            .eq("id", owned.id).eq("execution_token", owned.executionToken));
        return owned;
    }

    private AiVideoTaskEntity requireOwnedTask(Long taskId, AiTaskExecutionSupport.ClaimResult claim) {
        AiVideoTaskEntity owned = aiVideoTaskMapper.selectById(taskId);
        if (owned == null || !java.util.Objects.equals(owned.executionToken, claim.executionToken())
            || !java.util.Objects.equals(owned.executionVersion, claim.executionVersion())) {
            throw new IllegalStateException("Video task execution claim was lost: " + taskId);
        }
        return owned;
    }

    private void runQueryCandidate(AiVideoTaskEntity task, Long userId) {
        AiVideoTaskEntity owned = claimQueryTask(task, true);
        if (owned == null) return;
        try { processTask(owned, userId); }
        finally { clearDomainClaim(owned); }
    }

    private AiExecutionClaim admitSubmission(AiVideoTaskEntity task) {
        AiExecutionClaim claim = transactions.execute(status -> {
            // Lock the domain row first, matching cancellation's lock order.
            AiTaskExecutionSupport.ClaimResult domain = executionSupport.claimAiVideoTask(
                task.id, "PENDING", "VIDEO_SUBMIT", claimTimeout);
            if (!domain.claimed()) return null;
            applyDomainClaim(task, domain, "VIDEO_SUBMIT");
            AiExecutionClaim admitted = claimService.claim(task.executionId, UUID.randomUUID().toString(),
                LocalDateTime.now(), claimTimeout);
            if (admitted == null) {
                clearDomainClaim(task);
                return null;
            }
            task.status = "SUBMITTING";
            task.submittedAt = LocalDateTime.now();
            task.updatedAt = task.submittedAt;
            saveOwnedTask(task);
            // An interrupted submission may have reached the provider; never automatically resubmit it.
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("retryable", false).eq("id", task.executionId)
                .eq("claim_token", admitted.claimToken()));
            return admitted;
        });
        if (claim == null) {
            aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
                .set("next_poll_at", LocalDateTime.now().plusSeconds(10))
                .eq("id", task.id).eq("status", "PENDING").isNull("execution_token"));
            return null;
        }
        return claim;
    }

    private void scheduleSubmission(AiVideoTaskEntity task) {
        AiExecutionPools.Slot slot = executionPools.tryAcquire(AiExecutionPoolType.VIDEO_SUBMIT);
        if (slot == null) return;
        try (slot) {
            AiExecutionClaim claim = admitSubmission(task);
            if (claim == null) return;
            try {
                slot.submit(() -> submitTask(task, claim));
            } catch (java.util.concurrent.RejectedExecutionException rejected) {
                transactions.executeWithoutResult(status -> {
                    claimService.defer(claim, "Video submission executor unavailable.", LocalDateTime.now());
                    task.status = "PENDING";
                    saveOwnedTask(task);
                    clearDomainClaim(task);
                });
                LOGGER.debug("Video submission executor unavailable for task {}", task.id, rejected);
            }
        }
    }

    private void applyDomainClaim(AiVideoTaskEntity task, AiTaskExecutionSupport.ClaimResult claim, String phase) {
        task.executionToken = claim.executionToken();
        task.executionVersion = claim.executionVersion();
        task.executionPhase = phase;
    }

    private void saveOwnedTask(AiVideoTaskEntity task) {
        if (aiVideoTaskMapper.update(task, new UpdateWrapper<AiVideoTaskEntity>()
            .eq("id", task.id).eq("execution_token", task.executionToken)
            .eq("execution_version", task.executionVersion).isNull("deleted_at")) != 1) {
            throw new IllegalStateException("Video task execution claim was lost: " + task.id);
        }
    }

    private void clearDomainClaim(AiVideoTaskEntity task) {
        aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
            .set("execution_token", null).set("execution_phase", null)
            .set("heartbeat_at", null).set("execution_timeout_at", null)
            .eq("id", task.id).eq("execution_token", task.executionToken)
            .eq("execution_version", task.executionVersion));
    }

    private void recoverExpiredVideoClaims(LocalDateTime now) {
        // A queued cancellation can crash after its short claim transaction commits.
        aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
            .set("execution_token", null).set("execution_phase", null)
            .set("heartbeat_at", null).set("execution_timeout_at", null)
            .eq("status", "PENDING").isNull("external_task_id").le("execution_timeout_at", now));
        aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
            .set("execution_token", null).set("execution_phase", null)
            .set("heartbeat_at", null).set("execution_timeout_at", null)
            .isNotNull("external_task_id").le("execution_timeout_at", now));
        for (AiVideoTaskEntity task : aiVideoTaskMapper.selectList(new QueryWrapper<AiVideoTaskEntity>()
            .eq("status", "SUBMITTING").isNull("external_task_id")
            .le("execution_timeout_at", now).last("limit " + dueTaskBatchSize))) {
            transactions.executeWithoutResult(status -> {
                int claimed = aiVideoTaskMapper.update(null, new UpdateWrapper<AiVideoTaskEntity>()
                    .set("status", "FAILED").set("external_status", "SUBMIT_UNKNOWN")
                    .set("error_message", "视频提交结果未知，请核查服务商记录后重试。")
                    .set("completed_at", now).set("execution_token", null)
                    .set("execution_timeout_at", null).eq("id", task.id)
                    .eq("status", "SUBMITTING").isNull("external_task_id")
                    .le("execution_timeout_at", now));
                if (claimed == 1) settleExecution(task, AiSettlementOutcome.TRANSPORT_UNKNOWN, null, null, "FAILED");
            });
        }
    }

    public AiVideoTaskResponse cancel(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity snapshot = requireTask(tenantId, projectId, taskId);
        var view=videoViews(List.of(snapshot)).get(0);
        if(!view.resultTaskId().equals(taskId)) {
            cancel(tenantId,projectId,view.resultTaskId(),servletRequest);
            return response(snapshot);
        }
        if (AiVideoTaskStatus.CANCELED.name().equals(snapshot.status)) {
            return response(snapshot);
        }
        if (!CANCELABLE_STATUSES.contains(snapshot.status)) {
            throw new BusinessException(ErrorCode.AI_VIDEO_TASK_STATUS_INVALID, "当前任务状态不可取消。");
        }
        AiTaskExecutionSupport.ClaimResult claim = executionSupport.claimAiVideoTask(
            snapshot.id, snapshot.status, "VIDEO_CANCEL", claimTimeout);
        if (!claim.claimed()) {
            throw new BusinessException(ErrorCode.AI_VIDEO_TASK_STATUS_INVALID, "任务正在处理，请稍后取消。");
        }
        AiVideoTaskEntity task = requireOwnedTask(snapshot.id, claim);
        try {
            cancelAcceptedSeedanceTask(task);
        } catch (RuntimeException exception) {
            clearDomainClaim(task);
            throw exception;
        }
        task.status = AiVideoTaskStatus.CANCELED.name();
        task.externalStatus = "CANCELED";
        task.completedAt = LocalDateTime.now();
        task.updatedAt = task.completedAt;
        transactions.executeWithoutResult(status -> {
            saveOwnedTask(task);
            settleExecution(task, task.externalTaskId == null ? AiSettlementOutcome.PRE_CALL_CANCELED
                : AiSettlementOutcome.TRANSPORT_UNKNOWN, null, null, "CANCELED");
            clearDomainClaim(task);
        });
        recordOperation(context, projectId, "CANCEL_AI_VIDEO_TASK", task.id, servletRequest);
        return response(task);
    }

    private void cancelAcceptedSeedanceTask(AiVideoTaskEntity task) {
        if (!com.antshorttv.ai.VolcengineProviderCodes.isArk(task.providerCode)
            || task.externalTaskId == null || task.externalTaskId.isBlank()) {
            return;
        }
        AiExecutionAttemptEntity attempt = startExecutionAttempt(task, "VIDEO_CANCEL");
        try {
            AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation = invocationService.invokeProviderNative(
                invocationRequest(task, attempt),
                "externalTaskId=" + task.externalTaskId,
                route -> {
                    seedanceArkProviderAdapter.cancel(
                        route.providerConfig(), route.model(), task.externalTaskId, attempt.idempotencyKey);
                    return com.antshorttv.ai.AiProviderExecutionOutcome.completed(
                        new AiVideoProviderAdapter.VideoResult("CANCELED", null, null), null);
                }
            );
            finishExecutionAttempt(attempt, invocation, "SUCCEEDED", false, null, null);
        } catch (AiGatewayException exception) {
            finishExecutionAttempt(attempt, exception, "FAILED", true);
            throw exception;
        }
    }

    @Transactional
    public AiVideoTaskResponse regenerate(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        AiVideoTaskEntity source = requireTask(tenantId, projectId, taskId);
        if(avatarRecovery!=null) {
            var recovering=avatarRecovery.get(tenantId,projectId,taskId);
            if(recovering!=null) {
                if(recovering.get("retryTaskId") instanceof Number child) {
                    var recovered=requireTask(tenantId,projectId,child.longValue());
                    if(!taskId.equals(recovered.avatarRecoverySourceTaskId))
                        throw new BusinessException(ErrorCode.AI_VIDEO_TASK_STATUS_INVALID,"关联的视频任务已不可用。");
                    if(CANCELABLE_STATUSES.contains(recovered.status)) return response(recovered);
                    source=recovered;
                }
                if(java.util.Set.of("PENDING","RUNNING").contains(recovering.get("status")))
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR,"当前分镜正在自动准备虚拟人像素材，请等待恢复完成。");
            }
        }
        JsonNode sourceSnapshot = videoSnapshot(source);
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
            source.generateAudio,
            source.watermark,
            source.cameraMovement,
            source.motionStrength,
            source.randomSeed,
            sourceSnapshot.path("explicitTextOnly").asBoolean(
                sourceSnapshot.path("textOnly").asBoolean(false) && !sourceSnapshot.path("skipUnavailableImages").asBoolean(false)),
            sourceSnapshot.path("skipUnavailableImages").asBoolean(false)
        );
        AiVideoTaskResponse response = create(tenantId, projectId, request, servletRequest);
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        recordProjectLog(context, projectId, "AI_VIDEO_TASK_REGENERATE", "AI_VIDEO_TASK", response.id(), String.valueOf(taskId), response.status(), servletRequest);
        return response;
    }

    /** Creates a separate billed attempt from frozen inputs; never recompiles the current storyboard. */
    @Transactional
    public Long createAvatarRecoveryTask(long recoveryId,String token,JsonNode snapshot) {
        var job=avatarJdbc.queryForMap("select * from seedance_avatar_recovery where id=? for update",recoveryId);
        if(job.get("retry_task_id") instanceof Number existing)return existing.longValue();
        if(!token.equals(job.get("claim_token")) || !"RUNNING".equals(job.get("status"))
            || ((java.sql.Timestamp)job.get("claim_until")).toLocalDateTime().isBefore(LocalDateTime.now()))
            throw new com.antshorttv.avatar.ArkAssetPool.Waiting("恢复任务执行租约已失效。");
        AiVideoTaskEntity source=requireTask(((Number)job.get("tenant_id")).longValue(),((Number)job.get("project_id")).longValue(),((Number)job.get("source_task_id")).longValue());
        var previous=org.springframework.security.core.context.SecurityContextHolder.getContext();
        var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        security.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
            new com.antshorttv.authsession.AuthenticatedUser(source.createdBy,"","avatar-recovery:"+recoveryId,LocalDateTime.now().plusMinutes(1)),null,java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(security);
        try {
            TenantContext context=tenantContextResolver.requireActiveMember(source.tenantId);
            requireProjectAccess(context,source.projectId);
            if(!avatarPermissions.hasProjectPermission(context,source.projectId,"AI_VIDEO_TASK:CREATE")
                || !avatarPermissions.hasProjectPermission(context,source.projectId,"AI_SERVICE:USE"))
                throw new BusinessException(ErrorCode.FORBIDDEN,"原任务发起人已无视频生成权限。");
            requireStoryboard(source.tenantId,source.projectId,source.storyboardId);
            resolveVideoModel(source.tenantId,source.projectId,source.modelId);
            LocalDateTime now=LocalDateTime.now();
            snapshot=avatarUrls.refreshSnapshotUrls(snapshot);
            var task=objectMapper.convertValue(source,AiVideoTaskEntity.class);
            task.id=null;task.executionId=null;task.avatarRecoverySourceTaskId=source.id;
            task.externalTaskId=null;task.externalStatus=null;task.status="PENDING";task.errorMessage=null;
            task.providerResultMetadataJson=null;task.requestSnapshotJson=snapshot.toString();task.requestHash=hash(task.requestSnapshotJson);
            task.executionToken=null;task.executionPhase=null;task.executionVersion=0;task.claimedAt=null;
            task.heartbeatAt=null;task.executionTimeoutAt=null;task.retryable=null;
            task.submittedAt=null;task.startedAt=null;task.completedAt=null;task.lastPollAt=null;task.pollRetryCount=0;
            task.createdAt=now;task.updatedAt=now;task.nextPollAt=now;task.deletedAt=null;
            aiVideoTaskMapper.insert(task);
            avatarAssets.pinVideo(task.id,snapshot);
            for(var original:referenceMapper.selectList(new QueryWrapper<AiVideoTaskReferenceEntity>().eq("task_id",source.id).orderByAsc("sort_order","id"))) {
                var ref=objectMapper.convertValue(original,AiVideoTaskReferenceEntity.class);
                ref.id=null;ref.taskId=task.id;ref.createdAt=now;
                for(JsonNode prepared:snapshot.path("references")) {
                    if(ref.mediaIndex!=null && ref.mediaType.equalsIgnoreCase(prepared.path("reference").path("mediaType").asText())
                        && prepared.path("reference").path("mediaIndex").asInt(-1)==ref.mediaIndex) {
                        String asset=prepared.path("providerAssetId").asText("");
                        ref.providerUrl=asset.isBlank()?prepared.path("providerUrl").asText(ref.providerUrl):"asset://"+asset.replaceFirst("^asset://", "");
                    }
                }
                referenceMapper.insert(ref);
            }
            int maximum=snapshot.path("billingDurationSeconds").asInt(source.durationSeconds==null?5:source.durationSeconds);
            AiExecutionTaskEntity execution=executionService.createWithReservation(new AiExecutionCreateCommand(
                task.tenantId,task.createdBy,task.projectId,"ai_video_generate","VIDEO","AI_VIDEO_TASK",task.id,task.modelId,
                "VIDEO_SUBMIT","avatar-recovery:"+source.id,UUID.randomUUID().toString(),true,
                "{\"storyboardId\":%d,\"recoverySourceTaskId\":%d}".formatted(task.storyboardId,source.id)),
                videoAuthorizedUsage(task,maximum),videoDimensions(task));
            task.executionId=execution.id;aiVideoTaskMapper.updateById(task);
            avatarJdbc.update("update seedance_avatar_recovery set status='SUCCEEDED',phase='已创建关联重试视频',retry_task_id=?,error_message=null,updated_at=now() where id=? and claim_token=?",task.id,recoveryId,token);
            recordProjectLog(context,task.projectId,"AI_VIDEO_AVATAR_RECOVERY","AI_VIDEO_TASK",task.id,String.valueOf(source.id),task.status,null);
            return task.id;
        } finally {org.springframework.security.core.context.SecurityContextHolder.setContext(previous);}
    }

    @Transactional
    public void deleteTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProjectAccess(context, projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        if (isActiveTask(task)) {
            throw new BusinessException(ErrorCode.AI_VIDEO_TASK_STATUS_INVALID, "请先取消进行中的视频任务再删除。");
        }
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
        return AiVideoResultResponse.fromBrowser(result);
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

    private List<VideoTaskPresentation.View> videoViews(List<AiVideoTaskEntity> tasks) {
        return videoPresentation==null ? tasks.stream().map(t->new VideoTaskPresentation.View(t,t.id)).toList() : videoPresentation.views(tasks);
    }

    private AiVideoTaskResponse response(AiVideoTaskEntity task) {
        var view=videoViews(List.of(task)).get(0);
        return AiVideoTaskResponse.from(view.task(), aiVideoResultMapper.selectByTask(task.tenantId, task.projectId, view.resultTaskId()));
    }

    private PreparedVideoRequest prepareLegacyRequest(
        StoryboardEntity storyboard,
        AiModelRoute route,
        CreateAiVideoTaskRequest request
    ) {
        if (!Boolean.FALSE.equals(request.generateAudio()) && characterVoiceReferences.hasVoices(
            storyboard.tenantId, storyboard.projectId, storyboard.id)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "所选视频模型不支持角色参考音色，请选择支持参考音频的 Seedance 模型。");
        }
        if (Boolean.TRUE.equals(request.textOnly())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "所选视频模型暂不支持纯文生视频，请选择 Seedance 模型。");
        }
        validateLegacyCreateRequest(request);
        String firstFrameUrl = resolveFirstFrameUrl(storyboard, request);
        int duration = request.durationSeconds() == null ? 5 : request.durationSeconds();
        String resolution = request.resolution() == null || request.resolution().isBlank()
            ? "STANDARD" : request.resolution().trim();
        return new PreparedVideoRequest(
            request.prompt().trim(),
            materialFileAccessService.publicUrl(firstFrameUrl),
            duration,
            request.aspectRatio(),
            resolution,
            request.generateAudio() == null || request.generateAudio(),
            Boolean.TRUE.equals(request.watermark()),
            null,
            requestHash(request, route.model().getId(), firstFrameUrl),
            duration,
            List.of()
        );
    }

    private PreparedVideoRequest prepareSeedanceRequest(
        ProjectEntity project,
        StoryboardEntity storyboard,
        AiModelRoute route,
        CreateAiVideoTaskRequest request
    ) {
        boolean textOnly = Boolean.TRUE.equals(request.textOnly());
        boolean skipUnavailableImages = Boolean.TRUE.equals(request.skipUnavailableImages());
        List<StoryboardAssetReferenceEntity> formalRows = textOnly ? List.of() : storyboardReferenceRepository.listActive(
            storyboard.tenantId, storyboard.projectId, storyboard.id);
        List<Map<String, Object>> omittedReferences = new ArrayList<>();
        Map<Long,Long> propVariantRemap=new java.util.HashMap<>();
        Map<Long,String> scenePropMentions=new java.util.HashMap<>();
        List<StoryboardPromptCompiler.BindingReference> formalBindings = new ArrayList<>();
        for (StoryboardAssetReferenceEntity row : formalRows) {
            if ("PROP".equals(row.assetType) && row.assetId!=null && propProduction!=null) {
                var target=propProduction.target(storyboard.tenantId,storyboard.projectId,row.assetId,
                    row.variantId,storyboard.episodeId);
                if(target.scene()) {
                    var policy=propProduction.policy(storyboard.tenantId,storyboard.projectId,row.assetId);
                    boolean scenePresent=false;
                    for(JsonNode sceneId:policy.path("sceneIds")) if(formalRows.stream().anyMatch(r -> "SCENE".equals(r.assetType) && r.assetId!=null && r.assetId==sceneId.asLong())) scenePresent=true;
                    if(!scenePresent) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"场景内道具需要先绑定对应场景参考图。");
                    if(row.variantId!=null) scenePropMentions.put(row.variantId,row.sourceName==null?policy.path("sourceName").asText():row.sourceName);
                    continue;
                }
                if(!propProduction.policy(storyboard.tenantId,storyboard.projectId,row.assetId).isEmpty()) {
                    if(target.unresolved()||target.variantId()==null) throw new BusinessException(ErrorCode.VALIDATION_ERROR,"道具关键版本未确定，请先选择正确的视觉形象。");
                    if(row.variantId!=null) propVariantRemap.put(row.variantId,target.variantId());
                    row.variantId=target.variantId();row.resolutionStatus="RESOLVED";
                }
            }
            if (!"RESOLVED".equals(row.resolutionStatus) || row.variantId == null) {
                if (skipUnavailableImages) {
                    omittedReferences.add(objectMapper.convertValue(new VideoReferenceSubsetSelector.OmittedReference(
                        "IMAGE", "ASSET_VISUAL_VARIANT", row.variantId, row.variantId, row.assetType, row.assetId,
                        row.sourceName, row.referenceRole, row.sortOrder, "IMAGE_UNAVAILABLE"),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
                    continue;
                }
                throw new BusinessException(ErrorCode.AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED,
                    "参考图片尚不可用，请先生成或重新绑定图片，或确认跳过缺图继续生成。");
            }
            formalBindings.add(new StoryboardPromptCompiler.BindingReference(
                row.assetType, row.assetId, row.variantId, row.sourceName,
                row.referenceRole, row.sortOrder));
        }
        StoryboardPromptCompiler.CompiledPrompt compiled;
        try {
            if (textOnly) {
                compiled = new StoryboardPromptCompiler.CompiledPrompt(request.prompt(), List.of());
            } else {
                JsonNode document;
                if (storyboard.promptDocumentJson == null || storyboard.promptDocumentJson.isBlank()) {
                    var textDocument = objectMapper.createObjectNode().put("version", 2);
                    textDocument.putArray("nodes").addObject().put("type", "text").put("text", request.prompt());
                    document = textDocument;
                } else {
                    document = objectMapper.readTree(storyboard.promptDocumentJson);
                }
                for (JsonNode node : document.path("nodes")) {
                    if(node instanceof com.fasterxml.jackson.databind.node.ObjectNode mention && "mention".equals(node.path("type").asText())
                        && "FORMAL".equals(node.path("bindingSource").asText()) && "IMAGE".equals(node.path("mediaType").asText())) {
                        long original=node.path("sourceId").asLong();
                        if(scenePropMentions.containsKey(original)) {mention.removeAll();mention.put("type","text").put("text",scenePropMentions.get(original));}
                        else if(propVariantRemap.containsKey(original)) mention.put("sourceId",propVariantRemap.get(original));
                    }
                    if (!skipUnavailableImages && "mention".equals(node.path("type").asText())
                        && "IMAGE".equals(node.path("mediaType").asText())
                        && "FORMAL".equals(node.path("bindingSource").asText())
                        && formalBindings.stream().noneMatch(binding -> binding.variantId() == node.path("sourceId").asLong())) {
                        throw new BusinessException(ErrorCode.AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED,
                            "参考图片尚不可用，请先生成或重新绑定图片，或确认跳过缺图继续生成。");
                    }
                }
                if (skipUnavailableImages) {
                    var selection = availableVideoReferences.compile(storyboard.tenantId, storyboard.projectId, document, formalBindings);
                    compiled = selection.compiled();
                    omittedReferences.addAll(selection.omitted().stream().map(item -> objectMapper.convertValue(item,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {})).toList());
                } else {
                    compiled = promptCompiler.compile(document, formalBindings);
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜提示词文档不正确。");
        }
        List<VideoTaskReferenceResolver.ResolvedReference> resolvedReferences = referenceResolver.resolve(
            storyboard.tenantId, storyboard.projectId, compiled.references());
        int duration = request.durationSeconds() == null
            ? (storyboard.durationSeconds == null ? 5 : storyboard.durationSeconds)
            : request.durationSeconds();
        String aspectRatio = firstNonBlank(request.aspectRatio(), project.aspectRatio, "9:16");
        if (!SUPPORTED_ASPECT_RATIOS.contains(aspectRatio)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "项目视频比例不受支持。");
        }
        String resolution = firstNonBlank(request.resolution(), project.videoResolution, "720p").toLowerCase();
        boolean generateAudio = request.generateAudio() == null
            ? project.videoGenerateAudio == null || project.videoGenerateAudio
            : request.generateAudio();
        boolean watermark = request.watermark() == null
            ? Boolean.TRUE.equals(project.videoWatermark)
            : request.watermark();
        var characterVoices = generateAudio
            ? characterVoiceReferences.append(storyboard.tenantId, storyboard.projectId, storyboard.id, resolvedReferences)
            : new CharacterVoiceVideoReferences.Augmentation(resolvedReferences, List.of());
        resolvedReferences = characterVoices.references();
        JsonNode constraints = seedanceValidator.constraints(route.model());
        VideoReferenceSubsetSelector.Selection selection = referenceSubsetSelector.select(
            resolvedReferences, constraints);
        List<VideoTaskReferenceResolver.ResolvedReference> references = characterVoices.selectedReferences(selection.kept());
        String compiledPrompt = characterVoices.prompt(compiled.text(), references);
        if(propProduction!=null) {
            for(var row:formalRows) if("SCENE".equals(row.assetType) && row.assetId!=null)
                compiledPrompt+=propProduction.sceneContext(storyboard.tenantId,storyboard.projectId,row.assetId);
        }
        omittedReferences.addAll(selection.omitted().stream()
            .map(item -> objectMapper.convertValue(item,
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
            .toList());
        String firstFrameUrl = references.stream()
            .filter(item -> "IMAGE".equals(item.reference().mediaType()))
            .findFirst()
            .map(VideoTaskReferenceResolver.ResolvedReference::browserDisplayUrl)
            .filter(value -> value != null && !value.isBlank())
            .orElse(null);
        seedanceValidator.validate(route.model(), duration, resolution, references);
        int billingDuration = duration == -1
            ? constraints.path("duration").path("max").asInt()
            : duration;

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("modelId", route.model().getId());
        snapshot.put("endpointId", route.model().getModelCode());
        snapshot.put("compiledPrompt", compiledPrompt);
        snapshot.put("characterVoices", characterVoices.assignments());
        snapshot.put("duration", duration);
        snapshot.put("billingDurationSeconds", billingDuration);
        snapshot.put("ratio", aspectRatio);
        snapshot.put("resolution", resolution);
        snapshot.put("generateAudio", generateAudio);
        snapshot.put("watermark", watermark);
        snapshot.put("referenceLimits", Map.of(
            "image", constraints.path("image").path("maxCount").asInt(0),
            "video", constraints.path("video").path("maxCount").asInt(0),
            "audio", constraints.path("audio").path("maxCount").asInt(0)));
        snapshot.put("textOnly", references.isEmpty());
        snapshot.put("explicitTextOnly", textOnly);
        snapshot.put("skipUnavailableImages", skipUnavailableImages);
        snapshot.put("references", references);
        snapshot.put("omittedReferences", omittedReferences);
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize video request snapshot.", exception);
        }
        return new PreparedVideoRequest(
            compiledPrompt, firstFrameUrl, duration, aspectRatio, resolution, generateAudio, watermark,
            snapshotJson, hash(snapshotJson), billingDuration, references);
    }

    private void persistReferences(
        AiVideoTaskEntity task,
        List<VideoTaskReferenceResolver.ResolvedReference> references,
        LocalDateTime now
    ) {
        for (VideoTaskReferenceResolver.ResolvedReference resolved : references) {
            StoryboardPromptCompiler.Reference source = resolved.reference();
            AiVideoTaskReferenceEntity entity = new AiVideoTaskReferenceEntity();
            entity.tenantId = task.tenantId;
            entity.projectId = task.projectId;
            entity.taskId = task.id;
            entity.storyboardId = task.storyboardId;
            entity.mediaType = source.mediaType();
            entity.mediaIndex = source.mediaIndex();
            entity.providerRole = source.providerRole();
            entity.sourceType = source.sourceType();
            entity.sourceId = source.sourceId();
            entity.variantId = source.variantId();
            entity.displayName = resolved.displayName() == null ? source.displayName() : resolved.displayName();
            entity.compiledLabel = source.compiledLabel();
            entity.objectStoragePath = resolved.objectStoragePath();
            entity.providerUrl = resolved.providerUrl();
            entity.format = resolved.format();
            entity.fileSize = resolved.fileSize();
            entity.width = resolved.width();
            entity.height = resolved.height();
            entity.durationSeconds = resolved.durationSeconds();
            entity.fps = resolved.fps();
            entity.sortOrder = source.sortOrder();
            entity.createdAt = now;
            referenceMapper.insert(entity);
        }
    }

    private void validateLegacyCreateRequest(CreateAiVideoTaskRequest request) {
        int duration = request.durationSeconds() == null ? 5 : request.durationSeconds();
        if (!SUPPORTED_DURATIONS.contains(duration)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择正确的视频时长。");
        }
        if (!SUPPORTED_ASPECT_RATIOS.contains(request.aspectRatio())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择视频比例。");
        }
    }

    private boolean isSeedance(AiModelRoute route) {
        return com.antshorttv.ai.VolcengineProviderCodes.isArk(route.provider().getCode())
            && route.model().getCode() != null
            && route.model().getCode().startsWith("SEEDANCE_");
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
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

    private void submitTask(AiVideoTaskEntity task, AiExecutionClaim claim) {
        AiExecutionAttemptEntity attempt = executionAttemptMapper.selectById(claim.attemptId());
        try (AiExecutionLeaseKeeper.Lease lease = leaseKeeper.keepAlive(claim)) {
            claimService.requireActive(claim);
            AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation = invocationService.invokeProviderNative(
                invocationRequest(task, attempt),
                "taskId=%d,storyboardId=%d".formatted(task.id, task.storyboardId),
                route -> shouldUseLocalMock(route.providerConfig())
                    ? com.antshorttv.ai.AiProviderExecutionOutcome.accepted(
                        "mock-submit-" + task.id, "mock-video-" + UUID.randomUUID(), Duration.ofSeconds(10),
                        com.antshorttv.ai.AiProviderReconciliationStatus.NOT_REQUIRED)
                    : submitProviderTask(route, task, attempt.idempotencyKey)
            );
            lease.assertOwned();
            transactions.executeWithoutResult(status -> {
                task.externalTaskId = invocation.externalTaskId();
                task.externalStatus = "ACCEPTED";
                task.status = AiVideoTaskStatus.GENERATING.name();
                task.startedAt = LocalDateTime.now();
                task.nextPollAt = task.startedAt;
                task.updatedAt = task.startedAt;
                saveOwnedTask(task);
                claimService.handoffExternal(claim, task.startedAt);
                updateExecutionPhase(task.executionId, "VIDEO_QUERY", 10);
                finishExecutionAttempt(attempt, invocation, "SUCCEEDED", false, null, null);
                clearDomainClaim(task);
            });
        } catch (RuntimeException exception) {
            LOGGER.warn("Video submission failed for task {}", task.id, exception);
            transactions.executeWithoutResult(status -> {
                task.status = "FAILED";
                task.externalStatus = "SUBMIT_UNKNOWN";
                task.errorMessage = "服务商提交失败：" + exception.getMessage();
                task.completedAt = LocalDateTime.now();
                task.updatedAt = task.completedAt;
                saveOwnedTask(task);
                Long callLogId = exception instanceof AiGatewayException gateway ? gateway.getAiCallLogId() : null;
                if (exception instanceof AiGatewayException gateway) {
                    finishExecutionAttempt(attempt, gateway, "FAILED", false);
                } else {
                    finishExecutionAttempt(attempt, null, "FAILED", false, "VIDEO_SUBMIT_UNKNOWN", exception.getMessage());
                }
                // A timeout or transport failure cannot prove that the provider rejected the request.
                boolean rejected = exception instanceof AiGatewayException gateway
                    && gateway.getErrorCode() != ErrorCode.AI_PROVIDER_TIMEOUT
                    && gateway.getErrorCode() != ErrorCode.AI_RESPONSE_INVALID
                    && gateway.getErrorCode() != ErrorCode.AI_PROVIDER_ERROR;
                settleExecution(task, rejected ? AiSettlementOutcome.PROVIDER_REJECTION
                    : AiSettlementOutcome.TRANSPORT_UNKNOWN, attempt.id, callLogId, "FAILED");
                clearDomainClaim(task);
                if(rejected && avatarRecovery!=null) {
                    task.externalStatus="REJECTED";
                    aiVideoTaskMapper.update(null,new UpdateWrapper<AiVideoTaskEntity>().set("external_status","REJECTED").eq("id",task.id));
                    if(exception instanceof AiGatewayException gateway && SeedanceRealPersonRejection.matches(gateway.getErrorCode(),task.errorMessage))avatarRecovery.enqueue(task);
                }
            });
        }
    }

    private void processTask(AiVideoTaskEntity task, Long userId) {
        AiExecutionAttemptEntity attempt = startExecutionAttempt(task, "VIDEO_QUERY");
        LocalDateTime now = LocalDateTime.now();
        if (isTimedOut(task, now)) {
            transactions.executeWithoutResult(status -> {
                failTask(task, "视频生成超时。", userId, now);
                protectUnknownQuery(task);
                finishExecutionAttempt(attempt, null, "TIMED_OUT", false, "AI_PROVIDER_TIMEOUT", "视频生成超时。");
                settleExecution(task, AiSettlementOutcome.TIMED_OUT, attempt.id, null, "TIMED_OUT");
            });
            return;
        }
        AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation = null;
        try {
            invocation = queryProviderTask(task, attempt);
            task.lastPollAt = now;
            AiVideoProviderAdapter.VideoResult providerResult = invocation.response();
            boolean succeeded = providerResult != null && "SUCCEEDED".equals(providerResult.status());
            boolean failed = providerResult != null && "FAILED".equals(providerResult.status());
            if (succeeded || failed) {
                AiInvocationResult<AiVideoProviderAdapter.VideoResult> observed = invocation;
                transactions.executeWithoutResult(status -> {
                    rememberProviderBilling(task, attempt.id, observed);
                    recordUsageAndCost(task, attempt.id, observed);
                });
            }
            // Download/upload can take minutes and must not hold a database connection.
            GeneratedVideoResult result = succeeded
                && aiVideoResultMapper.selectByTask(task.tenantId, task.projectId, task.id).isEmpty()
                ? prepareGeneratedResult(task, providerResult) : null;
            AiInvocationResult<AiVideoProviderAdapter.VideoResult> observed = invocation;
            transactions.executeWithoutResult(status -> {
                if (succeeded) {
                    if (providerResult.durationSeconds() != null) {
                        task.durationSeconds = providerResult.durationSeconds().intValue();
                    }
                    task.status = AiVideoTaskStatus.SUCCEEDED.name();
                    task.externalStatus = "SUCCEEDED";
                    task.completedAt = now;
                    task.updatedAt = now;
                    task.nextPollAt = null;
                    saveOwnedTask(task);
                    if (result != null) {
                        aiVideoResultMapper.insert(result.video());
                        videoCoverService.publish(result.cover());
                    }
                    finishExecutionAttempt(attempt, observed, "SUCCEEDED", false, null, null);
                    settleExecution(task, AiSettlementOutcome.SUCCESS, attempt.id, observed.aiCallLogId(), "SUCCEEDED");
                } else if (failed) {
                    failTask(task, providerResult.errorMessage() == null ? "视频生成失败。"
                        : providerResult.errorMessage(), userId, now);
                    invocationService.markBusinessFailure(observed.aiCallLogId(), ErrorCode.AI_PROVIDER_ERROR, task.errorMessage);
                    finishExecutionAttempt(attempt, observed, "FAILED", false, "AI_PROVIDER_ERROR", task.errorMessage);
                    settleExecution(task, AiSettlementOutcome.PROVIDER_BILLED_FAILURE,
                        attempt.id, observed.aiCallLogId(), "FAILED");
                    if(avatarRecovery!=null)avatarRecovery.enqueue(task);
                } else {
                    task.externalStatus = "RUNNING";
                    task.nextPollAt = now.plusSeconds(10);
                    task.updatedAt = now;
                    saveOwnedTask(task);
                    finishExecutionAttempt(attempt, observed, "SUCCEEDED", false, null, null);
                }
            });
        } catch (AiGatewayException exception) {
            transactions.executeWithoutResult(status -> {
                int nextRetryCount = task.pollRetryCount == null ? 1 : task.pollRetryCount + 1;
                task.pollRetryCount = nextRetryCount;
                task.lastPollAt = now;
                task.updatedAt = now;
                if (nextRetryCount >= pollRetryLimit) {
                    failTask(task, "外部任务查询失败：" + exception.getMessage(), userId, now);
                    protectUnknownQuery(task);
                    finishExecutionAttempt(attempt, exception, "FAILED", false);
                    settleExecution(task, AiSettlementOutcome.TRANSPORT_UNKNOWN,
                        attempt.id, exception.getAiCallLogId(), "FAILED");
                } else {
                    task.nextPollAt = now.plusSeconds(10L * nextRetryCount);
                    saveOwnedTask(task);
                    finishExecutionAttempt(attempt, exception, "FAILED", true);
                }
            });
        } catch (Exception exception) {
            AiInvocationResult<AiVideoProviderAdapter.VideoResult> observed = invocation;
            transactions.executeWithoutResult(status -> {
                if (observed != null) {
                    invocationService.markBusinessFailure(observed.aiCallLogId(), ErrorCode.AI_RESPONSE_INVALID, exception.getMessage());
                }
                finishExecutionAttempt(attempt, observed, "FAILED", true, "AI_RESPONSE_INVALID", exception.getMessage());
                int nextRetryCount = task.pollRetryCount == null ? 1 : task.pollRetryCount + 1;
                task.pollRetryCount = nextRetryCount;
                task.lastPollAt = now;
                task.updatedAt = now;
                if (nextRetryCount >= pollRetryLimit) {
                    failTask(task, "视频结果处理失败：" + exception.getMessage(), userId, now);
                    if(observed==null || observed.response()==null || !java.util.Set.of("SUCCEEDED","FAILED").contains(observed.response().status()))protectUnknownQuery(task);
                    settleExecution(task, AiSettlementOutcome.BUSINESS_FAILURE, attempt.id,
                        observed == null ? null : observed.aiCallLogId(), "FAILED");
                } else {
                    task.status = "GENERATING";
                    task.nextPollAt = now.plusSeconds(10L * nextRetryCount);
                    saveOwnedTask(task);
                }
            });
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
                    new AiVideoProviderAdapter.VideoResult(
                        "SUCCEEDED", null, null,
                        "{\"resolution\":\"720p\",\"ratio\":\"%s\",\"duration\":%d,\"fps\":24,\"generateAudio\":true}"
                            .formatted(task.aspectRatio, task.durationSeconds == null || task.durationSeconds == -1 ? 5 : task.durationSeconds),
                        BigDecimal.valueOf(task.durationSeconds == null || task.durationSeconds == -1 ? 5 : task.durationSeconds),
                        "720p", task.aspectRatio, 1L, BigDecimal.valueOf(24), "default", true, 1L, 1L),
                    "mock-query-" + task.id
                )
                : pollProviderTask(route, task.externalTaskId, attempt.idempotencyKey)
        );
    }

    private com.antshorttv.ai.AiProviderExecutionOutcome<AiVideoProviderAdapter.VideoResult> submitProviderTask(
        AiModelRoute route,
        AiVideoTaskEntity task,
        String idempotencyKey
    ) throws Exception {
        if (com.antshorttv.ai.VolcengineProviderCodes.isArk(route.provider().getCode())) {
            return seedanceArkProviderAdapter.submit(route.providerConfig(), route.model(), task, idempotencyKey);
        }
        return providerAdapter.submit(route.providerConfig(), route.model(), task, idempotencyKey);
    }

    private com.antshorttv.ai.AiProviderExecutionOutcome<AiVideoProviderAdapter.VideoResult> pollProviderTask(
        AiModelRoute route,
        String externalTaskId,
        String idempotencyKey
    ) throws Exception {
        if (com.antshorttv.ai.VolcengineProviderCodes.isArk(route.provider().getCode())) {
            return seedanceArkProviderAdapter.poll(route.providerConfig(), route.model(), externalTaskId, idempotencyKey);
        }
        return providerAdapter.poll(route.providerConfig(), route.model(), externalTaskId, idempotencyKey);
    }

    private void protectUnknownQuery(AiVideoTaskEntity task) {
        if(com.antshorttv.ai.VolcengineProviderCodes.isArk(task.providerCode) && task.externalTaskId!=null) {
            task.externalStatus="QUERY_UNKNOWN";saveOwnedTask(task);
        }
    }

    private void failTask(AiVideoTaskEntity task, String errorMessage, Long userId, LocalDateTime now) {
        task.status = AiVideoTaskStatus.FAILED.name();
        task.externalStatus = "FAILED";
        task.errorMessage = errorMessage;
        task.completedAt = now;
        task.updatedAt = now;
        task.nextPollAt = null;
        saveOwnedTask(task);
    }

    private GeneratedVideoResult prepareGeneratedResult(
        AiVideoTaskEntity task,
        AiVideoProviderAdapter.VideoResult providerResult
    ) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AiVideoResultEntity result = new AiVideoResultEntity();
        result.tenantId = task.tenantId;
        result.projectId = task.projectId;
        result.taskId = task.id;
        result.executionId = task.executionId;
        result.storyboardId = task.storyboardId;
        result.storagePath = objectStorageKeyFactory.projectOriginal(
            task.tenantId,
            task.projectId,
            "videos",
            task.id,
            "result",
            now.toLocalDate(),
            "mp4"
        );
        long fileSize = writeVideoFile(result.storagePath, providerResult.videoUrl());
        result.videoUrl = materialFileAccessService.publicUrl(result.storagePath);
        ArkVideoSourceLink source = ArkVideoSourceLink.read(videoJson(task.providerResultMetadataJson));
        if (source != null) {
            result.sourceVideoUrl = source.url();
            result.sourceVideoExpiresAt = LocalDateTime.ofInstant(source.expiresAt(), ZoneOffset.UTC);
        }
        result.durationSeconds = providerResult.durationSeconds() == null
            ? BigDecimal.valueOf(task.durationSeconds == null ? 5 : task.durationSeconds)
            : providerResult.durationSeconds();
        int baseHeight = switch (providerResult.resolution() == null ? task.resolution : providerResult.resolution()) {
            case "480p" -> 480;
            case "1080p" -> 1080;
            case "4k" -> 2160;
            default -> 720;
        };
        String ratio = providerResult.ratio() == null ? task.aspectRatio : providerResult.ratio();
        if ("16:9".equals(ratio)) {
            result.width = baseHeight * 16 / 9;
            result.height = baseHeight;
        } else if ("1:1".equals(ratio)) {
            result.width = baseHeight;
            result.height = baseHeight;
        } else {
            result.width = baseHeight;
            result.height = baseHeight * 16 / 9;
        }
        result.fileSize = fileSize;
        result.format = "mp4";
        result.isSelected = false;
        result.status = AiVideoResultStatus.ACTIVE.name();
        result.createdAt = now;
        result.updatedAt = now;
        return new GeneratedVideoResult(result, videoCoverService.prepare(task, result));
    }

    private record GeneratedVideoResult(AiVideoResultEntity video, com.antshorttv.aiimage.AiImageResultEntity cover) {}

    private long writeVideoFile(String storagePath, String externalVideoUrl) throws Exception {
        Path file = Files.createTempFile("ant-short-tv-ai-video-", ".mp4");
        try {
            long size;
            if (externalVideoUrl == null || externalVideoUrl.isBlank()) {
                byte[] placeholder = placeholderMp4Bytes();
                Files.write(file, placeholder);
                size = placeholder.length;
            } else {
                size = providerAdapter.downloadTo(externalVideoUrl, file);
            }
            objectStorageService.uploadFile(storagePath, file, "video/mp4");
            return size;
        } finally {
            Files.deleteIfExists(file);
        }
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
        return hash(source);
    }

    private String hash(String source) {
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
            billingCorrelation(task, "billingAttemptId", attemptId),
            billingCorrelation(task, "billingCallLogId", invocation.aiCallLogId()),
            invocation.resolvedModelId()
        );
        boolean tokenBilling = usesVideoTokens(task);
        if (tokenBilling) {
            AiVideoProviderAdapter.VideoResult result = (AiVideoProviderAdapter.VideoResult) invocation.response();
            if (result != null && result.completionTokens() != null && result.completionTokens() > 0) {
                Map<String, String> dimensions = new LinkedHashMap<>(videoDimensions(task));
                if (result.resolution() != null) dimensions.put("resolution", result.resolution().toLowerCase(java.util.Locale.ROOT));
                usageAccountingService.recordIfAbsent(AiUsageCommand.providerReported(context,
                    AiUsageMetric.OUTPUT_TOKEN, result.completionTokens().toString(), dimensions, observedAt));
            }
        } else if (invocation.response() instanceof AiVideoProviderAdapter.VideoResult result
            && "SUCCEEDED".equals(result.status())) {
            usageAccountingService.recordIfAbsent(AiUsageCommand.requestDerived(
                context, AiUsageMetric.CALL, "1", Map.of(), observedAt));
            usageAccountingService.recordIfAbsent(AiUsageCommand.resultMeasured(context, AiUsageMetric.VIDEO_SECOND,
                result.durationSeconds() == null ? String.valueOf(task.durationSeconds) : result.durationSeconds().toPlainString(),
                videoDimensions(task), observedAt));
        }
        AiExecutionCostSummary cost = usageAccountingService.priceExecution(task.executionId,
            tokenBilling ? Set.of(AiUsageMetric.OUTPUT_TOKEN) : Set.of(AiUsageMetric.CALL, AiUsageMetric.VIDEO_SECOND));
        try {
            executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
                .set("usage_cost_status", cost.status().name())
                .set("provider_cost_summary_json", objectMapper.writeValueAsString(cost.totalsByCurrency()))
                .eq("id", task.executionId));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist video cost summary.", exception);
        }
    }

    private boolean usesVideoTokens(AiVideoTaskEntity task) {
        AiPointReservationEntity reservation = reservationMapper.selectByExecutionId(task.executionId);
        // Authorization was validated against both frozen price versions when the task was created.
        return reservation != null && AiAccountingJson.read(reservation.authorizedUsageJson)
            .containsKey(AiUsageMetric.OUTPUT_TOKEN.name());
    }

    public com.antshorttv.points.AiPointEstimatePlan pointEstimateRequest(
        Long tenantId, Long projectId, JsonNode input
    ) {
        var body = input.deepCopy();
        if (body instanceof com.fasterxml.jackson.databind.node.ObjectNode object) object.remove("promptDocument");
        return pointEstimate(tenantId, projectId, objectMapper.convertValue(body, CreateAiVideoTaskRequest.class),
            input.path("promptDocument"));
    }

    public com.antshorttv.points.AiPointEstimatePlan pointEstimate(
        Long tenantId, Long projectId, CreateAiVideoTaskRequest request
    ) {
        return pointEstimate(tenantId, projectId, request, null);
    }

    private com.antshorttv.points.AiPointEstimatePlan pointEstimate(
        Long tenantId, Long projectId, CreateAiVideoTaskRequest request, JsonNode promptDocument
    ) {
        ProjectEntity project = requireProjectAccess(tenantContextResolver.requireActiveMember(tenantId), projectId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        if (promptDocument != null && promptDocument.isObject()) storyboard.promptDocumentJson = promptDocument.toString();
        AiModelRoute route = resolveVideoModel(tenantId, projectId, request.modelId());
        PreparedVideoRequest prepared = isSeedance(route)
            ? prepareSeedanceRequest(project, storyboard, route, request)
            : prepareLegacyRequest(storyboard, route, request);
        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.providerCode = route.provider().getCode();
        task.resolution = prepared.resolution();
        task.aspectRatio = prepared.aspectRatio();
        task.requestSnapshotJson = prepared.snapshotJson();
        return new com.antshorttv.points.AiPointEstimatePlan("生成分镜视频", route.model().getId(),
            videoAuthorizedUsage(task, prepared.billingDurationSeconds()), videoDimensions(task), true).paidOnly();
    }

    public com.antshorttv.points.AiPointEstimatePlan pointEstimateExisting(Long tenantId, Long projectId, Long taskId) {
        requireProjectAccess(tenantContextResolver.requireActiveMember(tenantId), projectId);
        AiVideoTaskEntity task = requireTask(tenantId, projectId, taskId);
        AiModelRoute route = resolveVideoModel(tenantId, projectId, task.modelId);
        int maximumDuration = task.durationSeconds == -1
            ? videoSnapshot(task).path("billingDurationSeconds").asInt(
                seedanceValidator.constraints(route.model()).path("duration").path("max").asInt())
            : task.durationSeconds;
        return new com.antshorttv.points.AiPointEstimatePlan("重新生成视频", task.modelId,
            videoAuthorizedUsage(task, maximumDuration), videoDimensions(task), true).paidOnly();
    }

    public Map<AiUsageMetric, BigDecimal> videoAuthorizedUsage(AiVideoTaskEntity task, int maximumDuration) {
        if (!com.antshorttv.ai.VolcengineProviderCodes.isArk(task.providerCode)) {
            return Map.of(AiUsageMetric.CALL, BigDecimal.ONE,
                AiUsageMetric.VIDEO_SECOND, BigDecimal.valueOf(maximumDuration));
        }
        int shortEdge = switch (task.resolution.toLowerCase(java.util.Locale.ROOT)) {
            case "480p" -> 480;
            case "720p" -> 720;
            case "1080p" -> 1080;
            case "4k" -> 2160;
            default -> throw new IllegalArgumentException("视频分辨率没有安全的积分预占上限。");
        };
        double ratio = 3; // Adaptive ratio is bounded by Seedance's supported aspect ratios.
        if (task.aspectRatio != null && task.aspectRatio.matches("[1-9][0-9]*:[1-9][0-9]*")) {
            String[] parts = task.aspectRatio.split(":");
            double requested = Double.parseDouble(parts[0]) / Double.parseDouble(parts[1]);
            ratio = Math.max(requested, 1 / requested);
        }
        if (maximumDuration <= 0 || maximumDuration > 60 || ratio > 3) {
            throw new IllegalArgumentException("视频生成请求没有安全的积分预占上限。");
        }
        // A square at the longest edge, rounded up by one 64px block, conservatively bounds
        // output pixels. References are added separately; actual billing always uses reported tokens.
        long edge = ((long) Math.ceil(shortEdge * ratio) / 64 + 1) * 64;
        BigDecimal pixelFrames = BigDecimal.valueOf(edge).pow(2)
            .multiply(BigDecimal.valueOf(24L * maximumDuration));
        for (JsonNode item : videoSnapshot(task).path("references")) {
            if (!"VIDEO".equalsIgnoreCase(item.path("reference").path("mediaType").asText())) continue;
            int width = item.path("width").asInt();
            int height = item.path("height").asInt();
            BigDecimal duration = item.path("durationSeconds").decimalValue();
            BigDecimal fps = item.path("fps").decimalValue().max(BigDecimal.valueOf(24));
            if (width <= 0 || height <= 0 || duration.signum() <= 0) {
                throw new IllegalArgumentException("参考视频缺少计费预占所需的尺寸或时长。");
            }
            BigDecimal referencePixels = BigDecimal.valueOf((width / 64L + 1) * 64)
                .multiply(BigDecimal.valueOf((height / 64L + 1) * 64))
                .max(BigDecimal.valueOf(edge).pow(2)).max(BigDecimal.valueOf(1280L * 1280));
            pixelFrames = pixelFrames.add(referencePixels.multiply(duration).multiply(fps));
        }
        return Map.of(AiUsageMetric.OUTPUT_TOKEN,
            pixelFrames.divide(BigDecimal.valueOf(1024), 0, java.math.RoundingMode.CEILING));
    }

    public Map<String, String> videoDimensions(AiVideoTaskEntity task) {
        boolean referenceVideo = false;
        for (JsonNode item : videoSnapshot(task).path("references")) {
            if ("VIDEO".equalsIgnoreCase(item.path("reference").path("mediaType").asText())) referenceVideo = true;
        }
        return Map.of("resolution", com.antshorttv.ai.VolcengineProviderCodes.isArk(task.providerCode)
                ? task.resolution.toLowerCase(java.util.Locale.ROOT) : task.resolution,
            "aspectRatio", task.aspectRatio, "referenceVideo", String.valueOf(referenceVideo));
    }

    private JsonNode videoSnapshot(AiVideoTaskEntity task) {
        return videoJson(task.requestSnapshotJson);
    }

    private JsonNode videoJson(String json) {
        try {
            return json == null || json.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("视频计费快照无法读取。", exception);
        }
    }

    private Map<AiUsageMetric, BigDecimal> videoActualUsage(AiVideoTaskEntity task, AiSettlementOutcome outcome) {
        if (!usesVideoTokens(task)) {
            return outcome == AiSettlementOutcome.SUCCESS ? Map.of(AiUsageMetric.CALL, BigDecimal.ONE,
                AiUsageMetric.VIDEO_SECOND, BigDecimal.valueOf(task.durationSeconds)) : Map.of();
        }
        JsonNode metadata = videoJson(task.providerResultMetadataJson);
        JsonNode tokens = metadata.path("completionTokens");
        String measuredResolution = metadata.path("resolution").asText("");
        if (!tokens.isIntegralNumber() || !tokens.canConvertToLong() || tokens.longValue() <= 0
            || (!measuredResolution.isBlank() && !measuredResolution.equalsIgnoreCase(task.resolution))) return Map.of();
        return Map.of(AiUsageMetric.OUTPUT_TOKEN, BigDecimal.valueOf(tokens.longValue()));
    }

    private void rememberProviderBilling(AiVideoTaskEntity task, Long attemptId,
        AiInvocationResult<AiVideoProviderAdapter.VideoResult> invocation) {
        JsonNode previous = videoJson(task.providerResultMetadataJson);
        var metadata = (com.fasterxml.jackson.databind.node.ObjectNode) videoJson(invocation.response().metadataJson());
        if (invocation.response().completionTokens() != null) metadata.put("completionTokens", invocation.response().completionTokens());
        if (invocation.response().resolution() != null) metadata.put("resolution", invocation.response().resolution());
        if (com.antshorttv.ai.VolcengineProviderCodes.isArk(task.providerCode)
            && "SUCCEEDED".equals(invocation.response().status())) {
            ArkVideoSourceLink source = ArkVideoSourceLink.capture(
                invocation.response().videoUrl(), metadata, previous, Instant.now());
            if (source != null) source.writeTo(metadata);
        }
        metadata.put("billingAttemptId", previous.path("billingAttemptId").asLong(attemptId));
        metadata.put("billingCallLogId", previous.path("billingCallLogId").asLong(invocation.aiCallLogId()));
        task.providerResultMetadataJson = metadata.toString();
        saveOwnedTask(task);
    }

    private Long billingCorrelation(AiVideoTaskEntity task, String field, Long fallback) {
        JsonNode value = videoJson(task.providerResultMetadataJson).path(field);
        return value.isIntegralNumber() ? Long.valueOf(value.longValue()) : fallback;
    }

    private void updateExecutionPhase(Long executionId, String phase, int progress) {
        executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("phase", phase)
            .set("progress", progress)
            .set("updated_at", LocalDateTime.now())
            .eq("id", executionId));
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
            Map<AiUsageMetric, BigDecimal> usage = videoActualUsage(task, outcome);
            AiSettlementOutcome billingOutcome = usesVideoTokens(task) && usage.isEmpty()
                && outcome != AiSettlementOutcome.PRE_CALL_CANCELED && outcome != AiSettlementOutcome.PROVIDER_REJECTION
                ? AiSettlementOutcome.TRANSPORT_UNKNOWN : outcome;
            Long billedAttemptId = billingCorrelation(task, "billingAttemptId", attemptId);
            Long billedCallLogId = billingCorrelation(task, "billingCallLogId", callLogId);
            pointSettlementService.finalizeOutcome(reservation.id, billingOutcome, usage, billedAttemptId, billedCallLogId,
                "execution:%d:attempt:%s:call:%s:%s".formatted(task.executionId, billedAttemptId, billedCallLogId,
                    billingOutcome.name().toLowerCase()));
        }
        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<AiExecutionTaskEntity> update = new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", status).set("result_type", "AI_VIDEO_TASK").set("result_id", task.id)
            .set("completed_at", now).set("updated_at", now)
            .set("claim_token", null).set("claim_expires_at", null).eq("id", task.executionId);
        if ("SUCCEEDED".equals(status)) update.set("progress", 100);
        AiPointReservationEntity settled = reservationMapper.selectByExecutionId(task.executionId);
        if (settled != null) {
            update.set("point_settlement_status", settled.status)
                .set("reserved_points", settled.reservedPoints).set("settled_points", settled.settledPoints)
                .set("released_points", settled.releasedPoints);
        }
        executionTaskMapper.update(null, update);
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

    private record PreparedVideoRequest(
        String compiledPrompt,
        String firstFrameUrl,
        int durationSeconds,
        String aspectRatio,
        String resolution,
        boolean generateAudio,
        boolean watermark,
        String snapshotJson,
        String requestHash,
        int billingDurationSeconds,
        List<VideoTaskReferenceResolver.ResolvedReference> references
    ) {
    }

}
