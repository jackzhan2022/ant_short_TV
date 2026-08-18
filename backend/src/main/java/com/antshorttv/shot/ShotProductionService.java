package com.antshorttv.shot;

import com.antshorttv.ai.AiServiceConfigEntity;
import com.antshorttv.ai.AiServiceConfigMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.VideoMaterialEntity;
import com.antshorttv.material.VideoMaterialMapper;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.project.ProjectMemberEntity;
import com.antshorttv.project.ProjectMemberMapper;
import com.antshorttv.rbac.RbacPermissionService;
import com.antshorttv.script.StoryboardEntity;
import com.antshorttv.script.StoryboardMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShotProductionService {
    private static final BigDecimal DEFAULT_NUMBER = BigDecimal.ONE;

    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;
    private final StoryboardMapper storyboardMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiVoiceTaskMapper aiVoiceTaskMapper;
    private final AiVoiceResultMapper aiVoiceResultMapper;
    private final StoryboardSubtitleMapper subtitleMapper;
    private final ShotComposeTaskMapper shotComposeTaskMapper;
    private final ShotComposeResultMapper shotComposeResultMapper;
    private final VideoMaterialMapper materialMapper;
    private final TenantContextResolver tenantContextResolver;
    private final RbacPermissionService rbacPermissionService;
    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;

    public ShotProductionService(
        ProjectMapper projectMapper,
        ProjectMemberMapper projectMemberMapper,
        StoryboardMapper storyboardMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        AiVoiceTaskMapper aiVoiceTaskMapper,
        AiVoiceResultMapper aiVoiceResultMapper,
        StoryboardSubtitleMapper subtitleMapper,
        ShotComposeTaskMapper shotComposeTaskMapper,
        ShotComposeResultMapper shotComposeResultMapper,
        VideoMaterialMapper materialMapper,
        TenantContextResolver tenantContextResolver,
        RbacPermissionService rbacPermissionService,
        OperationLogService operationLogService,
        ObjectMapper objectMapper,
        @Value("${ai.video.storage-root:storage}") String storageRoot
    ) {
        this.projectMapper = projectMapper;
        this.projectMemberMapper = projectMemberMapper;
        this.storyboardMapper = storyboardMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiVoiceTaskMapper = aiVoiceTaskMapper;
        this.aiVoiceResultMapper = aiVoiceResultMapper;
        this.subtitleMapper = subtitleMapper;
        this.shotComposeTaskMapper = shotComposeTaskMapper;
        this.shotComposeResultMapper = shotComposeResultMapper;
        this.materialMapper = materialMapper;
        this.tenantContextResolver = tenantContextResolver;
        this.rbacPermissionService = rbacPermissionService;
        this.operationLogService = operationLogService;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot);
    }

    public List<AiVoiceTaskResponse> voiceTasks(Long tenantId, Long projectId, String status, Long storyboardId) {
        TenantContext context = requireContext(tenantId, projectId);
        return aiVoiceTaskMapper.selectByProject(context.tenantId(), projectId, status, storyboardId)
            .stream()
            .map(task -> AiVoiceTaskResponse.from(task, aiVoiceResultMapper.selectByTask(tenantId, projectId, task.id)))
            .toList();
    }

    public AiVoiceTaskResponse voiceTask(Long tenantId, Long projectId, Long taskId) {
        requireContext(tenantId, projectId);
        return voiceTaskResponse(requireVoiceTask(tenantId, projectId, taskId));
    }

    @Transactional
    public AiVoiceTaskResponse cancelVoiceTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceTaskEntity task = requireVoiceTask(tenantId, projectId, taskId);
        if (!List.of(ShotTaskStatus.PENDING.name(), ShotTaskStatus.PROCESSING.name()).contains(task.status)) {
            throw new BusinessException(ErrorCode.AI_VOICE_TASK_STATUS_INVALID, "当前任务状态不可取消。");
        }
        LocalDateTime now = LocalDateTime.now();
        task.status = ShotTaskStatus.CANCELED.name();
        task.completedAt = now;
        task.updatedAt = now;
        aiVoiceTaskMapper.updateById(task);
        recordOperation(context, "CANCEL_AI_VOICE_TASK", task.id, request);
        return voiceTaskResponse(task);
    }

    @Transactional
    public AiVoiceTaskResponse regenerateVoiceTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        AiVoiceTaskEntity source = requireVoiceTask(tenantId, projectId, taskId);
        CreateAiVoiceTaskRequest body = new CreateAiVoiceTaskRequest(
            source.storyboardId,
            source.serviceConfigId,
            source.voiceType,
            source.speakerName,
            source.voiceId,
            source.textContent,
            source.speed,
            source.pitch,
            source.volume
        );
        return createVoiceTask(tenantId, projectId, body, request);
    }

    @Transactional
    public void deleteVoiceTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceTaskEntity task = requireVoiceTask(tenantId, projectId, taskId);
        task.deletedAt = LocalDateTime.now();
        task.updatedAt = task.deletedAt;
        aiVoiceTaskMapper.updateById(task);
        recordOperation(context, "DELETE_AI_VOICE_TASK", task.id, request);
    }

    public List<AiVoiceResultResponse> voiceTaskResults(Long tenantId, Long projectId, Long taskId) {
        requireContext(tenantId, projectId);
        requireVoiceTask(tenantId, projectId, taskId);
        return aiVoiceResultMapper.selectByTask(tenantId, projectId, taskId)
            .stream()
            .map(AiVoiceResultResponse::from)
            .toList();
    }

    public AiVoiceResultResponse downloadVoiceResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceResultEntity result = requireVoiceResult(tenantId, projectId, resultId);
        recordOperation(context, "DOWNLOAD_AI_VOICE_RESULT", result.id, request);
        return AiVoiceResultResponse.from(result);
    }

    @Transactional
    public AiVoiceResultResponse saveVoiceMaterial(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceResultEntity result = requireVoiceResult(tenantId, projectId, resultId);
        if (result.materialId == null) {
            result.materialId = createMaterial(
                context,
                projectId,
                "AUDIO",
                result.taskId,
                result.id,
                "语音结果-" + result.storyboardId,
                result.audioUrl,
                null,
                result.durationSeconds,
                null,
                null,
                result.format,
                result.fileSize
            );
            result.updatedAt = LocalDateTime.now();
            aiVoiceResultMapper.updateById(result);
        }
        recordOperation(context, "SAVE_AI_VOICE_RESULT_MATERIAL", result.id, request);
        return AiVoiceResultResponse.from(result);
    }

    @Transactional
    public void deleteVoiceResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceResultEntity result = requireVoiceResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        if (result.id.equals(storyboard.currentVoiceResultId)) {
            throw new BusinessException(ErrorCode.AI_VOICE_RESULT_IN_USE, "当前语音结果已被分镜引用，不能静默删除。");
        }
        result.status = ShotResultStatus.DELETED.name();
        result.updatedAt = LocalDateTime.now();
        aiVoiceResultMapper.updateById(result);
        recordOperation(context, "DELETE_AI_VOICE_RESULT", result.id, request);
    }

    @Transactional
    public AiVoiceTaskResponse createVoiceTask(Long tenantId, Long projectId, CreateAiVoiceTaskRequest request, HttpServletRequest servletRequest) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        AiServiceConfigEntity serviceConfig = resolveVoiceService(tenantId, request.serviceConfigId());
        LocalDateTime now = LocalDateTime.now();

        AiVoiceTaskEntity task = new AiVoiceTaskEntity();
        task.tenantId = tenantId;
        task.projectId = projectId;
        task.storyboardId = storyboard.id;
        task.serviceConfigId = serviceConfig.getId();
        task.providerCode = serviceConfig.getProvider();
        task.model = serviceConfig.getModel();
        task.voiceType = request.voiceType().trim();
        task.speakerName = blankToNull(request.speakerName());
        task.voiceId = request.voiceId().trim();
        task.textContent = request.textContent().trim();
        task.speed = defaultNumber(request.speed());
        task.pitch = defaultNumber(request.pitch());
        task.volume = defaultNumber(request.volume());
        task.status = ShotTaskStatus.PROCESSING.name();
        task.startedAt = now;
        task.createdBy = context.userId();
        task.createdAt = now;
        task.updatedAt = now;
        aiVoiceTaskMapper.insert(task);

        AiVoiceResultEntity result = createVoiceResult(task, estimateDuration(task.textContent), now);
        task.status = ShotTaskStatus.SUCCEEDED.name();
        task.completedAt = now;
        task.updatedAt = now;
        aiVoiceTaskMapper.updateById(task);
        recordOperation(context, "CREATE_AI_VOICE_TASK", task.id, servletRequest);
        return AiVoiceTaskResponse.from(task, List.of(result));
    }

    @Transactional
    public AiVoiceResultResponse bindVoiceResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        AiVoiceResultEntity result = requireVoiceResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        LocalDateTime now = LocalDateTime.now();
        for (AiVoiceResultEntity item : aiVoiceResultMapper.selectByTask(tenantId, projectId, result.taskId)) {
            item.isSelected = item.id.equals(result.id);
            item.updatedAt = now;
            aiVoiceResultMapper.updateById(item);
        }
        result.isSelected = true;
        result.updatedAt = now;
        aiVoiceResultMapper.updateById(result);
        storyboard.currentVoiceResultId = result.id;
        storyboard.currentAudioUrl = result.audioUrl;
        storyboard.updatedAt = now;
        storyboardMapper.updateById(storyboard);
        recordOperation(context, "BIND_AI_VOICE_RESULT_STORYBOARD", result.id, request);
        return AiVoiceResultResponse.from(result);
    }

    @Transactional
    public StoryboardSubtitleResponse createSubtitle(Long tenantId, Long projectId, CreateStoryboardSubtitleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        AiVoiceResultEntity voiceResult = request.voiceResultId() == null ? null : requireVoiceResult(tenantId, projectId, request.voiceResultId());
        BigDecimal defaultDuration = voiceResult == null || voiceResult.durationSeconds == null ? BigDecimal.valueOf(5) : voiceResult.durationSeconds;
        BigDecimal startTime = request.startTime() == null ? BigDecimal.ZERO : request.startTime();
        BigDecimal endTime = request.endTime() == null ? defaultDuration : request.endTime();
        validateSubtitleTimeline(storyboard, startTime, endTime);
        LocalDateTime now = LocalDateTime.now();
        String text = request.textContent().trim();
        List<SubtitleSegmentResponse> segments = List.of(new SubtitleSegmentResponse(text, startTime, endTime));

        StoryboardSubtitleEntity subtitle = new StoryboardSubtitleEntity();
        subtitle.tenantId = tenantId;
        subtitle.projectId = projectId;
        subtitle.storyboardId = request.storyboardId();
        subtitle.voiceResultId = request.voiceResultId();
        subtitle.subtitleType = request.subtitleType().trim();
        subtitle.content = writeJsonContent(text, segments);
        subtitle.styleConfig = writeJson(request.styleConfig() == null ? Map.of("fontSize", "MEDIUM", "position", "BOTTOM") : request.styleConfig());
        subtitle.srtUrl = subtitleStoragePath(tenantId, projectId, subtitle.id);
        subtitle.isSelected = false;
        subtitle.status = ShotResultStatus.ACTIVE.name();
        subtitle.createdBy = context.userId();
        subtitle.createdAt = now;
        subtitle.updatedAt = now;
        subtitleMapper.insert(subtitle);
        subtitle.srtUrl = writeSubtitleFile(tenantId, projectId, subtitle.id, segments, subtitle.srtUrl);
        subtitleMapper.updateById(subtitle);
        recordOperation(context, "CREATE_STORYBOARD_SUBTITLE", subtitle.id, servletRequest);
        return subtitleResponse(subtitle);
    }

    @Transactional
    public StoryboardSubtitleResponse updateSubtitle(Long tenantId, Long projectId, Long subtitleId, UpdateStoryboardSubtitleRequest request, HttpServletRequest servletRequest) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardSubtitleEntity subtitle = requireSubtitle(tenantId, projectId, subtitleId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, subtitle.storyboardId);
        SubtitleSegmentResponse currentSegment = firstSubtitleSegment(subtitle);
        BigDecimal startTime = request.startTime() == null ? currentSegment.startTime() : request.startTime();
        BigDecimal endTime = request.endTime() == null ? currentSegment.endTime() : request.endTime();
        validateSubtitleTimeline(storyboard, startTime, endTime);
        String text = request.textContent().trim();
        List<SubtitleSegmentResponse> segments = List.of(new SubtitleSegmentResponse(text, startTime, endTime));
        subtitle.content = writeJsonContent(text, segments);
        if (request.styleConfig() != null) {
            subtitle.styleConfig = writeJson(request.styleConfig());
        }
        subtitle.srtUrl = writeSubtitleFile(tenantId, projectId, subtitle.id, segments, subtitle.srtUrl);
        subtitle.updatedAt = LocalDateTime.now();
        subtitleMapper.updateById(subtitle);
        recordOperation(context, "UPDATE_STORYBOARD_SUBTITLE", subtitle.id, servletRequest);
        return subtitleResponse(subtitle);
    }

    @Transactional
    public StoryboardSubtitleResponse selectSubtitle(Long tenantId, Long projectId, Long subtitleId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardSubtitleEntity subtitle = requireSubtitle(tenantId, projectId, subtitleId);
        LocalDateTime now = LocalDateTime.now();
        for (StoryboardSubtitleEntity item : subtitleMapper.selectByStoryboard(tenantId, projectId, subtitle.storyboardId)) {
            item.isSelected = item.id.equals(subtitle.id);
            item.updatedAt = now;
            subtitleMapper.updateById(item);
        }
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, subtitle.storyboardId);
        storyboard.currentSubtitleId = subtitle.id;
        storyboard.currentSubtitleUrl = subtitle.srtUrl;
        storyboard.updatedAt = now;
        storyboardMapper.updateById(storyboard);
        subtitle.isSelected = true;
        subtitle.updatedAt = now;
        subtitleMapper.updateById(subtitle);
        recordOperation(context, "SELECT_STORYBOARD_SUBTITLE", subtitle.id, request);
        return subtitleResponse(subtitle);
    }

    public List<StoryboardSubtitleResponse> subtitles(Long tenantId, Long projectId, Long storyboardId, String status) {
        requireContext(tenantId, projectId);
        QueryWrapper<StoryboardSubtitleEntity> query = new QueryWrapper<StoryboardSubtitleEntity>()
            .eq("tenant_id", tenantId)
            .eq("project_id", projectId)
            .orderByDesc("id");
        if (storyboardId != null) {
            query.eq("storyboard_id", storyboardId);
        }
        if (status != null && !status.isBlank()) {
            query.eq("status", status);
        }
        return subtitleMapper.selectList(query)
            .stream()
            .map(this::subtitleResponse)
            .toList();
    }

    public StoryboardSubtitleResponse subtitle(Long tenantId, Long projectId, Long subtitleId) {
        requireContext(tenantId, projectId);
        return subtitleResponse(requireSubtitle(tenantId, projectId, subtitleId));
    }

    @Transactional
    public void deleteSubtitle(Long tenantId, Long projectId, Long subtitleId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardSubtitleEntity subtitle = requireSubtitle(tenantId, projectId, subtitleId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, subtitle.storyboardId);
        if (subtitle.id.equals(storyboard.currentSubtitleId)) {
            throw new BusinessException(ErrorCode.STORYBOARD_SUBTITLE_IN_USE, "当前字幕已被分镜引用，不能静默删除。");
        }
        subtitle.status = ShotResultStatus.DELETED.name();
        subtitle.updatedAt = LocalDateTime.now();
        subtitleMapper.updateById(subtitle);
        recordOperation(context, "DELETE_STORYBOARD_SUBTITLE", subtitle.id, request);
    }

    public List<ShotComposeTaskResponse> composeTasks(Long tenantId, Long projectId, String status, Long storyboardId) {
        TenantContext context = requireContext(tenantId, projectId);
        return shotComposeTaskMapper.selectByProject(context.tenantId(), projectId, status, storyboardId)
            .stream()
            .map(task -> ShotComposeTaskResponse.from(task, shotComposeResultMapper.selectByTask(tenantId, projectId, task.id)))
            .toList();
    }

    public ShotComposeTaskResponse composeTask(Long tenantId, Long projectId, Long taskId) {
        requireContext(tenantId, projectId);
        return composeTaskResponse(requireComposeTask(tenantId, projectId, taskId));
    }

    @Transactional
    public ShotComposeTaskResponse cancelComposeTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeTaskEntity task = requireComposeTask(tenantId, projectId, taskId);
        if (!List.of(ShotTaskStatus.PENDING.name(), ShotTaskStatus.PROCESSING.name()).contains(task.status)) {
            throw new BusinessException(ErrorCode.SHOT_COMPOSE_TASK_STATUS_INVALID, "当前任务状态不可取消。");
        }
        LocalDateTime now = LocalDateTime.now();
        task.status = ShotTaskStatus.CANCELED.name();
        task.completedAt = now;
        task.updatedAt = now;
        shotComposeTaskMapper.updateById(task);
        recordOperation(context, "CANCEL_SHOT_COMPOSE_TASK", task.id, request);
        return composeTaskResponse(task);
    }

    @Transactional
    public ShotComposeTaskResponse regenerateComposeTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        ShotComposeTaskEntity source = requireComposeTask(tenantId, projectId, taskId);
        Map<String, Object> config = readJsonMap(source.composeConfig);
        Boolean includeSubtitle = config.containsKey("includeSubtitle") ? Boolean.valueOf(String.valueOf(config.get("includeSubtitle"))) : Boolean.TRUE;
        BigDecimal audioVolume = config.containsKey("audioVolume") ? new BigDecimal(String.valueOf(config.get("audioVolume"))) : DEFAULT_NUMBER;
        String outputFormat = config.containsKey("outputFormat") ? String.valueOf(config.get("outputFormat")) : "mp4";
        CreateShotComposeTaskRequest body = new CreateShotComposeTaskRequest(
            source.storyboardId,
            source.voiceResultId,
            source.subtitleId,
            includeSubtitle,
            audioVolume,
            outputFormat
        );
        return createComposeTask(tenantId, projectId, body, request);
    }

    @Transactional
    public void deleteComposeTask(Long tenantId, Long projectId, Long taskId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeTaskEntity task = requireComposeTask(tenantId, projectId, taskId);
        task.deletedAt = LocalDateTime.now();
        task.updatedAt = task.deletedAt;
        shotComposeTaskMapper.updateById(task);
        recordOperation(context, "DELETE_SHOT_COMPOSE_TASK", task.id, request);
    }

    public List<ShotComposeResultResponse> composeTaskResults(Long tenantId, Long projectId, Long taskId) {
        requireContext(tenantId, projectId);
        requireComposeTask(tenantId, projectId, taskId);
        return shotComposeResultMapper.selectByTask(tenantId, projectId, taskId)
            .stream()
            .map(ShotComposeResultResponse::from)
            .toList();
    }

    public ShotComposeResultResponse downloadComposeResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeResultEntity result = requireComposeResult(tenantId, projectId, resultId);
        recordOperation(context, "DOWNLOAD_SHOT_COMPOSE_RESULT", result.id, request);
        return ShotComposeResultResponse.from(result);
    }

    @Transactional
    public ShotComposeTaskResponse createComposeTask(Long tenantId, Long projectId, CreateShotComposeTaskRequest request, HttpServletRequest servletRequest) {
        TenantContext context = requireContext(tenantId, projectId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, request.storyboardId());
        if (blankToNull(storyboard.currentVideoUrl) == null) {
            throw new BusinessException(ErrorCode.SHOT_COMPOSE_STORYBOARD_VIDEO_REQUIRED, "请先生成或上传分镜视频。");
        }
        if (request.voiceResultId() != null) {
            requireVoiceResult(tenantId, projectId, request.voiceResultId());
        }
        if (request.subtitleId() != null) {
            requireSubtitle(tenantId, projectId, request.subtitleId());
        }
        LocalDateTime now = LocalDateTime.now();
        ShotComposeTaskEntity task = new ShotComposeTaskEntity();
        task.tenantId = tenantId;
        task.projectId = projectId;
        task.storyboardId = request.storyboardId();
        task.voiceResultId = request.voiceResultId();
        task.subtitleId = request.subtitleId();
        task.composeConfig = writeJson(Map.of(
            "includeSubtitle", Boolean.TRUE.equals(request.includeSubtitle()),
            "audioVolume", defaultNumber(request.audioVolume()),
            "outputFormat", blankToNull(request.outputFormat()) == null ? "mp4" : request.outputFormat().trim()
        ));
        task.status = ShotTaskStatus.PROCESSING.name();
        task.startedAt = now;
        task.createdBy = context.userId();
        task.createdAt = now;
        task.updatedAt = now;
        shotComposeTaskMapper.insert(task);

        ShotComposeResultEntity result = createComposeResult(task, storyboard, now);
        task.status = ShotTaskStatus.SUCCEEDED.name();
        task.completedAt = now;
        task.updatedAt = now;
        shotComposeTaskMapper.updateById(task);
        recordOperation(context, "CREATE_SHOT_COMPOSE_TASK", task.id, servletRequest);
        return ShotComposeTaskResponse.from(task, List.of(result));
    }

    @Transactional
    public ShotComposeResultResponse saveComposeMaterial(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeResultEntity result = requireComposeResult(tenantId, projectId, resultId);
        if (result.materialId != null) {
            return ShotComposeResultResponse.from(result);
        }
        result.materialId = createMaterial(context, projectId, "VIDEO", result.taskId, result.id, "单镜头视频-" + result.storyboardId, result.videoUrl, result.coverUrl, result.durationSeconds, result.width, result.height, result.format, result.fileSize);
        result.updatedAt = LocalDateTime.now();
        shotComposeResultMapper.updateById(result);
        recordOperation(context, "SAVE_SHOT_COMPOSE_RESULT_MATERIAL", result.id, request);
        return ShotComposeResultResponse.from(result);
    }

    @Transactional
    public ShotComposeResultResponse bindComposeResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeResultEntity result = requireComposeResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        LocalDateTime now = LocalDateTime.now();
        for (ShotComposeResultEntity item : shotComposeResultMapper.selectByTask(tenantId, projectId, result.taskId)) {
            item.isSelected = item.id.equals(result.id);
            item.updatedAt = now;
            shotComposeResultMapper.updateById(item);
        }
        result.isSelected = true;
        result.updatedAt = now;
        shotComposeResultMapper.updateById(result);
        storyboard.currentShotResultId = result.id;
        storyboard.currentShotVideoUrl = result.videoUrl;
        storyboard.updatedAt = now;
        storyboardMapper.updateById(storyboard);
        recordOperation(context, "BIND_SHOT_COMPOSE_RESULT_STORYBOARD", result.id, request);
        return ShotComposeResultResponse.from(result);
    }

    @Transactional
    public void deleteComposeResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest request) {
        TenantContext context = requireContext(tenantId, projectId);
        ShotComposeResultEntity result = requireComposeResult(tenantId, projectId, resultId);
        StoryboardEntity storyboard = requireStoryboard(tenantId, projectId, result.storyboardId);
        if (result.id.equals(storyboard.currentShotResultId)) {
            throw new BusinessException(ErrorCode.SHOT_COMPOSE_RESULT_IN_USE, "当前单镜头视频已被分镜引用，不能静默删除。");
        }
        result.status = ShotResultStatus.DELETED.name();
        result.updatedAt = LocalDateTime.now();
        shotComposeResultMapper.updateById(result);
        recordOperation(context, "DELETE_SHOT_COMPOSE_RESULT", result.id, request);
    }

    private AiVoiceResultEntity createVoiceResult(AiVoiceTaskEntity task, BigDecimal duration, LocalDateTime now) {
        AiVoiceResultEntity result = new AiVoiceResultEntity();
        result.tenantId = task.tenantId;
        result.projectId = task.projectId;
        result.taskId = task.id;
        result.storyboardId = task.storyboardId;
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(now);
        result.storagePath = "/materials/%d/%d/audios/%s/%d.mp3".formatted(task.tenantId, task.projectId, day, task.id);
        result.fileSize = writeFile(result.storagePath, "mock mp3 audio: " + task.textContent);
        result.audioUrl = result.storagePath;
        result.durationSeconds = duration;
        result.format = "mp3";
        result.isSelected = false;
        result.status = ShotResultStatus.ACTIVE.name();
        result.createdAt = now;
        result.updatedAt = now;
        aiVoiceResultMapper.insert(result);
        return result;
    }

    private ShotComposeResultEntity createComposeResult(ShotComposeTaskEntity task, StoryboardEntity storyboard, LocalDateTime now) {
        ShotComposeResultEntity result = new ShotComposeResultEntity();
        result.tenantId = task.tenantId;
        result.projectId = task.projectId;
        result.taskId = task.id;
        result.storyboardId = task.storyboardId;
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(now);
        result.storagePath = "/materials/%d/%d/shots/%s/%d.mp4".formatted(task.tenantId, task.projectId, day, task.id);
        result.fileSize = writeFile(result.storagePath, "mock composed mp4: " + storyboard.currentVideoUrl);
        result.videoUrl = result.storagePath;
        result.coverUrl = storyboard.firstFrameUrl;
        result.durationSeconds = BigDecimal.valueOf(storyboard.durationSeconds == null ? 5 : storyboard.durationSeconds);
        result.width = 720;
        result.height = 1280;
        result.format = "mp4";
        result.isSelected = false;
        result.status = ShotResultStatus.ACTIVE.name();
        result.createdAt = now;
        result.updatedAt = now;
        shotComposeResultMapper.insert(result);
        return result;
    }

    private String writeSubtitleFile(Long tenantId, Long projectId, Long subtitleId, List<SubtitleSegmentResponse> segments, String storagePath) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < segments.size(); index++) {
            SubtitleSegmentResponse segment = segments.get(index);
            body.append(index + 1)
                .append('\n')
                .append(formatSrtTime(segment.startTime()))
                .append(" --> ")
                .append(formatSrtTime(segment.endTime()))
                .append('\n')
                .append(segment.text())
                .append("\n\n");
        }
        writeFile(storagePath, body.toString());
        return storagePath;
    }

    private String subtitleStoragePath(Long tenantId, Long projectId, Long subtitleId) {
        String day = DateTimeFormatter.BASIC_ISO_DATE.format(LocalDateTime.now());
        return "/materials/%d/%d/subtitles/%s/%s.srt".formatted(tenantId, projectId, day, UUID.randomUUID());
    }

    private Long createMaterial(TenantContext context, Long projectId, String type, Long taskId, Long resultId, String name, String url, String coverUrl, BigDecimal duration, Integer width, Integer height, String format, Long fileSize) {
        VideoMaterialEntity material = new VideoMaterialEntity();
        material.setTenantId(context.tenantId());
        material.setProjectId(projectId);
        material.setMaterialType(type);
        material.setSourceType("AI_GENERATED");
        material.setSourceTaskId(taskId);
        material.setSourceResultId(resultId);
        material.setName(name);
        material.setUrl(url);
        material.setCoverUrl(coverUrl);
        material.setDurationSeconds(duration);
        material.setWidth(width);
        material.setHeight(height);
        material.setFormat(format);
        material.setFileSize(fileSize);
        material.setStatus("ACTIVE");
        material.setCreatedBy(context.userId());
        material.setCreatedAt(LocalDateTime.now());
        material.setUpdatedAt(LocalDateTime.now());
        materialMapper.insert(material);
        return material.getId();
    }

    private TenantContext requireContext(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        if (rbacPermissionService.hasPermission(context, "PROJECT:VIEW")) {
            return context;
        }
        ProjectMemberEntity member = projectMemberMapper.selectActiveByProjectIdAndUserId(context.tenantId(), projectId, context.userId());
        if (member == null) {
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "无权访问该项目。");
        }
        return context;
    }

    private AiServiceConfigEntity resolveVoiceService(Long tenantId, Long serviceConfigId) {
        QueryWrapper<AiServiceConfigEntity> base = new QueryWrapper<AiServiceConfigEntity>()
            .eq("tenant_id", tenantId)
            .eq("service_type", "VOICE")
            .eq("enabled", true)
            .isNull("deleted_at");
        if (serviceConfigId != null) {
            AiServiceConfigEntity selected = aiServiceConfigMapper.selectOne(base.clone().eq("id", serviceConfigId).last("limit 1"));
            if (selected == null) {
                throw new BusinessException(ErrorCode.AI_VOICE_SERVICE_UNAVAILABLE, "当前语音服务不可用。");
            }
            return selected;
        }
        AiServiceConfigEntity defaultConfig = aiServiceConfigMapper.selectOne(base.clone().eq("is_default", true).last("limit 1"));
        if (defaultConfig != null) {
            return defaultConfig;
        }
        AiServiceConfigEntity fallback = aiServiceConfigMapper.selectOne(base.orderByDesc("priority").orderByDesc("id").last("limit 1"));
        if (fallback == null) {
            throw new BusinessException(ErrorCode.AI_VOICE_SERVICE_UNAVAILABLE, "当前团队未配置可用语音服务。");
        }
        return fallback;
    }

    private StoryboardEntity requireStoryboard(Long tenantId, Long projectId, Long storyboardId) {
        StoryboardEntity storyboard = storyboardMapper.selectActive(tenantId, projectId, storyboardId);
        if (storyboard == null) {
            throw new BusinessException(ErrorCode.AI_VIDEO_STORYBOARD_NOT_FOUND, "分镜不存在。");
        }
        return storyboard;
    }

    private AiVoiceResultEntity requireVoiceResult(Long tenantId, Long projectId, Long resultId) {
        AiVoiceResultEntity result = aiVoiceResultMapper.selectActive(tenantId, projectId, resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.AI_VOICE_RESULT_NOT_FOUND, "语音结果不存在。");
        }
        return result;
    }

    private StoryboardSubtitleEntity requireSubtitle(Long tenantId, Long projectId, Long subtitleId) {
        StoryboardSubtitleEntity subtitle = subtitleMapper.selectActive(tenantId, projectId, subtitleId);
        if (subtitle == null) {
            throw new BusinessException(ErrorCode.STORYBOARD_SUBTITLE_NOT_FOUND, "字幕不存在。");
        }
        return subtitle;
    }

    private ShotComposeResultEntity requireComposeResult(Long tenantId, Long projectId, Long resultId) {
        ShotComposeResultEntity result = shotComposeResultMapper.selectActive(tenantId, projectId, resultId);
        if (result == null) {
            throw new BusinessException(ErrorCode.SHOT_COMPOSE_RESULT_NOT_FOUND, "单镜头合成结果不存在。");
        }
        return result;
    }

    private StoryboardSubtitleResponse subtitleResponse(StoryboardSubtitleEntity entity) {
        try {
            Map<String, Object> content = objectMapper.readValue(entity.content, new TypeReference<>() {});
            String text = String.valueOf(content.get("textContent"));
            List<SubtitleSegmentResponse> segments = readSubtitleSegments(content, text);
            return new StoryboardSubtitleResponse(entity.id, entity.storyboardId, entity.voiceResultId, entity.subtitleType, text, entity.srtUrl, entity.styleConfig, entity.isSelected, entity.status, entity.createdAt, segments);
        } catch (Exception exception) {
            return new StoryboardSubtitleResponse(entity.id, entity.storyboardId, entity.voiceResultId, entity.subtitleType, "", entity.srtUrl, entity.styleConfig, entity.isSelected, entity.status, entity.createdAt, List.of());
        }
    }

    private SubtitleSegmentResponse firstSubtitleSegment(StoryboardSubtitleEntity subtitle) {
        StoryboardSubtitleResponse response = subtitleResponse(subtitle);
        if (response.segments().isEmpty()) {
            return new SubtitleSegmentResponse(response.textContent(), BigDecimal.ZERO, BigDecimal.valueOf(5));
        }
        return response.segments().get(0);
    }

    private List<SubtitleSegmentResponse> readSubtitleSegments(Map<String, Object> content, String fallbackText) {
        Object rawSegments = content.get("segments");
        if (!(rawSegments instanceof List<?> segments) || segments.isEmpty()) {
            return List.of(new SubtitleSegmentResponse(fallbackText, BigDecimal.ZERO, BigDecimal.valueOf(5)));
        }
        return segments.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .map(segment -> new SubtitleSegmentResponse(
                String.valueOf(segment.getOrDefault("text", fallbackText)),
                toBigDecimal(segment.get("startTime"), BigDecimal.ZERO),
                toBigDecimal(segment.get("endTime"), BigDecimal.valueOf(5))
            ))
            .toList();
    }

    private void validateSubtitleTimeline(StoryboardEntity storyboard, BigDecimal startTime, BigDecimal endTime) {
        if (startTime.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入正确的开始时间。");
        }
        if (endTime.compareTo(startTime) <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请输入正确的结束时间。");
        }
        if (storyboard.durationSeconds != null && endTime.compareTo(BigDecimal.valueOf(storyboard.durationSeconds.longValue())) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "字幕时间不能超过视频时长。");
        }
    }

    private AiVoiceTaskEntity requireVoiceTask(Long tenantId, Long projectId, Long taskId) {
        AiVoiceTaskEntity task = aiVoiceTaskMapper.selectActive(tenantId, projectId, taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.AI_VOICE_TASK_NOT_FOUND, "语音任务不存在。");
        }
        return task;
    }

    private ShotComposeTaskEntity requireComposeTask(Long tenantId, Long projectId, Long taskId) {
        ShotComposeTaskEntity task = shotComposeTaskMapper.selectActive(tenantId, projectId, taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.SHOT_COMPOSE_TASK_NOT_FOUND, "单镜头合成任务不存在。");
        }
        return task;
    }

    private AiVoiceTaskResponse voiceTaskResponse(AiVoiceTaskEntity task) {
        return AiVoiceTaskResponse.from(task, aiVoiceResultMapper.selectByTask(task.tenantId, task.projectId, task.id));
    }

    private ShotComposeTaskResponse composeTaskResponse(ShotComposeTaskEntity task) {
        return ShotComposeTaskResponse.from(task, shotComposeResultMapper.selectByTask(task.tenantId, task.projectId, task.id));
    }

    private Map<String, Object> readJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("JSON解析失败", exception);
        }
    }

    private String writeJsonContent(String text, List<SubtitleSegmentResponse> segments) {
        return writeJson(Map.of("textContent", text, "segments", segments));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("JSON序列化失败", exception);
        }
    }

    private long writeFile(String storagePath, String content) {
        try {
            Path file = storageRoot.resolve(storagePath.substring(1));
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return Files.size(file);
        } catch (Exception exception) {
            throw new IllegalStateException("文件写入失败：" + exception.getMessage(), exception);
        }
    }

    private BigDecimal estimateDuration(String text) {
        int length = text == null ? 0 : text.strip().length();
        return BigDecimal.valueOf(Math.max(1.5, length * 0.25)).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal defaultNumber(BigDecimal value) {
        return value == null ? DEFAULT_NUMBER : value;
    }

    private BigDecimal toBigDecimal(Object value, BigDecimal fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String formatSrtTime(BigDecimal seconds) {
        int millis = seconds.multiply(BigDecimal.valueOf(1000)).setScale(0, RoundingMode.HALF_UP).intValue();
        int hours = millis / 3_600_000;
        int minutes = millis % 3_600_000 / 60_000;
        int secondPart = millis % 60_000 / 1000;
        return "%02d:%02d:%02d,%03d".formatted(hours, minutes, secondPart, millis % 1000);
    }

    private void recordOperation(TenantContext context, String operation, Long resourceId, HttpServletRequest request) {
        operationLogService.record(context.userId(), context.tenantId(), operation, resourceId, OperationResult.SUCCESS, request);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
