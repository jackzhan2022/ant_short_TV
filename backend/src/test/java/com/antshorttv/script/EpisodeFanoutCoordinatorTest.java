package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionClaim;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionTaskEntity;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EpisodeFanoutCoordinatorTest {
    private final EpisodeFanoutStore store = mock(EpisodeFanoutStore.class);
    private final WorkflowAgentRunner runner = mock(WorkflowAgentRunner.class);

    @Test
    void freezesOnePlanCreatesOneChildPerSnapshotEpisodeAndBoundsConcurrency() {
        EpisodeFanoutCoordinator coordinator = new EpisodeFanoutCoordinator(store, runner, 2);
        WorkflowAgentExecutionPlan plan = plan();
        List<EpisodeFanoutCoordinator.EpisodeUnit> units = units();
        when(runner.freezeFormal("short-drama-episode-summary")).thenReturn(plan);
        when(store.currentEpisodes(7L, 8L, 9L)).thenReturn(units);
        when(store.openSnapshot(any(), any(), any(), eq(plan), eq(99L), eq(units), any(), eq(false))).thenReturn(50L);
        when(store.runnableUnits(50L)).thenReturn(units);
        when(store.markRunning(eq(50L), anyLong())).thenReturn(1);
        when(store.snapshotMatches(eq(50L), any())).thenReturn(true);
        AtomicInteger completed = new AtomicInteger();
        when(store.progress(50L)).thenAnswer(ignored -> new EpisodeFanoutCoordinator.Progress(
            3, completed.get(), 0, 3 - completed.get(), 0,
            completed.get() == 3 ? "SUCCEEDED" : "RUNNING"));
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();

        EpisodeFanoutCoordinator.Result result = coordinator.execute(task(), stage(), null, 99L,
            "short-drama-episode-summary", false, (frozen, task, stage, execution, unit) -> {
                assertThat(frozen).isSameAs(plan);
                int current = active.incrementAndGet();
                maximum.accumulateAndGet(current, Math::max);
                try {
                    Thread.sleep(20);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                active.decrementAndGet();
                completed.incrementAndGet();
                return new EpisodeFanoutCoordinator.ChildResult(100L + unit.episodeId(), List.of());
            }, snapshotId -> { });

        assertThat(result.progress().completed()).isEqualTo(3);
        assertThat(maximum.get()).isLessThanOrEqualTo(2);
        verify(runner).freezeFormal("short-drama-episode-summary");
        verify(store).openSnapshot(any(), any(), any(), eq(plan), eq(99L), eq(units), any(), eq(false));
        verify(store, times(3)).markRunning(eq(50L), anyLong());
        verify(store, times(3)).markSucceeded(eq(50L), anyLong(), eq(1), anyLong());
        verify(store).complete(50L);
    }

    @Test
    void targetedRetrySchedulesOnlyFailedOrMissingUnitsAndRejectsStaleSnapshot() {
        EpisodeFanoutCoordinator coordinator = new EpisodeFanoutCoordinator(store, runner, 3);
        WorkflowAgentExecutionPlan plan = plan();
        List<EpisodeFanoutCoordinator.EpisodeUnit> units = units();
        EpisodeFanoutCoordinator.EpisodeUnit failed = units.get(1);
        when(runner.freezeFormal("short-drama-episode-summary")).thenReturn(plan);
        when(store.currentEpisodes(7L, 8L, 9L)).thenReturn(units);
        when(store.openSnapshot(any(), any(), any(), any(), any(), any(), any(), eq(false))).thenReturn(51L);
        when(store.runnableUnits(51L)).thenReturn(List.of(failed));
        when(store.markRunning(51L, failed.episodeId())).thenReturn(1);
        when(store.progress(51L)).thenReturn(new EpisodeFanoutCoordinator.Progress(
            3, 3, 0, 0, 0, "SUCCEEDED"));
        when(store.snapshotMatches(eq(51L), any())).thenReturn(false);
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.execute(task(), stage(), null, 99L,
            "short-drama-episode-summary", false, (frozen, task, stage, execution, unit) -> {
                runs.incrementAndGet();
                return new EpisodeFanoutCoordinator.ChildResult(202L, List.of());
            }, snapshotId -> { }))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED);
        assertThat(runs.get()).isEqualTo(1);
    }

    @Test
    void partialFailureDoesNotFinalizeAndCancellationStopsBeforeScheduling() {
        EpisodeFanoutCoordinator coordinator = new EpisodeFanoutCoordinator(store, runner, 2);
        WorkflowAgentExecutionPlan plan = plan();
        List<EpisodeFanoutCoordinator.EpisodeUnit> units = units();
        when(runner.freezeFormal("short-drama-episode-summary")).thenReturn(plan);
        when(store.currentEpisodes(7L, 8L, 9L)).thenReturn(units);
        when(store.openSnapshot(any(), any(), any(), any(), any(), any(), any(), eq(false))).thenReturn(52L);
        when(store.runnableUnits(52L)).thenReturn(List.of(units.get(0)));
        when(store.markRunning(52L, units.get(0).episodeId())).thenReturn(1);
        when(store.progress(52L)).thenReturn(new EpisodeFanoutCoordinator.Progress(
            3, 2, 1, 0, 0, "PARTIAL_FAILED"));
        when(store.snapshotMatches(eq(52L), any())).thenReturn(true);
        AtomicInteger finalized = new AtomicInteger();

        assertThatThrownBy(() -> coordinator.execute(task(), stage(), null, 99L,
            "short-drama-episode-summary", false,
            (frozen, task, stage, execution, unit) -> {
                throw new IllegalStateException("provider failed");
            }, snapshotId -> finalized.incrementAndGet()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.ANALYSIS_AGENT_INCOMPLETE);
        assertThat(finalized.get()).isZero();

        when(store.openSnapshot(any(), any(), any(), any(), any(), any(), any(), eq(true))).thenReturn(53L);
        when(store.cancellationRequested(53L, 700L)).thenReturn(true);
        assertThatThrownBy(() -> coordinator.execute(task(), stage(), executionContext(), 99L,
            "short-drama-episode-summary", true,
            (frozen, task, stage, execution, unit) ->
                new EpisodeFanoutCoordinator.ChildResult(1L, List.of()),
            snapshotId -> finalized.incrementAndGet()))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("取消");
        verify(store).cancel(53L);
        verify(store).cancellationRequested(53L, 700L);
        verify(store, never()).runnableUnits(53L);
    }

    @Test
    void executesAUnitOnlyWhenItsAtomicClaimSucceeds() {
        EpisodeFanoutCoordinator coordinator = new EpisodeFanoutCoordinator(store, runner, 1);
        WorkflowAgentExecutionPlan plan = plan();
        List<EpisodeFanoutCoordinator.EpisodeUnit> units = units();
        EpisodeFanoutCoordinator.EpisodeUnit contested = units.get(0);
        when(runner.freezeFormal("short-drama-episode-summary")).thenReturn(plan);
        when(store.currentEpisodes(7L, 8L, 9L)).thenReturn(units);
        when(store.openSnapshot(any(), any(), any(), any(), any(), any(), any(), eq(false))).thenReturn(54L);
        when(store.runnableUnits(54L)).thenReturn(List.of(contested));
        when(store.markRunning(54L, contested.episodeId())).thenReturn(0);
        when(store.progress(54L)).thenReturn(new EpisodeFanoutCoordinator.Progress(
            3, 3, 0, 0, 0, "SUCCEEDED"));
        when(store.snapshotMatches(eq(54L), any())).thenReturn(true);
        AtomicInteger runs = new AtomicInteger();

        coordinator.execute(task(), stage(), null, 99L, "short-drama-episode-summary", false,
            (frozen, task, stage, execution, unit) -> {
                runs.incrementAndGet();
                return new EpisodeFanoutCoordinator.ChildResult(1L, List.of());
            }, snapshotId -> { });

        assertThat(runs.get()).isZero();
        verify(store, never()).markSucceeded(eq(54L), anyLong(), any(Integer.class), anyLong());
    }

    private WorkflowAgentExecutionPlan plan() {
        return new WorkflowAgentExecutionPlan(new WorkflowAgentRecord(
            1L, "short-drama-episode-summary", "概要", null, "prompt", 2L,
            new BigDecimal("0.2"), 1000, 4, "ENABLED", 3L, null, null,
            LocalDateTime.now(), LocalDateTime.now(), List.of("foundation", "summary"),
            List.of("read_current_episode", "save_episode_summary")), List.of());
    }

    private List<EpisodeFanoutCoordinator.EpisodeUnit> units() {
        return List.of(
            new EpisodeFanoutCoordinator.EpisodeUnit(1L, "e1", "f1", "PENDING"),
            new EpisodeFanoutCoordinator.EpisodeUnit(2L, "e2", "f2", "FAILED"),
            new EpisodeFanoutCoordinator.EpisodeUnit(3L, "e3", "f3", "PENDING"));
    }

    private ScriptAnalysisTaskEntity task() {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(4L); task.setTenantId(7L); task.setProjectId(8L); task.setScriptId(9L); task.setCreatedBy(10L);
        return task;
    }

    private ScriptAnalysisStageEntity stage() {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(5L); stage.setStageCode("EPISODE_SUMMARY"); stage.setAttemptNo(1);
        return stage;
    }

    private AiExecutionContext executionContext() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 700L;
        execution.status = "RUNNING";
        execution.executionVersion = 1;
        return new AiExecutionContext(execution,
            new AiExecutionClaim(700L, 701L, "claim", 1, "ANALYSIS"));
    }
}
