package com.antshorttv.ai;

import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AiModelRouter {
    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;
    private final AiProviderConfigMapper aiProviderConfigMapper;
    private final AiServiceConfigMapper aiServiceConfigMapper;
    private final AiModelCapabilityMapper aiModelCapabilityMapper;
    private final Map<String, AiProviderAdapter> adapters;

    public AiModelRouter(
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        AiProviderConfigMapper aiProviderConfigMapper,
        AiServiceConfigMapper aiServiceConfigMapper,
        AiModelCapabilityMapper aiModelCapabilityMapper,
        List<AiProviderAdapter> adapters
    ) {
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.aiServiceConfigMapper = aiServiceConfigMapper;
        this.aiModelCapabilityMapper = aiModelCapabilityMapper;
        this.adapters = adapters.stream().collect(Collectors.toMap(adapter -> adapter.providerCode().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public AiModelRoute route(Long modelId, String serviceType) {
        return route(modelId, capabilityForServiceType(serviceType));
    }

    public AiModelRoute route(Long modelId, AiCapability capability) {
        String serviceType = capability.modelServiceType();
        AiModelEntity model = modelId == null ? defaultModel(capability) : aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型不存在。");
        }
        if (!serviceType.equals(model.getServiceType())) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型类型不匹配。");
        }
        if (!"ENABLED".equals(model.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_DISABLED, "AI 模型已停用。");
        }
        if (!hasEnabledCapability(model.getId(), capability)) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型未启用所需能力。");
        }
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_FOUND, "AI 服务商不存在。");
        }
        if (!"ENABLED".equals(provider.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_DISABLED, "AI 服务商已停用。");
        }
        AiProviderConfigEntity config = legacyConfig(model);
        if (config == null) {
            config = aiProviderConfigMapper.selectOne(new LambdaQueryWrapper<AiProviderConfigEntity>()
            .eq(AiProviderConfigEntity::getProviderId, provider.getId())
            .last("limit 1"));
        }
        if (config == null || !"ENABLED".equals(config.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_DISABLED, "AI 服务商配置不可用。");
        }
        AiProviderAdapter adapter = adapters.get(provider.getCode().toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED, "AI 服务商暂未接入 Gateway。");
        }
        return new AiModelRoute(model, provider, config, adapter);
    }

    private boolean hasEnabledCapability(Long modelId, AiCapability capability) {
        String capabilityCode = capabilityCode(capability);
        return aiModelCapabilityMapper.selectOne(new LambdaQueryWrapper<AiModelCapabilityEntity>()
            .eq(AiModelCapabilityEntity::getModelId, modelId)
            .eq(AiModelCapabilityEntity::getCapability, capabilityCode)
            .eq(AiModelCapabilityEntity::getStatus, "ENABLED")
            .last("limit 1")) != null;
    }

    private AiProviderConfigEntity legacyConfig(AiModelEntity model) {
        if (model.getLegacyServiceConfigId() == null) {
            return null;
        }
        AiServiceConfigEntity serviceConfig = aiServiceConfigMapper.selectById(model.getLegacyServiceConfigId());
        if (serviceConfig == null || serviceConfig.getDeletedAt() != null || !Boolean.TRUE.equals(serviceConfig.getEnabled())) {
            return null;
        }
        AiProviderConfigEntity config = new AiProviderConfigEntity();
        config.setId(serviceConfig.getId());
        config.setBaseUrl(serviceConfig.getBaseUrl());
        config.setApiKeyCipher(serviceConfig.getApiKeyCipher());
        config.setExtraConfig(serviceConfig.getEndpoint());
        config.setStatus("ENABLED");
        return config;
    }

    private AiModelEntity defaultModel(AiCapability capability) {
        String serviceType = capability.modelServiceType();
        String capabilityCode = capabilityCode(capability);
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getStatus, "ENABLED")
            .eq(AiModelEntity::getIsDefault, true)
            .inSql(AiModelEntity::getId, "select model_id from ai_model_capability where capability = '" + capabilityCode + "' and status = 'ENABLED'")
            .orderByDesc(AiModelEntity::getSort)
            .last("limit 1"));
        if (model != null) {
            return model;
        }
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getStatus, "ENABLED")
            .inSql(AiModelEntity::getId, "select model_id from ai_model_capability where capability = '" + capabilityCode + "' and status = 'ENABLED'")
            .orderByDesc(AiModelEntity::getSort)
            .orderByDesc(AiModelEntity::getId)
            .last("limit 1"));
    }

    private AiCapability capabilityForServiceType(String serviceType) {
        return switch (serviceType) {
            case "TEXT" -> AiCapability.TEXT;
            case "IMAGE" -> AiCapability.IMAGE;
            case "VIDEO_UNDERSTANDING" -> AiCapability.VIDEO_UNDERSTANDING;
            case "VIDEO" -> AiCapability.VIDEO;
            case "AUDIO", "VOICE" -> AiCapability.AUDIO;
            default -> throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型类型不匹配。");
        };
    }

    private String capabilityCode(AiCapability capability) {
        return switch (capability) {
            case TEXT -> "TEXT_GENERATION";
            case IMAGE -> "IMAGE_GENERATION";
            case VIDEO_UNDERSTANDING -> "VIDEO_UNDERSTANDING";
            case VIDEO -> "VIDEO_GENERATION";
            case AUDIO -> "AUDIO_GENERATION";
        };
    }
}
