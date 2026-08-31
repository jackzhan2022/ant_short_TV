package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReviewQuickAgentAdapterTest {
    @Test
    void dispatchesOneFrozenQuickRunAndRequiresItsFormalCommit() {
        ReviewAgentExecutionPlanFactory plans = mock(ReviewAgentExecutionPlanFactory.class);
        WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);
        ReviewTaskMapper tasks = mock(ReviewTaskMapper.class);
        WorkflowAgentRecord agent = new WorkflowAgentRecord(1L, "script-review", "审核", "", "", 9L,
            BigDecimal.ZERO, 4096, 8, "ENABLED", 3L, 1L, 1L, LocalDateTime.now(), LocalDateTime.now(),
            List.of(), List.of("read_review_context", "save_review_result"));
        WorkflowAgentExecutionPlan plan = new WorkflowAgentExecutionPlan(agent, List.of());
        when(plans.freeze(List.of("台词合理性"), "QUICK")).thenReturn(plan);
        when(runner.runFormal(any(), any())).thenReturn(new WorkflowAgentRunResult(88L, "saved"));
        ReviewTaskEntity task = task();
        ReviewTaskEntity committed = task();
        committed.setStatus("COMPLETED");
        committed.setWorkflowAgentRunId(88L);
        when(tasks.selectById(7L)).thenReturn(committed);

        ReviewQuickAgentAdapter adapter = new ReviewQuickAgentAdapter(plans, runner, tasks,
            new ObjectMapper(), true);
        ReviewQuickAgentAdapter.Execution result = adapter.execute(task, execution(), 9L);

        assertThat(result.runId()).isEqualTo(88L);
        ArgumentCaptor<WorkflowAgentRunInput> input = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner).runFormal(org.mockito.ArgumentMatchers.eq(plan), input.capture());
        assertThat(input.getValue().reviewScope().phase()).isEqualTo("QUICK");
        assertThat(input.getValue().reviewScope().versionId()).isEqualTo(6L);
        assertThat(input.getValue().input()).doesNotContain("林夏");
    }

    private ReviewTaskEntity task() {
        ReviewTaskEntity task = new ReviewTaskEntity();
        task.setId(7L); task.setTenantId(2L); task.setProjectId(5L); task.setScriptVersionId(6L);
        task.setReviewMode("QUICK"); task.setSelectedDimensionsJson("[\"台词合理性\"]");
        task.setRoundNo(1); task.setCreatedBy(3L); task.setStatus("RUNNING");
        return task;
    }

    private AiExecutionContext execution() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 10L; execution.executionVersion = 2;
        return new AiExecutionContext(execution, new AiExecutionClaim(10L, 11L, "claim", 2, "AI_REVIEW"));
    }
}
