package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
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
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class ReviewDeepAgentCoordinatorTest {
    private ReviewAgentExecutionPlanFactory plans;
    private WorkflowAgentRunner runner;
    private ReviewContentService content;
    private ReviewUnitPlanner planner;
    private ReviewFanoutRepository fanout;
    private ReviewTaskMapper tasks;
    private ReviewScriptVersionMapper versions;
    private JdbcTemplate jdbc;
    private ReviewDeepAgentCoordinator coordinator;
    private WorkflowAgentExecutionPlan childPlan;
    private WorkflowAgentExecutionPlan aggregationPlan;
    private ReviewTaskEntity task;

    @BeforeEach
    void setUp() {
        plans = mock(ReviewAgentExecutionPlanFactory.class);
        runner = mock(WorkflowAgentRunner.class);
        content = mock(ReviewContentService.class);
        planner = mock(ReviewUnitPlanner.class);
        fanout = mock(ReviewFanoutRepository.class);
        tasks = mock(ReviewTaskMapper.class);
        versions = mock(ReviewScriptVersionMapper.class);
        jdbc = mock(JdbcTemplate.class);
        coordinator = new ReviewDeepAgentCoordinator(plans, runner, content, planner, fanout,
            tasks, versions, jdbc, new ObjectMapper(), true, 100, 10, 2);
        childPlan = plan(3L);
        aggregationPlan = plan(4L);
        when(plans.freeze(List.of("台词合理性"), "DEEP_CHILD")).thenReturn(childPlan);
        when(plans.freeze(List.of("台词合理性"), "DEEP_AGGREGATION")).thenReturn(aggregationPlan);
        task = task("RUNNING");
        ReviewScriptVersionEntity version = new ReviewScriptVersionEntity();
        version.setId(6L);
        version.setProjectId(5L);
        version.setContent("abcdefghij");
        when(versions.selectById(6L)).thenReturn(version);
        ReviewContentService.FrozenReview frozen = new ReviewContentService.FrozenReview(
            "abcdefghij", "version-hash", "scope-hash", "dimension-hash", "snapshot", List.of(), 1);
        when(content.freeze(any(), any(), any(), any())).thenReturn(frozen);
        when(planner.plan(any(), any(), any(), eq(frozen), eq(100), eq(10))).thenReturn(List.of(
            new ReviewUnitPlanner.Unit(1, "offset-0-5", 0, 5, "abcde", "fingerprint-1"),
            new ReviewUnitPlanner.Unit(2, "offset-5-10", 5, 10, "fghij", "fingerprint-2")));
    }

    @Test
    void freezesConcurrencyAndRunsOneChildForEachNewUnitBeforeOneAggregation() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(null);
        when(fanout.openSnapshot(any())).thenReturn(50L);
        ReviewFanoutUnitEntity first = unit(11L, 1, "PENDING", false);
        ReviewFanoutUnitEntity second = unit(12L, 2, "PENDING", false);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(first, second),
            List.of(unit(11L, 1, "SUCCEEDED", true), second),
            List.of(first, unit(12L, 2, "SUCCEEDED", true)));
        when(runner.runFormal(eq(childPlan), any())).thenReturn(
            new WorkflowAgentRunResult(101L, "saved"), new WorkflowAgentRunResult(102L, "saved"));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "saved"));
        when(tasks.selectById(7L)).thenReturn(committed(200L));

        ReviewDeepAgentCoordinator.Execution result = coordinator.execute(task, execution(), 9L);

        assertThat(result.snapshotId()).isEqualTo(50L);
        assertThat(result.aggregationRunId()).isEqualTo(200L);
        ArgumentCaptor<ReviewFanoutRepository.SnapshotDraft> snapshot =
            ArgumentCaptor.forClass(ReviewFanoutRepository.SnapshotDraft.class);
        verify(fanout).openSnapshot(snapshot.capture());
        assertThat(snapshot.getValue().maxConcurrency()).isEqualTo(2);
        verify(fanout, times(2)).addUnit(any());
        ArgumentCaptor<WorkflowAgentRunInput> inputs = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner, times(3)).runFormal(any(), inputs.capture());
        assertThat(inputs.getAllValues()).extracting(input -> input.reviewScope().phase())
            .containsExactly("DEEP_CHILD", "DEEP_CHILD", "DEEP_AGGREGATION");
        verify(tasks, atLeastOnce()).updateById(any(ReviewTaskEntity.class));
    }

    @Test
    void restoresProgressAndRetriesOnlyFailedOrMissingCandidateUnits() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(3);
        ReviewFanoutUnitEntity done = unit(11L, 1, "SUCCEEDED", true);
        ReviewFanoutUnitEntity failed = unit(12L, 2, "FAILED", false);
        ReviewFanoutUnitEntity missingCandidate = unit(13L, 3, "SUCCEEDED", false);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(done, failed, missingCandidate),
            List.of(done, unit(12L, 2, "SUCCEEDED", true), missingCandidate),
            List.of(done, failed, unit(13L, 3, "SUCCEEDED", true)));
        when(runner.runFormal(eq(childPlan), any())).thenReturn(
            new WorkflowAgentRunResult(102L, "saved"), new WorkflowAgentRunResult(103L, "saved"));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "saved"));
        when(tasks.selectById(7L)).thenReturn(committed(200L));

        coordinator.execute(task, execution(), 9L);

        ArgumentCaptor<WorkflowAgentRunInput> inputs = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner, times(3)).runFormal(any(), inputs.capture());
        assertThat(inputs.getAllValues().stream().filter(input ->
            "DEEP_CHILD".equals(input.reviewScope().phase())).map(input -> input.reviewScope().unitId()))
            .containsExactly(12L, 13L);
        verify(fanout, never()).addUnit(any());
    }

    @Test
    void aggregationFailureKeepsCandidatesAndNeverReportsSuccess() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(
            unit(11L, 1, "SUCCEEDED", true), unit(12L, 2, "SUCCEEDED", true)));
        when(tasks.selectById(7L)).thenReturn(task("RUNNING"));
        when(runner.runFormal(eq(aggregationPlan), any())).thenThrow(new IllegalStateException("aggregate failed"));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(IllegalStateException.class).hasMessage("aggregate failed");

        verify(runner, never()).runFormal(eq(childPlan), any());
        verify(jdbc).update(startsWith("update review_fanout_snapshot set status='FAILED'"), eq(50L));
        verify(fanout, never()).addUnit(any());
    }

    @Test
    void cancellationStopsBeforeStartingAnyChildRun() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(1);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(unit(11L, 1, "PENDING", false)));
        when(tasks.selectById(7L)).thenReturn(task("CANCELED"));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("已取消");

        verify(runner, never()).runFormal(any(), any());
    }

    private WorkflowAgentExecutionPlan plan(long revision) {
        WorkflowAgentRecord agent = new WorkflowAgentRecord(1L, "script-review", "审核", "", "", 9L,
            BigDecimal.ZERO, 4096, 8, "ENABLED", revision, 1L, 1L, LocalDateTime.now(), LocalDateTime.now(),
            List.of(), List.of());
        return new WorkflowAgentExecutionPlan(agent, List.of());
    }

    private ReviewTaskEntity task(String status) {
        ReviewTaskEntity value = new ReviewTaskEntity();
        value.setId(7L); value.setTenantId(2L); value.setProjectId(5L); value.setScriptVersionId(6L);
        value.setReviewMode("DEEP"); value.setSelectedDimensionsJson("[\"台词合理性\"]");
        value.setReviewScopeType("ALL"); value.setReviewScopeJson("{}"); value.setRoundNo(1);
        value.setCreatedBy(3L); value.setStatus(status); value.setWorkflowAttemptNo(1);
        return value;
    }

    private ReviewTaskEntity committed(long runId) {
        ReviewTaskEntity value = task("COMPLETED");
        value.setWorkflowAgentRunId(runId);
        return value;
    }

    private ReviewFanoutUnitEntity unit(long id, int number, String status, boolean candidateSaved) {
        ReviewFanoutUnitEntity unit = new ReviewFanoutUnitEntity();
        unit.setId(id); unit.setSnapshotId(50L); unit.setUnitNo(number);
        unit.setUnitKey("unit-" + number); unit.setStatus(status); unit.setCandidateSaved(candidateSaved);
        return unit;
    }

    private AiExecutionContext execution() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 10L; execution.executionVersion = 2;
        return new AiExecutionContext(execution, new AiExecutionClaim(10L, 11L, "claim", 2, "AI_REVIEW"));
    }
}
