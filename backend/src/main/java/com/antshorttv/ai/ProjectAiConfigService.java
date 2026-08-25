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
    private final AiModelRouter aiModelRouter;
    private final OperationLogService operationLogService;

    public ProjectAiConfigService(
        TenantContextResolver tenantContextResolver,
        ProjectMapper projectMapper,
        ProjectAiConfigMapper projectAiConfigMapper,
        AiModelMapper aiModelMapper,
        AiModelRouter aiModelRouter,
        OperationLogService operationLogService
    ) {
        this.tenantContextResolver = tenantContextResolver;
        this.projectMapper = projectMapper;
        this.projectAiConfigMapper = projectAiConfigMapper;
        this.aiModelMapper = aiModelMapper;
        this.aiModelRouter = aiModelRouter;
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
        return defaultModelId(serviceType);
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
        aiModelRouter.route(modelId, serviceType);
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

    private boolean modelAvailable(AiModelEntity model) {
        try {
            aiModelRouter.route(model.getId(), model.getServiceType());
            return true;
        } catch (AiGatewayException exception) {
            return false;
        }
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
