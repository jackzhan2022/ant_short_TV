package com.antshorttv.workflowagent.agent;

import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiModelCapabilityEntity;
import com.antshorttv.ai.AiModelCapabilityMapper;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAgentModelLookup {
    private final AiModelMapper models;
    private final AiModelCapabilityMapper capabilities;

    public WorkflowAgentModelLookup(AiModelMapper models, AiModelCapabilityMapper capabilities) {
        this.models = models;
        this.capabilities = capabilities;
    }

    public WorkflowAgentModel requireEnabledTextModel(Long modelId) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "模型不存在。");
        }
        AiModelEntity model = models.selectById(modelId);
        if (model == null) {
            throw new BusinessException(ErrorCode.AI_MODEL_NOT_FOUND, "模型不存在。");
        }
        if (!"ENABLED".equals(model.getStatus())) {
            throw new BusinessException(ErrorCode.AI_MODEL_DISABLED, "模型未启用。");
        }
        if (!"TEXT".equals(model.getServiceType())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Agent 只能使用文本模型。");
        }
        Long supported = capabilities.selectCount(new LambdaQueryWrapper<AiModelCapabilityEntity>()
            .eq(AiModelCapabilityEntity::getModelId, modelId)
            .eq(AiModelCapabilityEntity::getCapability, "TOOL_CALLING")
            .eq(AiModelCapabilityEntity::getStatus, "ENABLED"));
        if (supported == null || supported == 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所选模型不支持原生工具调用。");
        }
        return new WorkflowAgentModel(model.getId(), model.getCode(), model.getServiceType());
    }
}
