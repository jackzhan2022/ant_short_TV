package com.antshorttv.video;

import com.antshorttv.ai.AiVideoCallLogEntity;
import com.antshorttv.ai.AiVideoCallLogMapper;
import com.antshorttv.ai.AiSecretCodec;
import com.antshorttv.ai.AiServiceConfigEntity;
import com.antshorttv.ai.AiServiceConfigMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.material.VideoMaterialEntity;
import com.antshorttv.material.VideoMaterialMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.project.ProjectOperationLogEntity;
import com.antshorttv.project.ProjectOperationLogMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.script.StoryboardEntity;
import com.antshorttv.script.StoryboardMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiVideoTaskService {
    private static final List<String> CANCELABLE_STATUSES = List.of("PENDING", "SUBMITTING", "GENERATING");
    private static final List<Integer> SUPPORTED_DURATIONS = List.of(5, 8, 10);
    private static final List<String> SUPPORTED_ASPECT_RATIOS = List.of("9:16", "16:9", "1:1");

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final StoryboardMapper storyboardMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiVideoTaskMapper aiVideoTaskMapper;
    private final AiVideoResultMapper aiVideoResultMapper;
    private final VideoMaterialMapper materialMapper;
    private final AiVideoCallLogMapper aiCallLogMapper;
    private final ProjectOperationLogMapper projectOperationLogMapper;
    private final TenantContextResolver tenantContextResolver;
    private final RbacPermissionService rbacPermissionService;
    private final OperationLogService operationLogService;
    private final MaterialFileAccessService materialFileAccessService;
    private final ObjectStorageService objectStorageService;
    private final AiSecretCodec aiSecretCodec;
    private final TeamPointService teamPointService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final int maxConcurrentPerTenant;
    private final int taskTimeoutMinutes;
    private final int pollRetryLimit;
    private final int dueTaskBatchSize;
    private final Path storageRoot;

    public AiVideoTaskService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        StoryboardMapper storyboardMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        AiVideoTaskMapper aiVideoTaskMapper,
        AiVideoResultMapper aiVideoResultMapper,
        VideoMaterialMapper materialMapper,
        AiVideoCallLogMapper aiCallLogMapper,
        ProjectOperationLogMapper projectOperationLogMapper,
        TenantContextResolver tenantContextResolver,
        RbacPermissionService rbacPermissionService,
        OperationLogService operationLogService,
        MaterialFileAccessService materialFileAccessService,
        ObjectStorageService objectStorageService,
        AiSecretCodec aiSecretCodec,
        TeamPointService teamPointService,
        ObjectMapper objectMapper,
        @Value("${ai.video.max-concurrent-per-tenant:3}") int maxConcurrentPerTenant,
        @Value("${ai.video.task-timeout-minutes:20}") int taskTimeoutMinutes,
        @Value("${ai.video.poll-retry-limit:3}") int pollRetryLimit,
        @Value("${ai.video.due-task-batch-size:20}") int dueTaskBatchSize,
        @Value("${ai.video.storage-root:storage}") String storageRoot
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.storyboardMapper = storyboardMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiVideoTaskMapper = aiVideoTaskMapper;
        this.aiVideoResultMapper = aiVideoResultMapper;
        this.materialMapper = materialMapper;
        this.aiCallLogMapper = aiCallLogMapper;
        this.projectOperationLogMapper = projectOperationLogMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.rbacPermissionService = rbacPermissionService;
        this.operationLogService = operationLogService;
        this.materialFileAccessService = materialFileAccessService;
        this.objectStorageService = objectStorageService;
        this.aiSecretCodec = aiSecretCodec;
        this.teamPointService = teamPointService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.maxConcurrentPerTenant = maxConcurrentPerTenant;
        this.taskTimeoutMinutes = taskTimeoutMinutes;
        this.pollRetryLimit = pollRetryLimit;
        this.dueTaskBatchSize = dueTaskBatchSize;
        this.storageRoot = Path.of(storageRoot);
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
        AiServiceConfigEntity serviceConfig = resolveVideoService(tenantId, request.serviceConfigId());
        String requestHash = requestHash(request, serviceConfig.getId(), firstFrameUrl);
        AiVideoTaskEntity duplicate = aiVideoTaskMapper.selectActiveDuplicate(tenantId, projectId, requestHash);
        if (duplicate != null) {
            return response(duplicate);
        }
        if (aiVideoTaskMapper.countActiveByTenant(tenantId) >= maxConcurrentPerTenant) {
            throw new BusinessException(ErrorCode.AI_VIDEO_CONCURRENCY_LIMIT_EXCEEDED, "当前团队视频生成并发已达上限，请稍后再试。");
        }
        teamPointService.consumeForAi(context, 1, "STORYBOARD_VIDEO_GENERATION", null, "AI 视频生成消耗积分");
        LocalDateTime now = LocalDateTime.now();

        AiVideoTaskEntity task = new AiVideoTaskEntity();
        task.tenantId = tenantId;
        task.projectId = projectId;
        task.storyboardId = storyboard.id;
        task.serviceConfigId = serviceConfig.getId();
        task.providerCode = serviceConfig.getProvider();
        task.model = serviceConfig.getModel();
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

        submitTask(task, serviceConfig);
        aiVideoTaskMapper.updateById(task);
        recordAiCall(context.userId(), context.tenantId(), task, "SUBMIT_SUCCESS", "externalTaskId=" + task.externalTaskId, 0L, null);
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
        recordOperation(context, projectId, "CANCEL_AI_VIDEO_TASK", task.id, servletRequest);
        return response(task);
    }

    @Transactional
    public AiVideoTaskResponse regenerate(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        AiVideoTaskEntity source = requireTask(tenantId, projectId, taskId);
        CreateAiVideoTaskRequest request = new CreateAiVideoTaskRequest(
            source.storyboardId,
            source.serviceConfigId,
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

    private AiServiceConfigEntity resolveVideoService(Long tenantId, Long serviceConfigId) {
        QueryWrapper<AiServiceConfigEntity> query = new QueryWrapper<AiServiceConfigEntity>()
            .eq("service_type", "VIDEO")
            .eq("enabled", true)
            .isNull("deleted_at");
        if (serviceConfigId != null) {
            query.eq("id", serviceConfigId);
            AiServiceConfigEntity specified = aiServiceConfigMapper.selectOne(query);
            if (specified == null) {
                throw new BusinessException(ErrorCode.AI_VIDEO_SERVICE_UNAVAILABLE, "当前视频服务不可用。");
            }
            return specified;
        }
        AiServiceConfigEntity defaultConfig = aiServiceConfigMapper.selectOne(query.clone().eq("is_default", true).last("limit 1"));
        if (defaultConfig != null) {
            return defaultConfig;
        }
        AiServiceConfigEntity fallback = aiServiceConfigMapper.selectOne(query.orderByDesc("priority").orderByDesc("id").last("limit 1"));
        if (fallback == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_SERVICE_UNAVAILABLE, "未配置可用视频服务。");
        }
        return fallback;
    }

    private void submitTask(AiVideoTaskEntity task, AiServiceConfigEntity serviceConfig) {
        LocalDateTime now = LocalDateTime.now();
        task.status = AiVideoTaskStatus.SUBMITTING.name();
        task.updatedAt = now;
        task.submittedAt = now;
        try {
            SubmitOutcome outcome = shouldUseLocalMock(serviceConfig)
                ? new SubmitOutcome("mock-video-" + UUID.randomUUID(), "ACCEPTED")
                : submitExternalTask(task, serviceConfig);
            task.externalTaskId = outcome.externalTaskId();
            task.externalStatus = outcome.externalStatus();
            task.status = AiVideoTaskStatus.GENERATING.name();
            task.startedAt = now;
            task.nextPollAt = now;
        } catch (Exception exception) {
            task.status = AiVideoTaskStatus.FAILED.name();
            task.externalStatus = "SUBMIT_FAILED";
            task.errorMessage = "服务商提交失败：" + exception.getMessage();
            task.completedAt = now;
        }
        task.updatedAt = now;
    }

    private SubmitOutcome submitExternalTask(AiVideoTaskEntity task, AiServiceConfigEntity serviceConfig) throws Exception {
        String endpoint = blankToNull(serviceConfig.getEndpoint());
        if (endpoint == null) {
            throw new IllegalStateException("视频服务未配置提交接口。");
        }
        String payload = objectMapper.writeValueAsString(Map.of(
            "model", task.model,
            "prompt", task.prompt,
            "negativePrompt", task.negativePrompt == null ? "" : task.negativePrompt,
            "firstFrameUrl", task.firstFrameUrl,
            "durationSeconds", task.durationSeconds,
            "aspectRatio", task.aspectRatio,
            "resolution", task.resolution == null ? "STANDARD" : task.resolution,
            "cameraMovement", task.cameraMovement == null ? "" : task.cameraMovement,
            "motionStrength", task.motionStrength == null ? "MEDIUM" : task.motionStrength,
            "randomSeed", task.randomSeed == null ? "" : task.randomSeed
        ));
        HttpRequest request = HttpRequest.newBuilder(buildUri(serviceConfig.getBaseUrl(), endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiSecretCodec.decrypt(serviceConfig.getApiKeyCipher()))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        String externalTaskId = firstText(body, "externalTaskId", "external_task_id", "taskId", "task_id", "id");
        if (externalTaskId == null) {
            throw new IllegalStateException("服务商未返回任务 ID。");
        }
        String externalStatus = firstText(body, "status", "externalStatus", "external_status");
        return new SubmitOutcome(externalTaskId, externalStatus == null ? "ACCEPTED" : externalStatus);
    }

    private void processTask(AiVideoTaskEntity task, Long userId) {
        if (!isActiveTask(task)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (isTimedOut(task, now)) {
            failTask(task, "视频生成超时。", userId, now);
            return;
        }
        try {
            QueryOutcome outcome = queryTask(task);
            task.lastPollAt = now;
            if ("SUCCEEDED".equals(outcome.status())) {
                if (aiVideoResultMapper.selectByTask(task.tenantId, task.projectId, task.id).isEmpty()) {
                    createGeneratedResult(task, outcome.videoUrl());
                }
                task.status = AiVideoTaskStatus.SUCCEEDED.name();
                task.externalStatus = "SUCCEEDED";
                task.completedAt = now;
                task.updatedAt = now;
                task.nextPollAt = null;
                aiVideoTaskMapper.updateById(task);
                long durationMs = task.submittedAt == null ? 0L : Duration.between(task.submittedAt, now).toMillis();
                recordAiCall(userId, task.tenantId, task, "GENERATE_SUCCESS", "resultCount=1", durationMs, null);
                return;
            }
            if ("FAILED".equals(outcome.status())) {
                failTask(task, outcome.errorMessage() == null ? "视频生成失败。" : outcome.errorMessage(), userId, now);
                return;
            }
            task.externalStatus = outcome.status();
            task.nextPollAt = now.plusSeconds(10);
            task.updatedAt = now;
            aiVideoTaskMapper.updateById(task);
            recordAiCall(userId, task.tenantId, task, "QUERY_RUNNING", "externalStatus=" + task.externalStatus, 0L, null);
        } catch (Exception exception) {
            int nextRetryCount = task.pollRetryCount == null ? 1 : task.pollRetryCount + 1;
            task.pollRetryCount = nextRetryCount;
            task.lastPollAt = now;
            task.updatedAt = now;
            if (nextRetryCount >= pollRetryLimit) {
                failTask(task, "外部任务查询失败：" + exception.getMessage(), userId, now);
                return;
            }
            task.nextPollAt = now.plusSeconds(10L * nextRetryCount);
            aiVideoTaskMapper.updateById(task);
            recordAiCall(userId, task.tenantId, task, "QUERY_RETRY", "retryCount=" + nextRetryCount, 0L, exception.getMessage());
        }
    }

    private QueryOutcome queryTask(AiVideoTaskEntity task) throws Exception {
        if (task.externalTaskId == null || task.externalTaskId.startsWith("mock-video-")) {
            return new QueryOutcome("SUCCEEDED", null, null);
        }
        AiServiceConfigEntity serviceConfig = aiServiceConfigMapper.selectById(task.serviceConfigId);
        if (serviceConfig == null || serviceConfig.getDeletedAt() != null || !Boolean.TRUE.equals(serviceConfig.getEnabled())) {
            return new QueryOutcome("FAILED", null, "当前视频服务不可用。");
        }
        String queryEndpoint = blankToNull(serviceConfig.getQueryEndpoint());
        if (queryEndpoint == null) {
            throw new IllegalStateException("视频服务未配置查询接口。");
        }
        String payload = objectMapper.writeValueAsString(Map.of("externalTaskId", task.externalTaskId));
        HttpRequest request = HttpRequest.newBuilder(buildUri(serviceConfig.getBaseUrl(), queryEndpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + aiSecretCodec.decrypt(serviceConfig.getApiKeyCipher()))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        String externalStatus = firstText(body, "status", "externalStatus", "external_status");
        String normalizedStatus = normalizeExternalStatus(externalStatus);
        String videoUrl = firstText(body, "videoUrl", "video_url", "url");
        String errorMessage = firstText(body, "errorMessage", "error_message", "message");
        if (AiVideoTaskStatus.SUCCEEDED.name().equals(normalizedStatus) && blankToNull(videoUrl) == null) {
            return new QueryOutcome(AiVideoTaskStatus.FAILED.name(), null, "未生成有效视频。");
        }
        return new QueryOutcome(normalizedStatus, videoUrl, errorMessage);
    }

    private void failTask(AiVideoTaskEntity task, String errorMessage, Long userId, LocalDateTime now) {
        task.status = AiVideoTaskStatus.FAILED.name();
        task.externalStatus = "FAILED";
        task.errorMessage = errorMessage;
        task.completedAt = now;
        task.updatedAt = now;
        task.nextPollAt = null;
        aiVideoTaskMapper.updateById(task);
        long durationMs = task.submittedAt == null ? 0L : Duration.between(task.submittedAt, now).toMillis();
        recordAiCall(userId, task.tenantId, task, "GENERATE_FAILED", errorMessage, durationMs, errorMessage);
    }

    private void createGeneratedResult(AiVideoTaskEntity task, String externalVideoUrl) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        AiVideoResultEntity result = new AiVideoResultEntity();
        result.tenantId = task.tenantId;
        result.projectId = task.projectId;
        result.taskId = task.id;
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
            : downloadVideoBytes(externalVideoUrl);
        if (objectStorageService.enabled()) {
            objectStorageService.upload(storagePath, bytes, "video/mp4");
            return bytes.length;
        }
        Path file = storageRoot.resolve(storagePath.substring(1));
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        return Files.size(file);
    }

    private byte[] downloadVideoBytes(String externalVideoUrl) throws Exception {
        URI uri = URI.create(externalVideoUrl);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalStateException("外部视频地址不可下载。");
        }
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body().length == 0) {
            throw new IllegalStateException("视频下载失败，HTTP " + response.statusCode());
        }
        return response.body();
    }

    private byte[] placeholderMp4Bytes() {
        return new byte[] {
            0, 0, 0, 24, 102, 116, 121, 112, 109, 112, 52, 50, 0, 0, 0, 0,
            109, 112, 52, 50, 105, 115, 111, 109, 0, 0, 0, 8, 109, 100, 97, 116
        };
    }

    private String requestHash(CreateAiVideoTaskRequest request, Long serviceConfigId, String firstFrameUrl) {
        String source = String.join("|",
            String.valueOf(serviceConfigId),
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

    private boolean shouldUseLocalMock(AiServiceConfigEntity serviceConfig) {
        String baseUrl = blankToNull(serviceConfig.getBaseUrl());
        return baseUrl == null || baseUrl.startsWith("mock://") || baseUrl.contains("example.com");
    }

    private URI buildUri(String baseUrl, String endpoint) {
        String normalizedBaseUrl = blankToNull(baseUrl);
        if (normalizedBaseUrl == null) {
            throw new IllegalStateException("视频服务未配置基础地址。");
        }
        String normalizedEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        return URI.create(normalizedBaseUrl.replaceAll("/+$", "") + normalizedEndpoint);
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
            JsonNode dataValue = node.path("data").get(field);
            if (dataValue != null && !dataValue.isNull() && !dataValue.asText().isBlank()) {
                return dataValue.asText();
            }
        }
        return null;
    }

    private String normalizeExternalStatus(String status) {
        if (status == null || status.isBlank()) {
            return AiVideoTaskStatus.GENERATING.name();
        }
        String normalized = status.trim().toUpperCase();
        if (List.of("SUCCESS", "SUCCEEDED", "COMPLETED", "DONE").contains(normalized)) {
            return AiVideoTaskStatus.SUCCEEDED.name();
        }
        if (List.of("FAILED", "FAIL", "ERROR", "CANCELED", "CANCELLED").contains(normalized)) {
            return AiVideoTaskStatus.FAILED.name();
        }
        return AiVideoTaskStatus.GENERATING.name();
    }

    private boolean isActiveTask(AiVideoTaskEntity task) {
        return CANCELABLE_STATUSES.contains(task.status);
    }

    private boolean isTimedOut(AiVideoTaskEntity task, LocalDateTime now) {
        LocalDateTime startedAt = task.submittedAt == null ? task.createdAt : task.submittedAt;
        return startedAt != null && Duration.between(startedAt, now).toMinutes() >= taskTimeoutMinutes;
    }

    private ProjectEntity requireProjectAccess(TenantContext context, Long projectId) {
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        if (rbacPermissionService.hasPermission(context, "PROJECT:VIEW")) {
            return project;
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(context.tenantId(), projectId, context.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
        }
        return project;
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

    private void recordAiCall(
        Long userId,
        Long tenantId,
        AiVideoTaskEntity task,
        String status,
        String responseSummary,
        Long durationMs,
        String errorMessage
    ) {
        AiVideoCallLogEntity log = new AiVideoCallLogEntity();
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setServiceConfigId(task.serviceConfigId);
        log.setProvider(task.providerCode);
        log.setServiceType("VIDEO");
        log.setModel(task.model);
        log.setBusinessScene("STORYBOARD_VIDEO_GENERATION");
        log.setRequestSummary("taskId=%d,storyboardId=%d".formatted(task.id, task.storyboardId));
        log.setResponseSummary(responseSummary);
        log.setStatus(status);
        log.setErrorMessage(errorMessage);
        log.setDurationMs(durationMs);
        log.setCreatedAt(LocalDateTime.now());
        aiCallLogMapper.insert(log);
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

    private record SubmitOutcome(String externalTaskId, String externalStatus) {
    }

    private record QueryOutcome(String status, String videoUrl, String errorMessage) {
    }
}
