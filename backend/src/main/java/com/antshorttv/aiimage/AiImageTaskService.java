package com.antshorttv.aiimage;

import com.antshorttv.ai.AiServiceConfigEntity;
import com.antshorttv.ai.AiServiceConfigMapper;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.ai.AiProviderEntity;
import com.antshorttv.ai.AiProviderMapper;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AiImageTaskService {
    private static final List<String> TASK_TYPES = List.of("CHARACTER", "SCENE", "STORYBOARD_FIRST_FRAME");
    private static final List<String> TARGET_TYPES = List.of("CHARACTER", "SCENE", "STORYBOARD");
    private static final List<String> ASPECT_RATIOS = List.of("1:1", "3:4", "4:3", "9:16", "16:9");

    private final TenantContextResolver tenantContextResolver;
    private final ProjectMapper projectMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final ProjectAiConfigService projectAiConfigService;
    private final AiImageTaskMapper taskMapper;
    private final AiImageResultMapper resultMapper;
    private final MaterialMapper materialMapper;
    private final AiImageTaskExecutionService executionService;
    private final AiImageStorageService storageService;
    private final JdbcTemplate jdbcTemplate;
    private final OperationLogService operationLogService;
    private final TeamPointService teamPointService;

    public AiImageTaskService(
        TenantContextResolver tenantContextResolver,
        ProjectMapper projectMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        ProjectAiConfigService projectAiConfigService,
        AiImageTaskMapper taskMapper,
        AiImageResultMapper resultMapper,
        MaterialMapper materialMapper,
        AiImageTaskExecutionService executionService,
        AiImageStorageService storageService,
        JdbcTemplate jdbcTemplate,
        OperationLogService operationLogService,
        TeamPointService teamPointService
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.projectMapper = projectMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.projectAiConfigService = projectAiConfigService;
        this.taskMapper = taskMapper;
        this.resultMapper = resultMapper;
        this.materialMapper = materialMapper;
        this.executionService = executionService;
        this.storageService = storageService;
        this.jdbcTemplate = jdbcTemplate;
        this.operationLogService = operationLogService;
        this.teamPointService = teamPointService;
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
        ResolvedImageModel resolved = resolveImageModel(tenantId, projectId, request.serviceConfigId());
        teamPointService.consumeForAi(context, 1, request.taskType().trim(), null, "AI 图片生成消耗积分");
        String taskType = request.taskType().trim();
        LocalDateTime now = LocalDateTime.now();

        AiImageTaskEntity task = new AiImageTaskEntity();
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setTaskType(taskType);
        task.setTargetType(request.targetType().trim());
        task.setTargetId(request.targetId());
        task.setServiceConfigId(resolved.legacyServiceConfigId());
        task.setModelId(resolved.modelId());
        task.setProviderCode(resolved.providerCode());
        task.setModel(resolved.modelName());
        task.setPrompt(request.prompt().trim());
        task.setNegativePrompt(blankToNull(request.negativePrompt()));
        task.setReferenceImages(ReferenceImagesCodec.encode(request.referenceImages()));
        task.setAspectRatio(request.aspectRatio().trim());
        task.setImageCount(request.imageCount());
        task.setStyle(blankToNull(request.style()));
        task.setQuality(blankToNull(request.quality()) == null ? "STANDARD" : request.quality().trim());
        task.setSeed(blankToNull(request.seed()));
        task.setStatus(AiImageTaskStatus.PENDING.name());
        task.setCreatedBy(context.userId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);

        operationLogService.record(context.userId(), tenantId, "CREATE_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
        executeAfterCommit(task.getId());
        return toResponse(task);
    }

    @Transactional
    public AiImageTaskResponse regenerate(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        AiImageTaskEntity source = requireTask(tenantId, projectId, taskId);
        return create(
            tenantId,
            projectId,
            new CreateAiImageTaskRequest(
                source.getTaskType(),
                source.getTargetType(),
                source.getTargetId(),
                source.getServiceConfigId(),
                source.getPrompt(),
                source.getNegativePrompt(),
                ReferenceImagesCodec.decode(source.getReferenceImages()),
                source.getAspectRatio(),
                source.getImageCount(),
                source.getStyle(),
                source.getQuality(),
                source.getSeed()
            ),
            servletRequest
        );
    }

    @Transactional
    public AiImageTaskResponse cancel(Long tenantId, Long projectId, Long taskId, HttpServletRequest servletRequest) {
        TenantContext context = requireProject(tenantId, projectId);
        AiImageTaskEntity task = requireTask(tenantId, projectId, taskId);
        if (!List.of(AiImageTaskStatus.PENDING.name(), AiImageTaskStatus.RUNNING.name()).contains(task.getStatus())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前任务状态不可取消。");
        }
        task.setStatus(AiImageTaskStatus.CANCELED.name());
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(task.getCompletedAt());
        taskMapper.updateById(task);
        operationLogService.record(context.userId(), tenantId, "CANCEL_AI_IMAGE_TASK", task.getId(), OperationResult.SUCCESS, servletRequest);
        return toResponse(task);
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

    private void executeAfterCommit(Long taskId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executionService.execute(taskId);
                }
            });
            return;
        }
        executionService.execute(taskId);
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
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请选择关联对象。");
        }
    }

    private boolean isResultReferenced(AiImageResultEntity result) {
        return switch (result.getTargetType()) {
            case "CHARACTER" -> countReferences("character_asset", "main_image_result_id", result) > 0;
            case "SCENE" -> countReferences("scene_asset", "main_image_result_id", result) > 0;
            case "STORYBOARD" -> countReferences("storyboard", "first_frame_result_id", result) > 0;
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

    private AiServiceConfigEntity resolveImageService(Long tenantId, Long serviceConfigId) {
        LambdaQueryWrapper<AiServiceConfigEntity> wrapper = new LambdaQueryWrapper<AiServiceConfigEntity>()
            .eq(AiServiceConfigEntity::getServiceType, "IMAGE")
            .eq(AiServiceConfigEntity::getEnabled, true)
            .isNull(AiServiceConfigEntity::getDeletedAt);
        if (serviceConfigId != null) {
            wrapper.eq(AiServiceConfigEntity::getId, serviceConfigId);
        }
        AiServiceConfigEntity config = aiServiceConfigMapper.selectOne(wrapper.orderByDesc(AiServiceConfigEntity::getIsDefault).orderByDesc(AiServiceConfigEntity::getPriority).last("limit 1"));
        if (config == null) {
            throw new BusinessException(ErrorCode.AI_IMAGE_SERVICE_UNAVAILABLE, "未配置可用图片服务。");
        }
        return config;
    }

    private ResolvedImageModel resolveImageModel(Long tenantId, Long projectId, Long serviceConfigId) {
        if (serviceConfigId != null) {
            AiServiceConfigEntity config = resolveImageService(tenantId, serviceConfigId);
            AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getLegacyServiceConfigId, config.getId())
                .last("limit 1"));
            return new ResolvedImageModel(
                config.getId(),
                model == null ? null : model.getId(),
                config.getProvider(),
                model == null ? config.getModel() : model.getName()
            );
        }
        Long modelId = projectAiConfigService.resolveModelId(tenantId, projectId, "IMAGE");
        AiModelEntity model = modelId == null ? null : aiModelMapper.selectById(modelId);
        if (model == null || !"ENABLED".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.AI_IMAGE_SERVICE_UNAVAILABLE, "未配置可用图片模型。");
        }
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null || !"ENABLED".equals(provider.getStatus())) {
            throw new BusinessException(ErrorCode.AI_IMAGE_SERVICE_UNAVAILABLE, "图片模型服务商不可用。");
        }
        return new ResolvedImageModel(model.getLegacyServiceConfigId(), model.getId(), provider.getCode(), model.getName());
    }

    private record ResolvedImageModel(Long legacyServiceConfigId, Long modelId, String providerCode, String modelName) {
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

    private AiImageTaskResponse toResponse(AiImageTaskEntity task) {
        return AiImageTaskResponse.from(task, resultMapper.selectActiveByTask(task.getId()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
