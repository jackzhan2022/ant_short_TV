package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiTextUsageTest {
    @Test
    void completeCallsCannotHideOneMissingUsageResponse() {
        var complete = new WorkflowAgentModelCall(1L,2L,3L,"a","SUCCESS","SUCCESS",4L,100,20,80,null);
        var missing = new WorkflowAgentModelCall(2L,2L,3L,"b","FAILED","FAILED",5L);
        assertThat(AiTextUsage.totals(List.of(complete,missing)))
            .containsEntry(AiUsageMetric.CALL,BigDecimal.valueOf(2))
            .doesNotContainKeys(AiUsageMetric.INPUT_TOKEN,AiUsageMetric.OUTPUT_TOKEN);
    }

    @Test
    void deduplicatesOnlyKnownCallIds() {
        var logged = new WorkflowAgentModelCall(1L,2L,3L,"a","SUCCESS","SUCCESS",4L,100,20,null,null);
        var unlogged = new WorkflowAgentModelCall(null,2L,3L,"b","SUCCESS","SUCCESS",4L,100,20,null,null);
        assertThat(AiTextUsage.totals(List.of(logged,logged,unlogged,unlogged)))
            .containsEntry(AiUsageMetric.CALL,BigDecimal.valueOf(3))
            .containsEntry(AiUsageMetric.INPUT_TOKEN,BigDecimal.valueOf(300));
    }
}
