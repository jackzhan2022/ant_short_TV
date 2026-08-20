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
    private final Map<String, AiProviderAdapter> adapters;

    public AiModelRouter(
        AiModelMapper aiModelMapper,
        AiProviderMapper aiProviderMapper,
        AiProviderConfigMapper aiProviderConfigMapper,
        List<AiProviderAdapter> adapters
    ) {
        this.aiModelMapper = aiModelMapper;
        this.aiProviderMapper = aiProviderMapper;
        this.aiProviderConfigMapper = aiProviderConfigMapper;
        this.adapters = adapters.stream().collect(Collectors.toMap(adapter -> adapter.providerCode().toUpperCase(Locale.ROOT), Function.identity()));
    }

    public AiModelRoute route(Long modelId, String serviceType) {
        AiModelEntity model = modelId == null ? defaultModel(serviceType) : aiModelMapper.selectById(modelId);
        if (model == null) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型不存在。");
        }
        if (!serviceType.equals(model.getServiceType())) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_NOT_FOUND, "AI 模型类型不匹配。");
        }
        if (!"ENABLED".equals(model.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_MODEL_DISABLED, "AI 模型已停用。");
        }
        AiProviderEntity provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider == null) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_FOUND, "AI 服务商不存在。");
        }
        if (!"ENABLED".equals(provider.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_DISABLED, "AI 服务商已停用。");
        }
        AiProviderConfigEntity config = aiProviderConfigMapper.selectOne(new LambdaQueryWrapper<AiProviderConfigEntity>()
            .eq(AiProviderConfigEntity::getProviderId, provider.getId())
            .last("limit 1"));
        if (config == null || !"ENABLED".equals(config.getStatus())) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_DISABLED, "AI 服务商配置不可用。");
        }
        AiProviderAdapter adapter = adapters.get(provider.getCode().toUpperCase(Locale.ROOT));
        if (adapter == null) {
            throw new AiGatewayException(ErrorCode.AI_PROVIDER_NOT_SUPPORTED, "AI 服务商暂未接入 Gateway。");
        }
        return new AiModelRoute(model, provider, config, adapter);
    }

    private AiModelEntity defaultModel(String serviceType) {
        AiModelEntity model = aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getStatus, "ENABLED")
            .eq(AiModelEntity::getIsDefault, true)
            .orderByDesc(AiModelEntity::getSort)
            .last("limit 1"));
        if (model != null) {
            return model;
        }
        return aiModelMapper.selectOne(new LambdaQueryWrapper<AiModelEntity>()
            .eq(AiModelEntity::getServiceType, serviceType)
            .eq(AiModelEntity::getStatus, "ENABLED")
            .orderByDesc(AiModelEntity::getSort)
            .orderByDesc(AiModelEntity::getId)
            .last("limit 1"));
    }
}
