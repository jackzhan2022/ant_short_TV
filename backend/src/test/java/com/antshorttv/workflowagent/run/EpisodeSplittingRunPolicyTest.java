package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.workflowagent.WorkflowAgentProperties;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EpisodeSplittingRunPolicyTest {
    @Test
    void classifiesOnlyCapacityAndIncompleteCallOutcomesForFallback() {
        EpisodeSplittingRunPolicy policy = new EpisodeSplittingRunPolicy(
            new WorkflowAgentProperties(), input -> "source");
        WorkflowToolRunState state = new WorkflowToolRunState();
        state.recordSuccess("read_current_script");

        assertThat(policy.classify(response("", "length", true), state))
            .contains(EpisodeSplittingRunPolicy.FallbackReason.OUTPUT_TRUNCATED);
        assertThat(policy.classify(response("", "stop", false), state))
            .contains(EpisodeSplittingRunPolicy.FallbackReason.EMPTY_RESPONSE);
        assertThat(policy.classify(response("done", "stop", false), state))
            .contains(EpisodeSplittingRunPolicy.FallbackReason.SAVE_NOT_CALLED);
    }

    @Test
    void preflightUsesConservativeUtf8Estimate() {
        WorkflowAgentProperties properties = new WorkflowAgentProperties();
        properties.setSplitSafeContextTokens(100);
        properties.setSplitPromptReserveTokens(10);
        properties.setSplitToolReserveTokens(10);
        EpisodeSplittingRunPolicy policy = new EpisodeSplittingRunPolicy(
            properties, input -> "中".repeat(100));

        assertThat(policy.preflight(new WorkflowAgentRunInput(
            "short-drama-episode-splitting", "split", 1L, 2L, null, 3L,
            null, null, 4L))).contains(EpisodeSplittingRunPolicy.FallbackReason.CONTEXT_PREFLIGHT);
    }

    private AiTextResponse response(String content, String finish, boolean truncated) {
        return new AiTextResponse(content, "req", 1, 1, 2, 1L, Map.of(), finish, truncated, List.of());
    }
}
