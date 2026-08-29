package com.antshorttv.aiimage;

import com.antshorttv.ai.AiModelRoute;
import com.antshorttv.ai.AiModelRouter;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.accounting.AiUsageMetric;
import com.antshorttv.execution.AiExecutionCreateCommand;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import com.antshorttv.execution.AiExecutionStatus;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.points.AiPointReservationEntity;
import com.antshorttv.points.AiPointReservationMapper;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.AiSettlementOutcome;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.script.AssetVisualVariantService;
import com.antshorttv.script.EpisodeAwareVisualResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiImageTaskService {
    private static final List<String> TASK_TYPES = List.of("CHARACTER", "SCENE", "STORYBOARD_FIRST_FRAME");
    private static final List<String> TARGET_TYPES = List.of("CHARACTER", "SCENE", "STORYBOARD", "VISUAL_VARIANT");
    private static final List<String> ASPECT_RATIOS = List.of("1:1", "3:4", "4:3", "9:16", "16:9");

    private final TenantContextResolver tenantContextResolver;
    private final ProjectMapper projectMapper;
    private final AiModelRouter aiModelRouter;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final MaterialMapper materialMapper;
    private final AiExecutionService executionService;
    private final AiExecutionResponseMapper executionResponseMapper;
    private final AiPointSettlementService pointSettlementService;
    private final AiPointReservationMapper pointReservationMapper;
    private final AiImageStorageService storageService;
    private final JdbcTemplate jdbcTemplate;
    private final OperationLogService operationLogService;
    private final AssetVisualVariantService assetVisualVariantService;
    private final EpisodeAwareVisualResolver episodeAwareVisualResolver;

    public AiImageTaskService(
        TenantContextResolver tenantContextResolver,
        ProjectMapper projectMapper,
        AiModelRouter aiModelRouter,
        ProjectAiConfigService projectAiConfigService,
        AiImageTaskMapper taskMapper,
        AiImageResultMapper resultMapper,
        MaterialMapper materialMapper,
        AiExecutionService executionService,
        AiExecutionResponseMapper executionResponseMapper,
        AiPointSettlementService pointSettlementService,
        AiPointReservationMapper pointReservationMapper,
        AiImageStorageService storageService,
        JdbcTemplate jdbcTemplate,
        OperationLogService operationLogService,
        AssetVisualVariantService assetVisualVariantService,
        EpisodeAwareVisualResolver episodeAwareVisualResolver
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.projectMapper = projectMapper;
        this.aiModelRouter = aiModelRouter;
        this.projectAiConfigService = projectAiConfigService;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.materialMapper = materialMapper;
        this.executionService = executionService;
        this.executionResponseMapper = executionResponseMapper;
        this.pointSettlementService = pointSettlementService;
        this.pointReservationMapper = pointReservationMapper;
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogService = operationLogService;
        this.assetVisualVariantService = assetVisualVariantService;
        this.episodeAwareVisualResolver = episodeAwareVisualResolver;
    }

    public List<AiImageTaskResponse> list(Long tenantId, Long projectId, String taskType, String status) {
        TenantContext context = requireProject(tenantId, projectId);
        return taskMapper.selectByProject(context.tenantId(), projectId, taskType, status)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public AiImageTaskResponse detail(Long tenantId, Long projectId, Long taskId) {
        requireProject(tenantId, projectId);
        return toResponse(requireTask(tenantId, projectId, taskId));
    }

    @Transactional
    public AiImageTaskResponse create(Long tenantId, Long projectId, CreateAiImageTaskRequest request, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        validateRequest(request);
        String idempotencyKey = requestKey(servletRequest);
        AiImageTaskEntity existing = taskMapper.selectByIdempotency(tenantId, idempotencyKey);
        if (existing != null) {
            return toResponse(existing);
        }
        ResolvedImageModel resolved = resolveImageModel(tenantId, projectId, request.modelId());
        String taskType = request.taskType().trim();
        LocalDateTime now = LocalDateTime.now();

        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setTaskType(taskType);
        task.setTargetType(request.targetType().trim());
        task.setTargetId(request.targetId());
        task.setModelId(resolved.modelId());
        task.setProviderCode(resolved.providerCode());
        task.setModel(resolved.modelName());
        task.setPrompt(request.prompt().trim());
        task.setNegativePrompt(blankToNull(request.negativePrompt()));
        task.setReferenceImages(ReferenceImagesCodec.encode(resolveReferenceImages(
            tenantId, projectId, request)));
        task.setAspectRatio(request.aspectRatio().trim());
        task.setImageCount(request.imageCount());
        task.setStyle(blankToNull(request.style()));
        task.setQuality(blankToNull(request.quality()) == null ? "STANDARD" : request.quality().trim());
        task.setSeed(blankToNull(request.seed()));
        task.setStatus(AiImageTaskStatus.PENDING.name());
        task.setClientIdempotencyKey(idempotencyKey);
        task.setCreatedBy(context.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        if ("VISUAL_VARIANT".equals(task.getTargetType())) {
            Long previousTaskId = assetVisualVariantService.replaceGenerationOwner(
                tenantId, projectId, task.getTargetId(), task.getId());
            supersedeVisualVariantTask(previousTaskId);
        }

        AiExecutionTaskEntity execution = executionService.createWithReservation(
            new AiExecutionCreateCommand(
                tenantId,
                context.userId(),
                projectId,
                "ai_image_generate",
                "IMAGE",
                "AI_IMAGE_TASK",
                task.getId(),
                resolved.modelId(),
                "SUBMIT",
                idempotencyKey,
                traceId(servletRequest),
                true,
                null
            ),
            Map.of(
                AiUsageMetric.CALL, BigDecimal.ONE,
                AiUsageMetric.IMAGE, BigDecimal.valueOf(request.imageCount())
            ),
            imageDimensions(task)
        );
        task.setExecutionId(execution.id);
        taskMapper.updateById(task);

        operationLogService.record(context.userId(), tenantId, "CREATE_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(task);
    }

    @Transactional
    public AiImageTaskResponse regenerate(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageTaskEntity source = requireTask(tenantId, projectId, taskId);
        String idempotencyKey = requestKey(servletRequest);
        AiImageTaskEntity existing = taskMapper.selectByIdempotency(tenantId, idempotencyKey);
        if (existing != null) {
            return toResponse(existing);
        }
        ResolvedImageModel resolved = resolveImageModel(tenantId, projectId, source.getModelId());
        LocalDateTime now = LocalDateTime.now();
        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(source.getTenantId());
        task.setProjectId(source.getProjectId());
        task.setTaskType(source.getTaskType());
        task.setTargetType(source.getTargetType());
        task.setTargetId(source.getTargetId());
        task.setModelId(resolved.modelId());
        task.setProviderCode(resolved.providerCode());
        task.setModel(resolved.modelName());
        task.setPrompt(source.getPrompt());
        task.setNegativePrompt(source.getNegativePrompt());
        task.setReferenceImages(source.getReferenceImages());
        task.setAspectRatio(source.getAspectRatio());
        task.setImageCount(source.getImageCount());
        task.setStyle(source.getStyle());
        task.setQuality(source.getQuality());
        task.setSeed(source.getSeed());
        task.setStatus(AiImageTaskStatus.PENDING.name());
        task.setClientIdempotencyKey(idempotencyKey);
        task.setCreatedBy(context.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        AiExecutionTaskEntity execution = executionService.regenerateWithReservation(
            source.getExecutionId(),
            task.getId(),
            resolved.modelId(),
            idempotencyKey,
            traceId(servletRequest),
            Map.of(
                AiUsageMetric.CALL, BigDecimal.ONE,
                AiUsageMetric.IMAGE, BigDecimal.valueOf(task.getImageCount())
            ),
            imageDimensions(task)
        );
        task.setExecutionId(execution.id);
        taskMapper.updateById(task);

        if ("VISUAL_VARIANT".equals(task.getTargetType())) {
            Long previousTaskId = assetVisualVariantService.replaceGenerationOwner(
                tenantId, projectId, task.getTargetId(), task.getId());
            supersedeVisualVariantTask(previousTaskId);
        }

        operationLogService.record(context.userId(), tenantId, "REGENERATE_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(task);
    }

    private void supersedeVisualVariantTask(Long taskId) {
        if (taskId == null) return;
        AiImageTaskEntity previous = taskMapper.selectById(taskId);
        if (previous == null || previous.getExecutionId() == null
            || !List.of(AiImageTaskStatus.PENDING.name(), AiImageTaskStatus.RUNNING.name()).contains(previous.getStatus())) {
            return;
        }
        AiExecutionTaskEntity execution = executionService.requireTask(previous.getExecutionId());
        if (!List.of(AiExecutionStatus.PENDING.name(), AiExecutionStatus.RUNNING.name()).contains(execution.status)) {
            return;
        }
        var cancellation = executionService.cancelWithDisposition(execution.id);
        execution = cancellation.task();
        settleCancellation(execution, cancellation.beforeProviderCall(), "superseded");
        previous.setStatus(AiImageTaskStatus.CANCELED.name());
        previous.setErrorMessage("已被同一视觉形象的新生成任务替代。");
        previous.setCompletedAt(LocalDateTime.now());
        previous.setUpdatedAt(previous.getCompletedAt());
        taskMapper.updateById(previous);
    }

    @Transactional
    public AiImageTaskResponse cancel(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageTaskEntity task = requireTask(tenantId, projectId, taskId);
        if (!List.of(AiImageTaskStatus.PENDING.name(), AiImageTaskStatus.RUNNING.name()).contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前任务状态不可取消。");
        }
        AiExecutionTaskEntity execution = executionService.requireTask(task.getExecutionId());
        var cancellation = executionService.cancelWithDisposition(execution.id);
        execution = cancellation.task();
        settleCancellation(execution, cancellation.beforeProviderCall(), "cancel");
        task.setStatus(AiImageTaskStatus.CANCELED.name());
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCompletedAt());
        taskMapper.updateById(task);
        operationLogService.record(context.userId(), tenantId, "CANCEL_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(task);
    }

    private void settleCancellation(
        AiExecutionTaskEntity execution, boolean beforeProviderCall, String reason
    ) {
        AiPointReservationEntity reservation = pointReservationMapper.selectByExecutionId(execution.id);
        if (reservation == null) return;
        AiPointReservationEntity settled = pointSettlementService.finalizeOutcome(
            reservation.id,
            beforeProviderCall ? AiSettlementOutcome.PRE_CALL_CANCELED : AiSettlementOutcome.TRANSPORT_UNKNOWN,
            Map.of(), null, null,
            "execution:%d:v%d:%s".formatted(execution.id, execution.executionVersion, reason));
        executionService.updateSettlementSummary(settled);
    }

    @Transactional
    public void delete(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageTaskEntity task = requireTask(tenantId, projectId, taskId);
        task.setDeletedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getDeletedAt());
        taskMapper.updateById(task);
        operationLogService.record(context.userId(), tenantId, "DELETE_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
    }

    @Transactional
    public AiImageResultResponse saveMaterial(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageResultEntity result = requireResult(tenantId, projectId, resultId);
        if (result.getMaterialId() == null) {
            MaterialEntity material = new MaterialEntity();
            material.setTenantId(tenantId);
            material.setProjectId(projectId);
            material.setMaterialType("IMAGE");
            material.setSourceType("AI_GENERATED");
            material.setSourceTaskId(result.getTaskId());
            material.setName("AI图片结果-" + result.getId());
            material.setUrl(result.getImageUrl());
            material.setStoragePath(result.getStoragePath());
            material.setMimeType("image/png");
            material.setFileSize(result.getFileSize());
            material.setWidth(result.getWidth());
            material.setHeight(result.getHeight());
            material.setCreatedBy(context.userId());
            material.setCreatedAt(LocalDateTime.now());
            materialMapper.insert(material);
            result.setMaterialId(material.getId());
            result.setUpdatedAt(LocalDateTime.now());
            resultMapper.updateById(result);
            operationLogService.record(context.userId(), tenantId, "SAVE_AI_IMAGE_MATERIAL", result.getId(), OperationResult.SUCCESS, servletRequest);
        }
        return AiImageResultResponse.from(result);
    }

    public Resource download(Long tenantId, Long projectId, Long resultId) {
        requireProject(tenantId, projectId);
        return storageService.resource(requireResult(tenantId, projectId, resultId));
    }

    @Transactional
    public AiImageResultResponse selectResult(Long tenantId, Long projectId, Long resultId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageResultEntity selected = requireResult(tenantId, projectId, resultId);
        for (AiImageResultEntity item : resultMapper.selectActiveByTarget(tenantId, projectId, selected.getTargetType(), selected.getTargetId())) {
            item.setIsSelected(item.getId().equals(selected.getId()));
            item.setUpdatedAt(LocalDateTime.now());
            resultMapper.updateById(item);
        }
        selected.setIsSelected(true);
        bindSelectedResult(selected);
        operationLogService.record(context.userId(), tenantId, "SELECT_AI_IMAGE_RESULT", selected.getId(), OperationResult.SUCCESS, servletRequest);
        return AiImageResultResponse.from(selected);
    }

    @Transactional
    public void deleteResult(Long tenantId, Long projectId, Long resultId, boolean force, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageResultEntity result = requireResult(tenantId, projectId, resultId);
        if (isResultReferenced(result) && !force) {
            throw new BusinessException(ErrorCode.AI_IMAGE_RESULT_IN_USE, "当前图片已被引用。");
        }
        if (force) {
            clearBusinessBinding(result);
        }
        result.setIsSelected(false);
        result.setStatus(AiImageResultStatus.DELETED.name());
        result.setUpdatedAt(LocalDateTime.now());
        resultMapper.updateById(result);
        operationLogService.record(context.userId(), tenantId, "DELETE_AI_IMAGE_RESULT", result.getId(), OperationResult.SUCCESS, servletRequest);
    }

    private void bindSelectedResult(AiImageResultEntity result) {
        switch (result.getTargetType()) {
            case "CHARACTER" -> jdbcTemplate.update("""
                update character_asset
                   set main_image_url = ?, main_image_result_id = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, result.getImageUrl(), result.getId(), result.getTenantId(), result.getProjectId(), result.getTargetId());
            case "SCENE" -> jdbcTemplate.update("""
                update scene_asset
                   set main_image_url = ?, main_image_result_id = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, result.getImageUrl(), result.getId(), result.getTenantId(), result.getProjectId(), result.getTargetId());
            case "STORYBOARD" -> jdbcTemplate.update("""
                update storyboard
                   set first_frame_image_url = ?, first_frame_result_id = ?, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
                """, result.getImageUrl(), result.getId(), result.getTenantId(), result.getProjectId(), result.getTargetId());
            case "VISUAL_VARIANT" -> {
                AssetVisualVariantService.VariantResponse variant = assetVisualVariantService.generationSucceeded(
                    result.getTenantId(), result.getProjectId(), result.getTargetId(), result.getId(), result.getImageUrl());
                if (variant.primary()) {
                    bindLegacyPrimary(variant, result);
                }
            }
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择关联对象。");
        }
    }

    private void bindLegacyPrimary(
        AssetVisualVariantService.VariantResponse variant, AiImageResultEntity result
    ) {
        String table = switch (variant.assetType()) {
            case "CHARACTER" -> "character_asset";
            case "SCENE" -> "scene_asset";
            case "PROP" -> "prop_asset";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "视觉形象关联的资产类型无效。");
        };
        jdbcTemplate.update("update " + table
                + " set main_image_url = ?, main_image_result_id = ?, updated_at = now()"
                + " where tenant_id = ? and project_id = ? and id = ? and deleted_at is null",
            result.getImageUrl(), result.getId(), result.getTenantId(), result.getProjectId(), variant.assetId());
    }

    private boolean isResultReferenced(AiImageResultEntity result) {
        return switch (result.getTargetType()) {
            case "CHARACTER" -> countReferences("character_asset", "main_image_result_id", result) > 0;
            case "SCENE" -> countReferences("scene_asset", "main_image_result_id", result) > 0;
            case "STORYBOARD" -> countReferences("storyboard", "first_frame_result_id", result) > 0;
            case "VISUAL_VARIANT" -> countReferences("asset_visual_variant", "current_image_result_id", result) > 0;
            default -> false;
        };
    }

    private int countReferences(String table, String column, AiImageResultEntity result) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from %s where tenant_id = ? and project_id = ? and id = ? and %s = ? and deleted_at is null".formatted(table, column),
            Integer.class,
            result.getTenantId(),
            result.getProjectId(),
            result.getTargetId(),
            result.getId()
        );
        return count == null ? 0 : count;
    }

    private void clearBusinessBinding(AiImageResultEntity result) {
        switch (result.getTargetType()) {
            case "CHARACTER" -> jdbcTemplate.update("""
                update character_asset
                   set main_image_url = null, main_image_result_id = null, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and main_image_result_id = ?
                """, result.getTenantId(), result.getProjectId(), result.getTargetId(), result.getId());
            case "SCENE" -> jdbcTemplate.update("""
                update scene_asset
                   set main_image_url = null, main_image_result_id = null, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and main_image_result_id = ?
                """, result.getTenantId(), result.getProjectId(), result.getTargetId(), result.getId());
            case "STORYBOARD" -> jdbcTemplate.update("""
                update storyboard
                   set first_frame_image_url = null, first_frame_result_id = null, updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and first_frame_result_id = ?
                """, result.getTenantId(), result.getProjectId(), result.getTargetId(), result.getId());
            case "VISUAL_VARIANT" -> jdbcTemplate.update("""
                update asset_visual_variant
                   set current_image_url = null, current_image_result_id = null,
                       generation_status = 'NOT_STARTED', updated_at = now()
                 where tenant_id = ? and project_id = ? and id = ? and current_image_result_id = ?
                """, result.getTenantId(), result.getProjectId(), result.getTargetId(), result.getId());
            default -> {
            }
        }
    }

    private TenantContext requireProject(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        ProjectEntity project = projectMapper.selectByTenantIdAndId(tenantId, projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        return context;
    }

    private ResolvedImageModel resolveImageModel(Long tenantId, Long projectId, Long requestedModelId) {
        Long modelId = requestedModelId == null
            ? projectAiConfigService.resolveModelId(tenantId, projectId, "IMAGE")
            : requestedModelId;
        AiModelRoute route = aiModelRouter.route(modelId, "IMAGE");
        return new ResolvedImageModel(route.model().getId(), route.provider().getCode(), route.model().getName());
    }

    private record ResolvedImageModel(Long modelId, String providerCode, String modelName) {
    }

    private AiImageTaskEntity requireTask(Long tenantId, Long projectId, Long taskId) {
        AiImageTaskEntity task = taskMapper.selectOne(new LambdaQueryWrapper<AiImageTaskEntity>()
            .eq(AiImageTaskEntity::getTenantId, tenantId)
            .eq(AiImageTaskEntity::getProjectId, projectId)
            .eq(AiImageTaskEntity::getId, taskId)
            .isNull(AiImageTaskEntity::getDeletedAt));
        if (task == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片任务不存在。");
        }
        return task;
    }

    private AiImageResultEntity requireResult(Long tenantId, Long projectId, Long resultId) {
        AiImageResultEntity result = resultMapper.selectById(resultId);
        if (result == null || !tenantId.equals(result.getTenantId()) || !projectId.equals(result.getProjectId())
            || !AiImageResultStatus.ACTIVE.name().equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片结果不存在。");
        }
        return result;
    }

    private void validateRequest(CreateAiImageTaskRequest request) {
        if (!TASK_TYPES.contains(request.taskType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择任务类型。");
        }
        if (!TARGET_TYPES.contains(request.targetType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择关联对象。");
        }
        if (!ASPECT_RATIOS.contains(request.aspectRatio())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择图片比例。");
        }
        if (request.referenceImages() != null && request.referenceImages().size() > 4) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "参考图最多上传 4 张。");
        }
    }

    private List<String> resolveReferenceImages(
        Long tenantId, Long projectId, CreateAiImageTaskRequest request
    ) {
        if (request.referenceImages() != null && !request.referenceImages().isEmpty()) {
            return request.referenceImages();
        }
        if (!"STORYBOARD".equals(request.targetType())) {
            return List.of();
        }
        List<Map<String, Object>> storyboards = jdbcTemplate.queryForList("""
            select script_id, episode_no, characters, scene, visual_description, image_prompt
              from storyboard
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
             limit 1
            """, tenantId, projectId, request.targetId());
        if (storyboards.isEmpty()) return List.of();
        Map<String, Object> storyboard = storyboards.get(0);
        Long scriptId = storyboard.get("script_id") instanceof Number value ? value.longValue() : null;
        Integer episodeNo = storyboard.get("episode_no") instanceof Number value ? value.intValue() : null;
        Long episodeId = null;
        if (scriptId != null && episodeNo != null) {
            List<Long> episodeIds = jdbcTemplate.query("""
                select id from script_episode
                 where tenant_id = ? and project_id = ? and script_id = ? and episode_no = ?
                   and status = 'ACTIVE' and retired_at is null
                 limit 1
                """, (rs, rowNum) -> rs.getLong(1), tenantId, projectId, scriptId, episodeNo);
            episodeId = episodeIds.isEmpty() ? null : episodeIds.get(0);
        }
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();
        String characters = storyboard.get("characters") == null ? "" : storyboard.get("characters").toString();
        for (String character : characters.split("[,，、;；]")) {
            addResolvedAssetUrl(urls, tenantId, projectId, "CHARACTER", character, episodeId);
        }
        String scene = storyboard.get("scene") == null ? "" : storyboard.get("scene").toString();
        addResolvedAssetUrl(urls, tenantId, projectId, "SCENE", scene, episodeId);
        String propSource = (storyboard.get("visual_description") == null ? "" : storyboard.get("visual_description").toString())
            + " " + (storyboard.get("image_prompt") == null ? "" : storyboard.get("image_prompt").toString());
        for (Map<String, Object> prop : jdbcTemplate.queryForList("""
            select id, name from prop_asset
             where tenant_id = ? and project_id = ? and deleted_at is null order by id
            """, tenantId, projectId)) {
            String propName = String.valueOf(prop.get("name"));
            if (!propName.isBlank() && propSource.contains(propName)) {
                addResolvedAssetUrl(urls, tenantId, projectId, "PROP", propName, episodeId);
            }
        }
        return urls.stream().limit(4).toList();
    }

    private void addResolvedAssetUrl(
        java.util.Set<String> urls,
        Long tenantId,
        Long projectId,
        String assetType,
        String name,
        Long episodeId
    ) {
        if (name == null || name.isBlank()) return;
        String table = switch (assetType) {
            case "CHARACTER" -> "character_asset";
            case "SCENE" -> "scene_asset";
            case "PROP" -> "prop_asset";
            default -> throw new IllegalArgumentException("Unsupported asset type: " + assetType);
        };
        List<Long> ids = jdbcTemplate.query("select id from " + table
                + " where tenant_id = ? and project_id = ? and lower(name) = lower(?) and deleted_at is null limit 1",
            (rs, rowNum) -> rs.getLong(1), tenantId, projectId, name.trim());
        if (ids.isEmpty()) return;
        EpisodeAwareVisualResolver.ResolvedVisual resolved = episodeAwareVisualResolver.resolve(
            tenantId, projectId, assetType, ids.get(0), episodeId);
        if (resolved.imageUrl() != null && !resolved.imageUrl().isBlank()) {
            urls.add(resolved.imageUrl());
        }
    }

    private AiImageTaskResponse toResponse(AiImageTaskEntity task) {
        AiImageTaskResponse response = AiImageTaskResponse.from(task, resultMapper.selectActiveByTask(task.getId()));
        return task.getExecutionId() == null
            ? response
            : response.withExecution(executionResponseMapper.toResponse(executionService.requireTask(task.getExecutionId())));
    }

    private String requestKey(HttpServletRequest request) {
        String value = request.getHeader("Idempotency-Key");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private String traceId(HttpServletRequest request) {
        String value = request.getHeader("X-Trace-Id");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }

    private Map<String, String> imageDimensions(AiImageTaskEntity task) {
        return Map.of(
            "aspectRatio", task.getAspectRatio(),
            "quality", task.getQuality()
        );
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
