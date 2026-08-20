package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.security.CurrentUser;
import com.antshorttv.security.CurrentUserHolder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAiManagementService {
    private static final List<String> SERVICE_TYPES = List.of("TEXT", "IMAGE", "VIDEO", "AUDIO", "VOICE");

    private final AiProviderMapper aiProviderMapper;
    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelCapabilityMapper capabilityMapper;
    private final AiSecretCodec aiSecretCodec;
    private final OperationLogService operationLogService;
    private final AiModelRouter aiModelRouter;

    public PlatformAiManagementService(
        AiProviderMapper aiProviderMapper,
        AiProviderConfigMapper aiProviderConfigMapper,
        AiModelMapper aiModelMapper,
        AiModelCapabilityMapper capabilityMapper,
        AiSecretCodec aiSecretCodec,
        OperationLogService operationLogService,
        AiModelRouter aiModelRouter
    ) {
        this.aiProviderMapper = aiProviderMapper;
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.aiModelMapper = aiModelMapper;
        this.capabilityMapper = capabilityMapper;
        this.aiSecretCodec = aiSecretCodec;
        this.operationLogService = operationLogService;
        this.aiModelRouter = aiModelRouter;
    }

    public List<PlatformProviderResponse> providers() {
        CurrentUserHolder.require();
        return aiProviderMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>().orderByAsc(AiProviderEntity::getId))
            .stream()
            .map(this::providerResponse)
            .toList();
    }

    @Transactional
    public PlatformProviderResponse createProvider(PlatformProviderRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        LocalDateTime now = LocalDateTime.now();
        AiProviderEntity provider = new AiProviderEntity();
        provider.setName(request.name().trim());
        provider.setCode(request.code().trim());
        provider.setSupportedTypes(blankToDefault(request.supportedTypes(), "TEXT"));
        provider.setDefaultBaseUrl(blankToNull(request.defaultBaseUrl()) == null ? blankToNull(request.baseUrl()) : request.defaultBaseUrl().trim());
        provider.setDescription(blankToNull(request.description()));
        provider.setStatus(Boolean.FALSE.equals(request.enabled()) ? "DISABLED" : "ENABLED");
        provider.setCreatedAt(now);
        provider.setUpdatedAt(now);
        aiProviderMapper.insert(provider);
        upsertProviderConfig(provider, request.apiKey(), request.baseUrl(), provider.getStatus(), now);
        operationLogService.record(user.userId(), null, "CREATE_PLATFORM_AI_PROVIDER", provider.getId(), OperationResult.SUCCESS, servletRequest);
        return providerResponse(provider);
    }

    @Transactional
    public PlatformProviderResponse updateProvider(Long id, PlatformProviderRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiProviderEntity provider = requireProvider(id);
        LocalDateTime now = LocalDateTime.now();
        provider.setName(request.name().trim());
        provider.setCode(request.code().trim());
        provider.setSupportedTypes(blankToDefault(request.supportedTypes(), "TEXT"));
        provider.setDefaultBaseUrl(blankToNull(request.defaultBaseUrl()) == null ? blankToNull(request.baseUrl()) : request.defaultBaseUrl().trim());
        provider.setDescription(blankToNull(request.description()));
        provider.setStatus(Boolean.FALSE.equals(request.enabled()) ? "DISABLED" : "ENABLED");
        provider.setUpdatedAt(now);
        aiProviderMapper.updateById(provider);
        upsertProviderConfig(provider, request.apiKey(), request.baseUrl(), provider.getStatus(), now);
        operationLogService.record(user.userId(), null, "UPDATE_PLATFORM_AI_PROVIDER", provider.getId(), OperationResult.SUCCESS, servletRequest);
        return providerResponse(provider);
    }

    @Transactional
    public PlatformProviderResponse updateProviderStatus(Long id, boolean enabled, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiProviderEntity provider = requireProvider(id);
        provider.setStatus(enabled ? "ENABLED" : "DISABLED");
        provider.setUpdatedAt(LocalDateTime.now());
        aiProviderMapper.updateById(provider);
        AiProviderConfigEntity config = providerConfig(provider.getId());
        if (config != null) {
            config.setStatus(provider.getStatus());
            config.setUpdatedAt(provider.getUpdatedAt());
            aiProviderConfigMapper.updateById(config);
        }
        operationLogService.record(user.userId(), null, enabled ? "ENABLE_PLATFORM_AI_PROVIDER" : "DISABLE_PLATFORM_AI_PROVIDER", id, OperationResult.SUCCESS, servletRequest);
        return providerResponse(provider);
    }

    @Transactional
    public AiServiceTestResponse testProvider(Long id, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiProviderEntity provider = requireProvider(id);
        AiProviderConfigEntity config = providerConfig(provider.getId());
        LocalDateTime now = LocalDateTime.now();
        String status = config == null || !"ENABLED".equals(config.getStatus()) ? "FAILED" : "SUCCESS";
        String message = "SUCCESS".equals(status) ? "服务配置可用。" : "服务配置不可用。";
        if (config != null) {
            config.setLastTestStatus(status);
            config.setLastTestMessage(message);
            config.setLastTestAt(now);
            config.setUpdatedAt(now);
            aiProviderConfigMapper.updateById(config);
        }
        operationLogService.record(user.userId(), null, "TEST_PLATFORM_AI_PROVIDER", id, OperationResult.SUCCESS, servletRequest);
        return new AiServiceTestResponse(status, message);
    }

    public List<PlatformModelResponse> models() {
        CurrentUserHolder.require();
        return aiModelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                .orderByAsc(AiModelEntity::getServiceType)
                .orderByDesc(AiModelEntity::getSort)
                .orderByDesc(AiModelEntity::getId))
            .stream()
            .map(this::modelResponse)
            .toList();
    }

    @Transactional
    public PlatformModelResponse createModel(PlatformModelRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiProviderEntity provider = requireProvider(request.providerId());
        validateServiceType(request.serviceType());
        LocalDateTime now = LocalDateTime.now();
        AiModelEntity model = new AiModelEntity();
        fillModel(model, request, now);
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            clearModelDefault(model.getServiceType(), null);
        }
        aiModelMapper.insert(model);
        ensureDefaultCapability(model, now);
        operationLogService.record(user.userId(), null, "CREATE_PLATFORM_AI_MODEL", model.getId(), OperationResult.SUCCESS, servletRequest);
        return modelResponse(model);
    }

    @Transactional
    public PlatformModelResponse updateModel(Long id, PlatformModelRequest request, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiModelEntity model = requireModel(id);
        requireProvider(request.providerId());
        validateServiceType(request.serviceType());
        fillModel(model, request, LocalDateTime.now());
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            clearModelDefault(model.getServiceType(), model.getId());
        }
        aiModelMapper.updateById(model);
        ensureDefaultCapability(model, model.getUpdatedAt());
        operationLogService.record(user.userId(), null, "UPDATE_PLATFORM_AI_MODEL", model.getId(), OperationResult.SUCCESS, servletRequest);
        return modelResponse(model);
    }

    @Transactional
    public PlatformModelResponse updateModelStatus(Long id, boolean enabled, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiModelEntity model = requireModel(id);
        model.setStatus(enabled ? "ENABLED" : "DISABLED");
        model.setUpdatedAt(LocalDateTime.now());
        if (!enabled) {
            model.setIsDefault(false);
        }
        aiModelMapper.updateById(model);
        operationLogService.record(user.userId(), null, enabled ? "ENABLE_PLATFORM_AI_MODEL" : "DISABLE_PLATFORM_AI_MODEL", id, OperationResult.SUCCESS, servletRequest);
        return modelResponse(model);
    }

    @Transactional
    public PlatformModelResponse setDefault(Long id, HttpServletRequest servletRequest) {
        CurrentUser user = CurrentUserHolder.require();
        AiModelEntity model = requireModel(id);
        if (!"ENABLED".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.AI_MODEL_DISABLED, "停用模型不能设为默认。");
        }
        clearModelDefault(model.getServiceType(), model.getId());
        model.setIsDefault(true);
        model.setUpdatedAt(LocalDateTime.now());
        aiModelMapper.updateById(model);
        operationLogService.record(user.userId(), null, "DEFAULT_PLATFORM_AI_MODEL", id, OperationResult.SUCCESS, servletRequest);
        return modelResponse(model);
    }

    void syncLegacyConfig(AiServiceConfigEntity config) {
        AiProviderEntity provider = aiProviderMapper.selectOne(new LambdaQueryWrapper<AiProviderEntity>()
            .eq(AiProviderEntity::getCode, config.getProvider())
            .last("limit 1"));
        if (provider == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        upsertProviderConfig(provider, null, config.getBaseUrl(), provider.getStatus(), now, config.getApiKeyCipher());
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getLegacyServiceConfigId, config.getId())
            .last("limit 1"));
        if (model == null) {
            model = new AiModelEntity();
            model.setCreatedAt(now);
            model.setCode("%s_%s_%d".formatted(safeCode(config.getProvider()), config.getServiceType(), config.getId()));
            model.setLegacyServiceConfigId(config.getId());
        }
        model.setProviderId(provider.getId());
        model.setName(config.getName());
        model.setModelCode(config.getModel());
        model.setServiceType(config.getServiceType());
        model.setDescription(config.getRemark());
        model.setStatus(Boolean.TRUE.equals(config.getEnabled()) ? "ENABLED" : "DISABLED");
        model.setIsDefault(Boolean.TRUE.equals(config.getIsDefault()));
        model.setSort(config.getPriority() == null ? 0 : config.getPriority());
        model.setUpdatedAt(now);
        if (Boolean.TRUE.equals(model.getIsDefault())) {
            clearModelDefault(model.getServiceType(), model.getId());
        }
        if (model.getId() == null) {
            aiModelMapper.insert(model);
        } else {
            aiModelMapper.updateById(model);
        }
        ensureDefaultCapability(model, now);
    }

    private PlatformProviderResponse providerResponse(AiProviderEntity provider) {
        AiProviderConfigEntity config = providerConfig(provider.getId());
        String masked = config == null || config.getApiKeyCipher() == null ? null : aiSecretCodec.mask(config.getApiKeyCipher());
        return new PlatformProviderResponse(
            provider.getId(),
            provider.getName(),
            provider.getCode(),
            provider.getSupportedTypes(),
            provider.getDefaultBaseUrl(),
            config == null ? provider.getDefaultBaseUrl() : config.getBaseUrl(),
            masked,
            provider.getDescription(),
            provider.getStatus(),
            config == null ? "UNTESTED" : config.getLastTestStatus(),
            config == null ? null : config.getLastTestMessage(),
            config == null ? null : config.getLastTestAt(),
            provider.getUpdatedAt()
        );
    }

    private PlatformModelResponse modelResponse(AiModelEntity model) {
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        List<String> capabilities = capabilityMapper.selectList(new LambdaQueryWrapper<AiModelCapabilityEntity>()
                .eq(AiModelCapabilityEntity::getModelId, model.getId())
                .eq(AiModelCapabilityEntity::getStatus, "ENABLED")
                .orderByAsc(AiModelCapabilityEntity::getId))
            .stream()
            .map(AiModelCapabilityEntity::getCapability)
            .toList();
        return new PlatformModelResponse(
            model.getId(), model.getProviderId(), provider == null ? null : provider.getName(), model.getCode(),
            model.getName(), model.getModelCode(), model.getServiceType(), model.getDescription(), model.getStatus(),
            model.getIsDefault(), model.getSort(), capabilities, model.getUpdatedAt()
        );
    }

    private void fillModel(AiModelEntity model, PlatformModelRequest request, LocalDateTime now) {
        model.setProviderId(request.providerId());
        model.setCode(request.code().trim());
        model.setName(request.name().trim());
        model.setModelCode(request.modelCode().trim());
        model.setServiceType(request.serviceType().trim().toUpperCase());
        model.setDescription(blankToNull(request.description()));
        model.setStatus(Boolean.FALSE.equals(request.enabled()) ? "DISABLED" : "ENABLED");
        model.setIsDefault(Boolean.TRUE.equals(request.isDefault()));
        model.setSort(request.sort() == null ? 0 : request.sort());
        model.setConfigJson(blankToNull(request.configJson()));
        if (model.getCreatedAt() == null) {
            model.setCreatedAt(now);
        }
        model.setUpdatedAt(now);
    }

    private void upsertProviderConfig(AiProviderEntity provider, String apiKey, String baseUrl, String status, LocalDateTime now) {
        upsertProviderConfig(provider, apiKey, baseUrl, status, now, null);
    }

    private void upsertProviderConfig(AiProviderEntity provider, String apiKey, String baseUrl, String status, LocalDateTime now, String existingCipher) {
        AiProviderConfigEntity config = providerConfig(provider.getId());
        if (config == null) {
            config = new AiProviderConfigEntity();
            config.setProviderId(provider.getId());
            config.setCreatedAt(now);
            config.setLastTestStatus("UNTESTED");
        }
        if (apiKey != null && !apiKey.isBlank()) {
            config.setApiKeyCipher(aiSecretCodec.encrypt(apiKey.trim()));
        } else if (existingCipher != null && !existingCipher.isBlank() && (config.getApiKeyCipher() == null || config.getApiKeyCipher().isBlank())) {
            config.setApiKeyCipher(existingCipher);
        }
        config.setBaseUrl(blankToNull(baseUrl) == null ? provider.getDefaultBaseUrl() : baseUrl.trim());
        config.setStatus(status);
        config.setUpdatedAt(now);
        if (config.getId() == null) {
            aiProviderConfigMapper.insert(config);
        } else {
            aiProviderConfigMapper.updateById(config);
        }
    }

    private void ensureDefaultCapability(AiModelEntity model, LocalDateTime now) {
        String capability = switch (model.getServiceType()) {
            case "TEXT" -> "TEXT_GENERATION";
            case "IMAGE" -> "IMAGE_GENERATION";
            case "VIDEO" -> "VIDEO_GENERATION";
            case "AUDIO", "VOICE" -> "AUDIO_GENERATION";
            default -> model.getServiceType() + "_GENERATION";
        };
        AiModelCapabilityEntity entity = capabilityMapper.selectOne(new LambdaQueryWrapper<AiModelCapabilityEntity>()
            .eq(AiModelCapabilityEntity::getModelId, model.getId())
            .eq(AiModelCapabilityEntity::getCapability, capability)
            .last("limit 1"));
        if (entity == null) {
            entity = new AiModelCapabilityEntity();
            entity.setModelId(model.getId());
            entity.setCapability(capability);
            entity.setCreatedAt(now);
        }
        entity.setStatus(model.getStatus());
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            capabilityMapper.insert(entity);
        } else {
            capabilityMapper.updateById(entity);
        }
    }

    private void clearModelDefault(String serviceType, Long exceptId) {
        for (AiModelEntity model : aiModelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getIsDefault, true))) {
            if (exceptId != null && exceptId.equals(model.getId())) {
                continue;
            }
            model.setIsDefault(false);
            model.setUpdatedAt(LocalDateTime.now());
            aiModelMapper.updateById(model);
        }
    }

    private AiProviderEntity requireProvider(Long id) {
        AiProviderEntity provider = aiProviderMapper.selectById(id);
        if (provider == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_NOT_FOUND, "AI 服务商不存在。");
        }
        return provider;
    }

    private AiModelEntity requireModel(Long id) {
        AiModelEntity model = aiModelMapper.selectById(id);
        if (model == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型不存在。");
        }
        return model;
    }

    private AiProviderConfigEntity providerConfig(Long providerId) {
        return aiProviderConfigMapper.selectOne(new LambdaQueryWrapper<AiProviderConfigEntity>()
            .eq(AiProviderConfigEntity::getProviderId, providerId)
            .last("limit 1"));
    }

    private void validateServiceType(String serviceType) {
        if (serviceType == null || !SERVICE_TYPES.contains(serviceType.trim().toUpperCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 模型服务类型不正确。");
        }
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String safeCode(String value) {
        return value == null ? "MODEL" : value.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }
}
