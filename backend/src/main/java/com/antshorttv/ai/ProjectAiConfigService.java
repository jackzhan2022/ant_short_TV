package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.project.ProjectMapper;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectAiConfigService {
    private final TenantContextResolver tenantContextResolver;
    private final ProjectMapper projectMapper;
    private final ProjectAiConfigMapper projectAiConfigMapper;
    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final OperationLogService operationLogService;

    public ProjectAiConfigService(
        TenantContextResolver tenantContextResolver,
        ProjectMapper projectMapper,
        ProjectAiConfigMapper projectAiConfigMapper,
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        OperationLogService operationLogService
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.projectMapper = projectMapper;
        this.projectAiConfigMapper = projectAiConfigMapper;
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.operationLogService = operationLogService;
    }

    public ProjectAiModelsResponse availableModels(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProject(context, projectId);
        return new ProjectAiModelsResponse(
            options("TEXT"),
            options("IMAGE"),
            options("VIDEO"),
            options("AUDIO")
        );
    }

    public ProjectAiConfigResponse config(Long tenantId, Long projectId) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProject(context, projectId);
        ProjectAiConfigEntity config = findConfig(tenantId, projectId);
        if (config == null) {
            return new ProjectAiConfigResponse(projectId, defaultModelId("TEXT"), defaultModelId("IMAGE"), defaultModelId("VIDEO"), defaultModelId("AUDIO"));
        }
        return response(config);
    }

    @Transactional
    public ProjectAiConfigResponse save(Long tenantId, Long projectId, ProjectAiConfigRequest request, HttpServletRequest servletRequest) {
        TenantContext context = tenantContextResolver.requireActiveMember(tenantId);
        requireProject(context, projectId);
        validateModel(request.textModelId(), "TEXT");
        validateModel(request.imageModelId(), "IMAGE");
        validateModel(request.videoModelId(), "VIDEO");
        validateModel(request.audioModelId(), "AUDIO");
        LocalDateTime now = LocalDateTime.now();
        ProjectAiConfigEntity config = findConfig(tenantId, projectId);
        if (config == null) {
            config = new ProjectAiConfigEntity();
            config.setTenantId(tenantId);
            config.setProjectId(projectId);
            config.setCreatedAt(now);
        }
        config.setTextModelId(request.textModelId());
        config.setImageModelId(request.imageModelId());
        config.setVideoModelId(request.videoModelId());
        config.setAudioModelId(request.audioModelId());
        config.setUpdatedAt(now);
        if (config.getId() == null) {
            projectAiConfigMapper.insert(config);
        } else {
            projectAiConfigMapper.updateById(config);
        }
        operationLogService.record(context.userId(), tenantId, "SAVE_PROJECT_AI_CONFIG", projectId, OperationResult.SUCCESS, servletRequest);
        return response(config);
    }

    public Long resolveModelId(Long tenantId, Long projectId, String serviceType) {
        ProjectAiConfigEntity config = findConfig(tenantId, projectId);
        Long configured = switch (serviceType) {
            case "TEXT" -> config == null ? null : config.getTextModelId();
            case "IMAGE" -> config == null ? null : config.getImageModelId();
            case "VIDEO" -> config == null ? null : config.getVideoModelId();
            case "AUDIO", "VOICE" -> config == null ? null : config.getAudioModelId();
            default -> null;
        };
        if (configured != null) {
            return configured;
        }
        Long defaultModelId = defaultModelId(serviceType);
        return defaultModelId == null ? legacyDefaultModelId(serviceType) : defaultModelId;
    }

    private List<ProjectModelOptionResponse> options(String serviceType) {
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                .eq(AiModelEntity::getServiceType, serviceType)
                .eq(AiModelEntity::getStatus, "ENABLED")
                .orderByDesc(AiModelEntity::getSort)
                .orderByDesc(AiModelEntity::getId))
            .stream()
            .filter(this::modelAvailable)
            .map(model -> new ProjectModelOptionResponse(model.getId(), model.getName(), model.getDescription()))
            .toList();
    }

    private void validateModel(Long modelId, String serviceType) {
        if (modelId == null) {
            return;
        }
        AiModelEntity model = aiModelMapper.selectById(modelId);
        if (model == null || !serviceType.equals(model.getServiceType())) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型不存在。");
        }
        if (!"ENABLED".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.AI_MODEL_DISABLED, "AI 模型已停用。");
        }
        if (!providerEnabled(model)) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_DISABLED, "AI 服务商已停用。");
        }
    }

    private boolean providerEnabled(AiModelEntity model) {
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        return provider != null && "ENABLED".equals(provider.getStatus());
    }

    private Long defaultModelId(String serviceType) {
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getStatus, "ENABLED")
            .eq(AiModelEntity::getIsDefault, true)
            .orderByDesc(AiModelEntity::getSort)
            .last("limit 1"));
        return model == null || !modelAvailable(model) ? null : model.getId();
    }

    private Long legacyDefaultModelId(String serviceType) {
        AiServiceConfigEntity config = aiServiceConfigMapper.selectOne(new LambdaQueryWrapper<AiServiceConfigEntity>()
            .eq(AiServiceConfigEntity::getServiceType, serviceType)
            .eq(AiServiceConfigEntity::getIsDefault, true)
            .eq(AiServiceConfigEntity::getEnabled, true)
            .isNull(AiServiceConfigEntity::getDeletedAt)
            .orderByDesc(AiServiceConfigEntity::getPriority)
            .orderByDesc(AiServiceConfigEntity::getId)
            .last("limit 1"));
        if (config == null) {
            return null;
        }
        AiModelEntity existing = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getLegacyServiceConfigId, config.getId())
            .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        AiProviderEntity provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
            .eq(AiProviderEntity::getCode, config.getProvider())
            .eq(AiProviderEntity::getStatus, "ENABLED")
            .last("limit 1"));
        if (provider == null) {
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        AiModelEntity model = new AiModelEntity();
        model.setProviderId(provider.getId());
        model.setCode("%s_%s_%d".formatted(config.getProvider().replaceAll("[^A-Za-z0-9]", "_"), config.getServiceType(), config.getId()));
        model.setName(config.getName());
        model.setModelCode(config.getModel());
        model.setServiceType(config.getServiceType());
        model.setDescription(config.getRemark());
        model.setStatus("ENABLED");
        model.setIsDefault(false);
        model.setSort(config.getPriority() == null ? 0 : config.getPriority());
        model.setLegacyServiceConfigId(config.getId());
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        aiModelMapper.insert(model);
        return model.getId();
    }

    private boolean modelAvailable(AiModelEntity model) {
        if (!providerEnabled(model)) {
            return false;
        }
        if (model.getLegacyServiceConfigId() == null) {
            return true;
        }
        AiServiceConfigEntity config = aiServiceConfigMapper.selectById(model.getLegacyServiceConfigId());
        return config != null && config.getDeletedAt() == null && Boolean.TRUE.equals(config.getEnabled());
    }

    private ProjectEntity requireProject(TenantContext context, Long projectId) {
        ProjectEntity project = projectMapper.selectByTenantIdAndId(context.tenantId(), projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        return project;
    }

    private ProjectAiConfigEntity findConfig(Long tenantId, Long projectId) {
        return projectAiConfigMapper.selectOne(new LambdaQueryWrapper<ProjectAiConfigEntity>()
            .eq(ProjectAiConfigEntity::getTenantId, tenantId)
            .eq(ProjectAiConfigEntity::getProjectId, projectId)
            .last("limit 1"));
    }

    private ProjectAiConfigResponse response(ProjectAiConfigEntity config) {
        return new ProjectAiConfigResponse(
            config.getProjectId(),
            config.getTextModelId(),
            config.getImageModelId(),
            config.getVideoModelId(),
            config.getAudioModelId()
        );
    }
}
