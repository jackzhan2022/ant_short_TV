package com.antshorttv.workflowagent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.ai.AiModelCapabilityMapper;
import com.antshorttv.ai.AiModelEntity;
import com.antshorttv.ai.AiModelMapper;
import com.antshorttv.common.BusinessException;
import org.junit.jupiter.api.Test;

class WorkflowAgentModelLookupTest {
    private final AiModelMapper models = mock(AiModelMapper.class);
    private final AiModelCapabilityMapper capabilities = mock(AiModelCapabilityMapper.class);
    private final WorkflowAgentModelLookup lookup = new WorkflowAgentModelLookup(models, capabilities);

    @Test
    void acceptsOnlyEnabledTextModelsWithToolCallingCapability() {
        AiModelEntity model = new AiModelEntity();
        model.setId(8L);
        model.setCode("tool-model");
        model.setServiceType("TEXT");
        model.setStatus("ENABLED");
        when(models.selectById(8L)).thenReturn(model);
        when(capabilities.selectCount(any())).thenReturn(1L);

        assertThat(lookup.requireEnabledTextModel(8L).code()).isEqualTo("tool-model");

        when(capabilities.selectCount(any())).thenReturn(0L);
        assertThatThrownBy(() -> lookup.requireEnabledTextModel(8L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("不支持原生工具调用");
    }
}
