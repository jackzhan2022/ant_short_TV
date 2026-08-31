package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.execution.AiExecutionResponseMapper;
import com.antshorttv.execution.AiExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ScriptAnalysisExecutionCoordinatorTest {
    @Test
    void reservesOneAdditionalCallForTheTwoRoundGlobalAgent() {
        ScriptAnalysisExecutionCoordinator coordinator = new ScriptAnalysisExecutionCoordinator(
            mock(ScriptAnalysisTaskMapper.class), mock(ScriptVersionMapper.class),
            mock(ProjectAiConfigService.class), mock(AiExecutionService.class),
            mock(AiExecutionResponseMapper.class));

        ReflectionTestUtils.setField(coordinator, "globalUnderstandingAgentEnabled", false);
        assertThat(coordinator.maximumCallCount("script")).isEqualTo(4);

        ReflectionTestUtils.setField(coordinator, "globalUnderstandingAgentEnabled", true);
        assertThat(coordinator.maximumCallCount("script")).isEqualTo(5);
    }
}
