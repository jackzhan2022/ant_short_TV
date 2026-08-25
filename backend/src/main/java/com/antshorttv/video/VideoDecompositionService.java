package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.execution.AiExecutionTaskMapper;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.ScriptEntity;
import com.antshorttv.script.ScriptMapper;
import com.antshorttv.script.ScriptVersionEntity;
import com.antshorttv.script.ScriptVersionMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.storage.ObjectStorageService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VideoDecompositionService {
    private static final long MAX_FILE_SIZE = 1024L * 1024L * 1024L;
    private static final BigDecimal MAX_DURATION_SECONDS = BigDecimal.valueOf(1800);
    private static final List<String> SUPPORTED_MIME_TYPES = List.of("video/mp4", "video/quicktime", "video/x-msvideo");
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".mp4", ".mov", ".avi");
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED");

    private final TenantContextResolver tenantContextResolver;
    private final ProjectPermissionGuard projectPermissionGuard;
    private final VideoDecompositionBatchMapper batchMapper;
    private final VideoDecompositionEpisodeMapper episodeMapper;
    private final VideoDecompositionAnalysisMapper analysisMapper;
    private final VideoDecompositionAttemptMapper attemptMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper scriptVersionMapper;
    private final ObjectStorageService objectStorageService;
    private final AiExecutionService executionService;
    private final AiExecutionTaskMapper executionTaskMapper;
    private final Path storageRoot;

    public VideoDecompositionService(
        TenantContextResolver tenantContextResolver,
        ProjectPermissionGuard projectPermissionGuard,
        VideoDecompositionBatchMapper batchMapper,
        VideoDecompositionEpisodeMapper episodeMapper,
        VideoDecompositionAnalysisMapper analysisMapper,
        VideoDecompositionAttemptMapper attemptMapper,
        ScriptMapper scriptMapper,
        ScriptVersionMapper scriptVersionMapper,
        ObjectStorageService objectStorageService,
        AiExecutionService executionService,
        AiExecutionTaskMapper executionTaskMapper,
        @Value("${ai.video.storage-root:storage}") String storageRoot
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.projectPermissionGuard = projectPermissionGuard;
        this.batchMapper = batchMapper;
        this.episodeMapper = episodeMapper;
        this.analysisMapper = analysisMapper;
        this.attemptMapper = attemptMapper;
        this.scriptMapper = scriptMapper;
        this.scriptVersionMapper = scriptVersionMapper;
        this.objectStorageService = objectStorageService;
        this.executionService = executionService;
        this.executionTaskMapper = executionTaskMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public VideoDecompositionUploadResponse upload(Long tenantId, MultipartFile file) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择视频文件。");
        }
        String fileName = file.getOriginalFilename() == null ? "episode.mp4" : file.getOriginalFilename().trim();
        String mimeType = file.getContentType();
        validateVideoFile(fileName, mimeType, file.getSize(), null);
        String extension = extension(fileName);
        String day = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String storagePath = "/materials/%d/video-decomposition/%s/%s%s".formatted(
            tenantId,
            day,
            UUID.randomUUID(),
            extension
        );
        try {
            if (objectStorageService.enabled()) {
                objectStorageService.upload(storagePath, file.getBytes(), mimeType);
            } else {
                Path target = storageRoot.resolve(storagePath.substring(1)).normalize();
                if (!target.startsWith(storageRoot)) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频存储路径不合法。");
                }
                Files.createDirectories(target.getParent());
                file.transferTo(target);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频上传失败：" + exception.getMessage());
        }
        return new VideoDecompositionUploadResponse(fileName, storagePath, mimeType, file.getSize(), null);
    }

    public List<VideoDecompositionBatchResponse> list(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        if (projectId != null) {
            requireProjectAccess(context, projectId, "PROJECT:VIEW");
        }
        LambdaQueryWrapper<VideoDecompositionBatchEntity> wrapper = new LambdaQueryWrapper<VideoDecompositionBatchEntity>()
            .eq(VideoDecompositionBatchEntity::getTenantId, tenantId)
            .isNull(VideoDecompositionBatchEntity::getDeletedAt)
            .orderByDesc(VideoDecompositionBatchEntity::getCreatedAt);
        if (projectId != null) {
            wrapper.eq(VideoDecompositionBatchEntity::getProjectId, projectId);
        }
        return batchMapper.selectList(wrapper).stream()
            .filter(batch -> projectId != null || batch.getProjectId() == null || canViewProject(context, batch.getProjectId()))
            .map(batch -> VideoDecompositionBatchResponse.from(batch, episodeMapper.selectByBatch(tenantId, batch.getId())))
            .toList();
    }

    public VideoDecompositionBatchResponse detail(Long tenantId, Long batchId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        VideoDecompositionBatchEntity batch = requireBatch(tenantId, batchId);
        requireProjectAccessIfBound(context, batch.getProjectId(), "PROJECT:VIEW");
        return VideoDecompositionBatchResponse.from(batch, episodeMapper.selectByBatch(tenantId, batchId));
    }

    @Transactional
    public VideoDecompositionBatchResponse create(Long tenantId, CreateVideoDecompositionBatchRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        validateVideos(tenantId, request.videos());

        LocalDateTime now = LocalDateTime.now();
        VideoDecompositionBatchEntity batch = new VideoDecompositionBatchEntity();
        batch.setTenantId(tenantId);
        batch.setName(request.name().trim());
        batch.setModelId(request.modelId());
        batch.setStatus("PENDING_ANALYSIS");
        batch.setTotalEpisodes(request.videos().size());
        batch.setCompletedEpisodes(0);
        batch.setFailedEpisodes(0);
        batch.setCreatedBy(context.userId());
        batch.setCreatedAt(now);
        batch.setUpdatedAt(now);
        batchMapper.insert(batch);

        for (int index = 0; index < request.videos().size(); index++) {
            VideoUploadMetadataRequest video = request.videos().get(index);
            VideoDecompositionEpisodeEntity episode = new VideoDecompositionEpisodeEntity();
            episode.setBatchId(batch.getId());
            episode.setTenantId(tenantId);
            episode.setEpisodeNo(index + 1);
            episode.setSourceFileName(video.fileName().trim());
            episode.setStoragePath(video.storagePath().trim());
            episode.setMimeType(blankToNull(video.mimeType()));
            episode.setFileSize(video.fileSize());
            episode.setDurationSeconds(video.durationSeconds());
            episode.setStatus("PENDING_ANALYSIS");
            episode.setAnalysisVersion(0);
            episode.setDraftStatus("NOT_STARTED");
            episode.setDraftVersion(0);
            episode.setCreatedBy(context.userId());
            episode.setCreatedAt(now);
            episode.setUpdatedAt(now);
            episodeMapper.insert(episode);
            createExecutionHeader(episode, batch.getModelId());
            createAttempt(episode.getId(), 1, "VIDEO_ANALYSIS", "PENDING", now);
        }

        return detail(tenantId, batch.getId());
    }

    @Transactional
    public VideoDecompositionEpisodeResponse retry(Long tenantId, Long episodeId, RetryVideoDecompositionEpisodeRequest request) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        VideoDecompositionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        requireProjectAccessIfBound(context, episode.getProjectId(), "AI_SERVICE:USE");
        String phase = normalizePhase(request == null ? null : request.phase());
        if (!RETRYABLE_STATUSES.contains(episode.getStatus()) || episode.getExecutionToken() != null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前拆剧任务状态不可重试。");
        }
        int attemptNo = nextAttemptNo(episodeId, phase);
        LocalDateTime now = LocalDateTime.now();
        episode.setStatus("VIDEO_ANALYSIS".equals(phase) ? "PENDING_ANALYSIS" : "PENDING_DRAFT");
        episode.setErrorCode(null);
        episode.setErrorMessage(null);
        episode.setExecutionToken(null);
        episode.setExecutionPhase(null);
        episode.setHeartbeatAt(null);
        episode.setExecutionTimeoutAt(null);
        episode.setRetryable(false);
        episode.setUpdatedAt(now);
        episodeMapper.updateById(episode);
        createAttempt(episodeId, attemptNo, phase, "PENDING", now);
        recalculateBatch(tenantId, episode.getBatchId());
        return VideoDecompositionEpisodeResponse.from(episode);
    }

    public VideoDecompositionEpisodeDetailResponse episodeDetail(Long tenantId, Long episodeId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        VideoDecompositionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        requireProjectAccessIfBound(context, episode.getProjectId(), "PROJECT:VIEW");
        VideoDecompositionAnalysisEntity analysis = analysisMapper.selectLatest(episodeId);
        List<VideoDecompositionAttemptResponse> attempts = attemptMapper.selectList(
                new LambdaQueryWrapper<VideoDecompositionAttemptEntity>()
                    .eq(VideoDecompositionAttemptEntity::getEpisodeId, episodeId)
                    .orderByDesc(VideoDecompositionAttemptEntity::getStartedAt))
            .stream()
            .map(VideoDecompositionAttemptResponse::from)
            .toList();
        return new VideoDecompositionEpisodeDetailResponse(
            VideoDecompositionEpisodeResponse.from(episode),
            episode.getDraftContent(),
            currentScriptVersionId(tenantId, episode.getProjectId()),
            analysis == null ? null : analysis.getRawResponse(),
            analysis == null ? null : analysis.getNormalizedJson(),
            attempts
        );
    }

    @Transactional
    public VideoDecompositionEpisodeResponse updateDraft(
        Long tenantId,
        Long episodeId,
        UpdateVideoDecompositionDraftRequest request
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        VideoDecompositionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        requireProjectAccessIfBound(context, episode.getProjectId(), "AI_SERVICE:USE");
        requireDraftVersion(episode, request.expectedDraftVersion());

        episode.setDraftContent(request.draftContent().trim());
        episode.setDraftStatus("PENDING_REVIEW");
        episode.setDraftVersion(safeVersion(episode.getDraftVersion()) + 1);
        episode.setStatus("PENDING_REVIEW");
        episode.setErrorCode(null);
        episode.setErrorMessage(null);
        episode.setRetryable(false);
        episode.setUpdatedAt(LocalDateTime.now());
        episodeMapper.updateById(episode);
        recalculateBatch(tenantId, episode.getBatchId());
        return VideoDecompositionEpisodeResponse.from(episode);
    }

    @Transactional
    public VideoDecompositionEpisodeResponse confirmDraft(
        Long tenantId,
        Long episodeId,
        ConfirmVideoDecompositionDraftRequest request
    ) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        VideoDecompositionEpisodeEntity episode = requireEpisode(tenantId, episodeId);
        requireProjectAccess(context, request.projectId(), "AI_SERVICE:USE");
        if (episode.getProjectId() != null && !episode.getProjectId().equals(request.projectId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "该拆剧单集已绑定其他项目。");
        }
        requireDraftVersion(episode, request.expectedDraftVersion());

        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, request.projectId());
        Long currentVersionId = script == null ? null : script.getCurrentVersionId();
        if (request.expectedCurrentScriptVersionId() != null
            && !request.expectedCurrentScriptVersionId().equals(currentVersionId)) {
            throw new BusinessException(ErrorCode.SCRIPT_VERSION_CONFLICT, "当前剧本版本已变化，请刷新后再确认导入。");
        }

        LocalDateTime now = LocalDateTime.now();
        if (episode.getProjectId() == null) {
            episode.setProjectId(request.projectId());
            VideoDecompositionBatchEntity batch = requireBatch(tenantId, episode.getBatchId());
            if (batch.getProjectId() == null) {
                batch.setProjectId(request.projectId());
                batch.setUpdatedAt(now);
                batchMapper.updateById(batch);
            }
        }
        if (script == null) {
            script = createVideoImportScript(context, episode, request.draftContent().trim(), now);
        }
        ScriptVersionEntity version = createVideoImportVersion(context, episode, script.getId(), request.draftContent().trim(), now);
        if (currentVersionId == null) {
            script.setContent(request.draftContent().trim());
            script.setSourceType("VIDEO_IMPORT");
            script.setCurrentVersionId(version.getId());
            script.setUpdatedAt(now);
            scriptMapper.updateById(script);
        }

        episode.setDraftContent(request.draftContent().trim());
        episode.setDraftStatus("CONFIRMED");
        episode.setStatus("CONFIRMED");
        episode.setConfirmedScriptVersionId(version.getId());
        episode.setRetryable(false);
        episode.setUpdatedAt(now);
        episodeMapper.updateById(episode);
        recalculateBatch(tenantId, episode.getBatchId());
        return VideoDecompositionEpisodeResponse.from(episode);
    }

    private void validateVideos(Long tenantId, List<VideoUploadMetadataRequest> videos) {
        for (VideoUploadMetadataRequest video : videos) {
            String storagePath = video.storagePath().trim();
            if (!storagePath.startsWith("/materials/%d/video-decomposition/".formatted(tenantId)) || storagePath.contains("..")) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频文件必须属于当前租户的拆剧素材。");
            }
            validateVideoFile(video.fileName(), video.mimeType(), video.fileSize(), video.durationSeconds());
        }
    }

    private void validateVideoFile(String fileName, String mimeType, Long fileSize, BigDecimal durationSeconds) {
        if (fileSize == null || fileSize < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频文件不能为空。");
        }
        if (fileSize > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频文件不能超过 1GB。");
        }
        if (durationSeconds != null && durationSeconds.compareTo(MAX_DURATION_SECONDS) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视频时长不能超过 30 分钟。");
        }
        String safeMimeType = mimeType == null ? "" : mimeType.trim().toLowerCase(Locale.ROOT);
        String safeFileName = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        boolean supportedMime = safeMimeType.isBlank() || SUPPORTED_MIME_TYPES.contains(safeMimeType);
        boolean supportedExtension = SUPPORTED_EXTENSIONS.stream().anyMatch(safeFileName::endsWith);
        if (!supportedMime || !supportedExtension) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "仅支持 mp4、mov、avi 视频。");
        }
    }

    private String extension(String fileName) {
        String safe = fileName == null ? "" : fileName.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream()
            .filter(safe::endsWith)
            .findFirst()
            .orElse(".mp4");
    }

    private void createAttempt(Long episodeId, int attemptNo, String phase, String status, LocalDateTime now) {
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectById(episodeId);
        VideoDecompositionAttemptEntity attempt = new VideoDecompositionAttemptEntity();
        attempt.setEpisodeId(episodeId);
        attempt.setExecutionId(episode == null ? null : episode.getExecutionId());
        attempt.setAttemptNo(attemptNo);
        attempt.setPhase(phase);
        attempt.setStatus(status);
        attempt.setStartedAt(now);
        attemptMapper.insert(attempt);
    }

    private void createExecutionHeader(VideoDecompositionEpisodeEntity episode, Long modelId) {
        AiExecutionTaskEntity execution = executionService.create(new AiExecutionCreateCommand(
            episode.getTenantId(),
            episode.getCreatedBy(),
            episode.getProjectId(),
            "video_decomposition",
            "VIDEO_UNDERSTANDING",
            "VIDEO_DECOMPOSITION_EPISODE",
            episode.getId(),
            modelId,
            "VIDEO_ANALYSIS",
            "video-decomposition:%d".formatted(episode.getId()),
            UUID.randomUUID().toString(),
            true,
            "{\"episodeId\":%d}".formatted(episode.getId())
        ));
        episode.setExecutionId(execution.id);
        episodeMapper.updateById(episode);
        executionTaskMapper.update(null, new UpdateWrapper<AiExecutionTaskEntity>()
            .set("status", "RUNNING")
            .set("started_at", LocalDateTime.now())
            .set("progress", 5)
            .set("next_run_at", null)
            .set("updated_at", LocalDateTime.now())
            .eq("id", execution.id));
    }

    private void recalculateBatch(Long tenantId, Long batchId) {
        List<VideoDecompositionEpisodeEntity> episodes = episodeMapper.selectByBatch(tenantId, batchId);
        int failed = (int) episodes.stream().filter(item -> "FAILED".equals(item.getStatus())).count();
        int completed = (int) episodes.stream().filter(item -> List.of("PENDING_REVIEW", "CONFIRMED").contains(item.getStatus())).count();
        VideoDecompositionBatchEntity batch = requireBatch(tenantId, batchId);
        batch.setFailedEpisodes(failed);
        batch.setCompletedEpisodes(completed);
        batch.setStatus(failed > 0 ? "PARTIAL_FAILED" : completed == batch.getTotalEpisodes() ? "PENDING_REVIEW" : "PENDING_ANALYSIS");
        batch.setUpdatedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
    }

    private int nextAttemptNo(Long episodeId, String phase) {
        Long count = attemptMapper.selectCount(new LambdaQueryWrapper<VideoDecompositionAttemptEntity>()
            .eq(VideoDecompositionAttemptEntity::getEpisodeId, episodeId)
            .eq(VideoDecompositionAttemptEntity::getPhase, phase));
        return count.intValue() + 1;
    }

    private ScriptEntity createVideoImportScript(
        TenantContext context,
        VideoDecompositionEpisodeEntity episode,
        String draftContent,
        LocalDateTime now
    ) {
        ScriptEntity script = new ScriptEntity();
        script.setTenantId(context.tenantId());
        script.setProjectId(episode.getProjectId());
        script.setTitle("视频拆剧剧本");
        script.setSourceType("VIDEO_IMPORT");
        script.setContent(draftContent);
        script.setStatus("DRAFT");
        script.setCreatedBy(context.userId());
        script.setCreatedAt(now);
        script.setUpdatedAt(now);
        scriptMapper.insert(script);
        return script;
    }

    private ScriptVersionEntity createVideoImportVersion(
        TenantContext context,
        VideoDecompositionEpisodeEntity episode,
        Long scriptId,
        String draftContent,
        LocalDateTime now
    ) {
        ScriptVersionEntity version = new ScriptVersionEntity();
        version.setTenantId(context.tenantId());
        version.setProjectId(episode.getProjectId());
        version.setScriptId(scriptId);
        version.setVersionNo(scriptVersionMapper.countByScript(context.tenantId(), scriptId).intValue() + 1);
        version.setSourceType("VIDEO_IMPORT");
        version.setInputSummary("视频拆剧批次%d 第%d集 单集%d".formatted(
            episode.getBatchId(),
            episode.getEpisodeNo(),
            episode.getId()
        ));
        version.setContent(draftContent);
        version.setStatus("DRAFT");
        version.setCreatedBy(context.userId());
        version.setCreatedAt(now);
        scriptVersionMapper.insert(version);
        return version;
    }

    private Long currentScriptVersionId(Long tenantId, Long projectId) {
        ScriptEntity script = scriptMapper.selectCurrentByProject(tenantId, projectId);
        return script == null ? null : script.getCurrentVersionId();
    }

    private void requireDraftVersion(VideoDecompositionEpisodeEntity episode, Integer expectedDraftVersion) {
        if (expectedDraftVersion == null) {
            return;
        }
        if (!expectedDraftVersion.equals(safeVersion(episode.getDraftVersion()))) {
            throw new BusinessException(ErrorCode.SCRIPT_VERSION_CONFLICT, "拆剧草稿已变化，请刷新后再操作。");
        }
    }

    private int safeVersion(Integer version) {
        return version == null ? 0 : version;
    }

    private VideoDecompositionBatchEntity requireBatch(Long tenantId, Long batchId) {
        VideoDecompositionBatchEntity batch = batchMapper.selectOne(new LambdaQueryWrapper<VideoDecompositionBatchEntity>()
            .eq(VideoDecompositionBatchEntity::getTenantId, tenantId)
            .eq(VideoDecompositionBatchEntity::getId, batchId)
            .isNull(VideoDecompositionBatchEntity::getDeletedAt));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拆剧批次不存在。");
        }
        return batch;
    }

    private VideoDecompositionEpisodeEntity requireEpisode(Long tenantId, Long episodeId) {
        VideoDecompositionEpisodeEntity episode = episodeMapper.selectOne(new LambdaQueryWrapper<VideoDecompositionEpisodeEntity>()
            .eq(VideoDecompositionEpisodeEntity::getTenantId, tenantId)
            .eq(VideoDecompositionEpisodeEntity::getId, episodeId));
        if (episode == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "拆剧单集不存在。");
        }
        return episode;
    }

    private void requireProjectAccess(TenantContext context, Long projectId, String permissionCode) {
        projectPermissionGuard.require(context.tenantId(), projectId, permissionCode);
    }

    private void requireProjectAccessIfBound(TenantContext context, Long projectId, String permissionCode) {
        if (projectId != null) {
            requireProjectAccess(context, projectId, permissionCode);
        }
    }

    private boolean canViewProject(TenantContext context, Long projectId) {
        if (projectId == null) {
            return true;
        }
        try {
            requireProjectAccess(context, projectId, "PROJECT:VIEW");
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    private String normalizePhase(String phase) {
        String value = phase == null || phase.isBlank() ? "VIDEO_ANALYSIS" : phase.trim().toUpperCase(Locale.ROOT);
        if (!List.of("VIDEO_ANALYSIS", "DRAFT_GENERATION").contains(value)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "重试阶段不正确。");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
