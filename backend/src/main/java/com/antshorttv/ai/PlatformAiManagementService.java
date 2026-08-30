package com.antshorttv.ai;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.operationlog.OperationLogService;
import com.antshorttv.operationlog.OperationResult;
import com.antshorttv.authsession.AuthenticatedUser;
import com.antshorttv.security.CurrentPrincipal;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PlatformAiManagementService {
    private static final List<String> SERVICE_TYPES = List.of("TEXT", "IMAGE", "VIDEO", "VIDEO_UNDERSTANDING", "AUDIO", "VOICE");

    private final AiProviderMapper aiProviderMapper;
    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final AiModelMapper aiModelMapper;
    private final AiModelCapabilityMapper capabilityMapper;
    private final AiModelParameterProfileMapper parameterMapper;
    private final AiSecretCodec aiSecretCodec;
    private final OperationLogService operationLogService;
    private final AiModelRouter aiModelRouter;
    private final CurrentPrincipal currentPrincipal;

    @Autowired
    public PlatformAiManagementService(
        AiProviderMapper aiProviderMapper,
        AiProviderConfigMapper aiProviderConfigMapper,
        AiModelMapper aiModelMapper,
        AiModelCapabilityMapper capabilityMapper,
        AiModelParameterProfileMapper parameterMapper,
        AiSecretCodec aiSecretCodec,
        OperationLogService operationLogService,
        AiModelRouter aiModelRouter,
        CurrentPrincipal currentPrincipal
    ) {
        this.aiProviderMapper = aiProviderMapper;
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.aiModelMapper = aiModelMapper;
        this.capabilityMapper = capabilityMapper;
        this.parameterMapper = parameterMapper;
        this.aiSecretCodec = aiSecretCodec;
        this.operationLogService = operationLogService;
        this.aiModelRouter = aiModelRouter;
        this.currentPrincipal = currentPrincipal;
    }

    public PlatformAiManagementService(
        AiProviderMapper aiProviderMapper,
        AiProviderConfigMapper aiProviderConfigMapper,
        AiModelMapper aiModelMapper,
        AiModelCapabilityMapper capabilityMapper,
        AiSecretCodec aiSecretCodec,
        OperationLogService operationLogService,
        AiModelRouter aiModelRouter,
        CurrentPrincipal currentPrincipal
    ) {
        this(aiProviderMapper, aiProviderConfigMapper, aiModelMapper, capabilityMapper, null,
            aiSecretCodec, operationLogService, aiModelRouter, currentPrincipal);
    }

    public AiModelParameterResponse modelParameters(Long modelId) {
        currentPrincipal.require();
        requireModel(modelId);
        if (parameterMapper == null) return parameterResponse(defaultParameterProfile(modelId, 1));
        AiModelParameterProfileEntity profile = parameterMapper.selectOne(new LambdaQueryWrapper<AiModelParameterProfileEntity>()
            .eq(AiModelParameterProfileEntity::getModelId, modelId)
            .eq(AiModelParameterProfileEntity::getPublished, true)
            .orderByDesc(AiModelParameterProfileEntity::getVersionNo)
            .last("limit 1"));
        if (profile == null) {
            profile = defaultParameterProfile(modelId, 1);
        }
        return parameterResponse(profile);
    }

    @Transactional
    public AiModelParameterResponse updateModelParameters(Long modelId, AiModelParameterRequest request, HttpServletRequest servletRequest) {
        AuthenticatedUser user = currentPrincipal.require();
        requireModel(modelId);
        validateParameters(request);
        AiModelParameterProfileEntity current = parameterMapper.selectOne(new LambdaQueryWrapper<AiModelParameterProfileEntity>()
            .eq(AiModelParameterProfileEntity::getModelId, modelId).orderByDesc(AiModelParameterProfileEntity::getVersionNo).last("limit 1"));
        int version = current == null || current.getVersionNo() == null ? 1 : current.getVersionNo() + 1;
        if (current != null) { current.setPublished(false); parameterMapper.updateById(current); }
        AiModelParameterProfileEntity profile = defaultParameterProfile(modelId, version);
        profile.setTemperature(request.temperature()); profile.setTopP(request.topP()); profile.setMaxTokens(request.maxTokens());
        profile.setJsonMode(Boolean.TRUE.equals(request.jsonMode())); profile.setTimeoutSeconds(request.timeoutSeconds()); profile.setRetryCount(request.retryCount());
        profile.setCreatedBy(user.userId()); parameterMapper.insert(profile);
        operationLogService.record(user.userId(), null, "UPDATE_PLATFORM_AI_MODEL_PARAMETERS", modelId, OperationResult.SUCCESS, servletRequest);
        return parameterResponse(profile);
    }

    private void validateParameters(AiModelParameterRequest request) {
        if (request.temperature() == null || request.temperature() < 0 || request.temperature() > 2
            || (request.topP() != null && (request.topP() < 0 || request.topP() > 1))
            || request.maxTokens() == null || request.maxTokens() < 256 || request.maxTokens() > 32768
            || request.timeoutSeconds() == null || request.timeoutSeconds() < 5 || request.timeoutSeconds() > 180
            || request.retryCount() == null || request.retryCount() < 0 || request.retryCount() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型参数超出允许范围。");
        }
    }

    private AiModelParameterProfileEntity defaultParameterProfile(Long modelId, int version) {
        AiModelParameterProfileEntity profile = new AiModelParameterProfileEntity();
        profile.setModelId(modelId); profile.setVersionNo(version); profile.setTemperature(0.7); profile.setMaxTokens(2048);
        profile.setJsonMode(false); profile.setTimeoutSeconds(60); profile.setRetryCount(1); profile.setStatus("ENABLED"); profile.setPublished(true);
        profile.setCreatedAt(LocalDateTime.now()); profile.setUpdatedAt(LocalDateTime.now());
        return profile;
    }

    private AiModelParameterResponse parameterResponse(AiModelParameterProfileEntity profile) {
        return new AiModelParameterResponse(profile.getModelId(), profile.getVersionNo(), profile.getTemperature(), profile.getTopP(), profile.getMaxTokens(), profile.getJsonMode(), profile.getTimeoutSeconds(), profile.getRetryCount(), profile.getStatus(), profile.getPublished());
    }

    public List<PlatformProviderResponse> providers() {
        currentPrincipal.require();
        return aiProviderMapper.selectList(new LambdaQueryWrapper<AiProviderEntity>().orderByAsc(AiProviderEntity::getId))
            .stream()
            .map(this::providerResponse)
            .toList();
    }

    @Transactional
    public PlatformProviderResponse createProvider(PlatformProviderRequest request, HttpServletRequest servletRequest) {
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
        AiProviderEntity provider = requireProvider(id);
        AiProviderConfigEntity config = providerConfig(provider.getId());
        LocalDateTime now = LocalDateTime.now();
        String status = "FAILED";
        String message;
        if (config == null || !"ENABLED".equals(config.getStatus())) {
            message = "服务配置不可用。";
        } else {
            try {
                AiModelEntity model = aiModelMapper.selectList(new LambdaQueryWrapper<AiModelEntity>()
                        .eq(AiModelEntity::getProviderId, provider.getId())
                        .eq(AiModelEntity::getStatus, "ENABLED")
                        .orderByDesc(AiModelEntity::getSort)
                        .last("limit 1"))
                    .stream()
                    .filter(candidate -> capabilityMapper.selectList(new LambdaQueryWrapper<AiModelCapabilityEntity>()
                        .eq(AiModelCapabilityEntity::getModelId, candidate.getId())
                        .eq(AiModelCapabilityEntity::getStatus, "ENABLED")).stream().anyMatch(capability -> "TEXT_GENERATION".equals(capability.getCapability())))
                    .findFirst()
                    .orElseThrow(() -> new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "服务商没有启用的模型能力。"));
                AiModelRoute route = aiModelRouter.route(model.getId(), AiCapability.TEXT.modelServiceType());
                route.adapter().text(provider, config, model, new AiTextRequest(null, "连接测试", 0.0, 8, null));
                status = "SUCCESS";
                message = "服务配置可用。";
            } catch (Exception exception) {
                message = exception.getMessage() == null ? "连接测试失败。" : exception.getMessage();
            }
        }
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
        currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        AuthenticatedUser user = currentPrincipal.require();
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
        String generationCapability = switch (model.getServiceType()) {
            case "TEXT" -> "TEXT_GENERATION";
            case "IMAGE" -> "IMAGE_GENERATION";
            case "VIDEO" -> "VIDEO_GENERATION";
            case "VIDEO_UNDERSTANDING" -> "VIDEO_UNDERSTANDING";
            case "AUDIO", "VOICE" -> "AUDIO_GENERATION";
            default -> model.getServiceType() + "_GENERATION";
        };
        upsertCapability(model, generationCapability, now);
        if ("TEXT".equals(model.getServiceType())) {
            upsertCapability(model, "TOOL_CALLING", now);
        }
    }

    private void upsertCapability(AiModelEntity model, String capability, LocalDateTime now) {
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

}
