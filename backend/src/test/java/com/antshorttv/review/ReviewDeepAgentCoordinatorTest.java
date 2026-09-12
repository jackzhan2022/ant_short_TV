package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
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
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.run.WorkflowAgentTruncatedOutputException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ReviewDeepAgentCoordinatorTest {
    private ReviewAgentExecutionPlanFactory plans;
    private WorkflowAgentRunner runner;
    private ReviewContentService content;
    private ReviewUnitPlanner planner;
    private ReviewFanoutRepository fanout;
    private ReviewTaskMapper tasks;
    private ReviewScriptVersionMapper versions;
    private WorkflowAgentRunRepository workflowRuns;
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
        when(tasks.update(any(), any())).thenReturn(1);
        versions = mock(ReviewScriptVersionMapper.class);
        workflowRuns = mock(WorkflowAgentRunRepository.class);
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        coordinator = new ReviewDeepAgentCoordinator(plans, runner, content, planner, fanout,
            tasks, versions, workflowRuns, jdbc, new ObjectMapper(), 100, 10, 2);
        childPlan = plan(3L);
        aggregationPlan = plan(5L);
        when(plans.freeze(List.of("台词合理性", "时间线连续性"), "MARKDOWN_DEEP_CHILD")).thenReturn(childPlan);
        when(plans.freeze(List.of("台词合理性", "时间线连续性"), "MARKDOWN_DEEP_AGGREGATION")).thenReturn(aggregationPlan);
        task = task("RUNNING");
        when(tasks.selectById(7L)).thenReturn(task);
        when(fanout.orderedMarkdownFragments(50L)).thenAnswer(invocation -> fanout.orderedUnits(50L).stream()
            .map(unit -> new ReviewFanoutRepository.MarkdownFragment(unit.getId(), unit.getUnitNo(),
                unit.getUnitKey(), unit.getDimension(), "## 单元 " + unit.getUnitNo())).toList());
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
    void markdownDeepStoresFragmentsSkipsSemanticAndPersistsFinalReport() {
        when(plans.freeze(any(), eq("DEEP_CHILD"))).thenAnswer(invocation -> {
            assertThat((String) invocation.getArgument(1)).startsWith("MARKDOWN_");
            return null;
        });
        coordinator = new ReviewDeepAgentCoordinator(plans, runner, content, planner, fanout,
            tasks, versions, workflowRuns, jdbc, new ObjectMapper(), 100, 10, 2);
        WorkflowAgentExecutionPlan markdownChild = plan(6L);
        WorkflowAgentExecutionPlan markdownAggregation = plan(7L);
        when(plans.freeze(List.of("台词合理性", "时间线连续性"), "MARKDOWN_DEEP_CHILD"))
            .thenReturn(markdownChild);
        when(plans.freeze(List.of("台词合理性", "时间线连续性"), "MARKDOWN_DEEP_AGGREGATION"))
            .thenReturn(markdownAggregation);
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(null);
        when(fanout.openSnapshot(any())).thenReturn(50L);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(
            unit(11L, 1, "PENDING", false), unit(12L, 2, "PENDING", false)));
        when(tasks.selectById(7L)).thenReturn(task);
        when(runner.runFormal(eq(markdownChild), any())).thenReturn(
            new WorkflowAgentRunResult(101L, "## 台词\n第1集：引用 A"),
            new WorkflowAgentRunResult(102L, "## 时间线\n第2集：引用 B"));
        doReturn(List.of(
            new ReviewFanoutRepository.MarkdownFragment(11L, 1, "unit-1", "台词合理性", "## 台词\n第1集：引用 A"),
            new ReviewFanoutRepository.MarkdownFragment(12L, 2, "unit-2", "时间线连续性", "## 时间线\n第2集：引用 B")))
            .when(fanout).orderedMarkdownFragments(50L);
        String finalReport = "# 审核报告\n\n两个发现。";
        when(runner.runFormal(eq(markdownAggregation), any()))
            .thenReturn(new WorkflowAgentRunResult(200L, finalReport));

        ReviewDeepAgentCoordinator.Execution result = coordinator.execute(task, execution(), 9L);

        assertThat(result.aggregationRunId()).isEqualTo(200L);
        assertThat(task.getReportMarkdown()).isEqualTo(finalReport);
        assertThat(task.getStatus()).isEqualTo("COMPLETED");
        verify(fanout, times(2)).replaceMarkdownFragment(any());
        verify(plans, never()).freeze(any(), eq("DEEP_SEMANTIC"));
        ArgumentCaptor<WorkflowAgentRunInput> inputs = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner, times(3)).runFormal(any(), inputs.capture());
        assertThat(inputs.getAllValues()).extracting(input -> input.reviewScope().phase())
            .containsExactly("MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_AGGREGATION");
        assertThat(inputs.getAllValues().get(2).input()).contains("引用 A", "引用 B", "根因", "稳定位置");
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
        when(tasks.selectById(7L)).thenReturn(task);

        ReviewDeepAgentCoordinator.Execution result = coordinator.execute(task, execution(), 9L);

        assertThat(result.snapshotId()).isEqualTo(50L);
        assertThat(result.aggregationRunId()).isEqualTo(200L);
        ArgumentCaptor<ReviewFanoutRepository.SnapshotDraft> snapshot =
            ArgumentCaptor.forClass(ReviewFanoutRepository.SnapshotDraft.class);
        verify(fanout).openSnapshot(snapshot.capture());
        assertThat(snapshot.getValue().maxConcurrency()).isEqualTo(2);
        verify(fanout, times(4)).addUnit(any());
        ArgumentCaptor<WorkflowAgentRunInput> inputs = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner, times(3)).runFormal(any(), inputs.capture());
        assertThat(inputs.getAllValues()).extracting(input -> input.reviewScope().phase())
            .containsExactly("MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_CHILD", "MARKDOWN_DEEP_AGGREGATION");
        assertThat(inputs.getAllValues().subList(0, 2)).extracting(WorkflowAgentRunInput::promptCacheKey)
            .doesNotContainNull().allMatch(inputs.getAllValues().get(0).promptCacheKey()::equals);
        assertThat(inputs.getAllValues().subList(0, 2)).extracting(WorkflowAgentRunInput::stableContext)
            .containsOnly(inputs.getAllValues().get(0).stableContext());
        assertThat(inputs.getAllValues().subList(0, 2))
            .extracting(input -> input.reviewScope().selectedDimensions())
            .containsExactly(List.of("台词合理性"), List.of("时间线连续性"));
        verify(tasks, atLeastOnce()).update(any(ReviewTaskEntity.class), any());
    }

    @Test
    void warmsFirstDimensionBeforeRunningRemainingDimensionsConcurrently() {
        List<String> dimensions = List.of("台词合理性", "时间线连续性", "道具连续性");
        task.setSelectedDimensionsJson("[\"台词合理性\",\"时间线连续性\",\"道具连续性\"]");
        when(plans.freeze(dimensions, "MARKDOWN_DEEP_CHILD")).thenReturn(childPlan);
        when(plans.freeze(dimensions, "MARKDOWN_DEEP_AGGREGATION")).thenReturn(aggregationPlan);
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(null);
        when(fanout.openSnapshot(any())).thenReturn(50L);
        ReviewFanoutUnitEntity first = unit(11L, 1, "PENDING", false);
        ReviewFanoutUnitEntity second = unit(12L, 2, "PENDING", false);
        ReviewFanoutUnitEntity third = unit(13L, 3, "PENDING", false);
        third.setDimension("道具连续性");
        List<ReviewFanoutUnitEntity> units = List.of(first, second, third);
        when(fanout.orderedUnits(50L)).thenReturn(units);
        AtomicBoolean warmCompleted = new AtomicBoolean();
        AtomicBoolean parallelStartedBeforeWarm = new AtomicBoolean();
        AtomicBoolean everyParallelRunSawPeer = new AtomicBoolean(true);
        CountDownLatch parallelRuns = new CountDownLatch(2);
        when(runner.runFormal(eq(childPlan), any())).thenAnswer(invocation -> {
            WorkflowAgentRunInput input = invocation.getArgument(1);
            long unitId = input.reviewScope().unitId();
            ReviewFanoutUnitEntity current = units.stream()
                .filter(unit -> unit.getId() == unitId).findFirst().orElseThrow();
            if (unitId == 11L) {
                warmCompleted.set(true);
            } else {
                if (!warmCompleted.get()) parallelStartedBeforeWarm.set(true);
                parallelRuns.countDown();
                if (!parallelRuns.await(500, TimeUnit.MILLISECONDS)) {
                    everyParallelRunSawPeer.set(false);
                }
            }
            current.setStatus("SUCCEEDED");
            current.setReportSaved(true);
            return new WorkflowAgentRunResult(100L + unitId, "saved");
        });
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "saved"));
        when(tasks.selectById(7L)).thenReturn(task);

        coordinator.execute(task, execution(), 9L);

        assertThat(parallelStartedBeforeWarm).isFalse();
        assertThat(everyParallelRunSawPeer).isTrue();
    }

    @Test
    void restoresProgressAndRetriesOnlyFailedOrMissingFragmentUnits() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(3);
        ReviewFanoutUnitEntity done = unit(11L, 1, "SUCCEEDED", true);
        ReviewFanoutUnitEntity failed = unit(12L, 2, "FAILED", false);
        ReviewFanoutUnitEntity missingFragment = unit(13L, 3, "SUCCEEDED", false);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(done, failed, missingFragment),
            List.of(done, unit(12L, 2, "SUCCEEDED", true), missingFragment),
            List.of(done, failed, unit(13L, 3, "SUCCEEDED", true)));
        when(runner.runFormal(eq(childPlan), any())).thenReturn(
            new WorkflowAgentRunResult(102L, "saved"), new WorkflowAgentRunResult(103L, "saved"));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "saved"));
        when(tasks.selectById(7L)).thenReturn(task);

        coordinator.execute(task, execution(), 9L);

        ArgumentCaptor<WorkflowAgentRunInput> inputs = ArgumentCaptor.forClass(WorkflowAgentRunInput.class);
        verify(runner, times(3)).runFormal(any(), inputs.capture());
        assertThat(inputs.getAllValues().stream().filter(input ->
            "MARKDOWN_DEEP_CHILD".equals(input.reviewScope().phase())).map(input -> input.reviewScope().unitId()))
            .containsExactly(12L, 13L);
        verify(fanout, never()).addUnit(any());
    }

    @Test
    void aggregationFailureKeepsFragmentsAndNeverReportsSuccess() {
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
    void oneDimensionFailurePreventsAggregation() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        ReviewFanoutUnitEntity first = unit(11L, 1, "PENDING", false);
        ReviewFanoutUnitEntity second = unit(12L, 2, "PENDING", false);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(first, second),
            List.of(unit(11L, 1, "SUCCEEDED", true), second));
        when(runner.runFormal(eq(childPlan), any()))
            .thenReturn(new WorkflowAgentRunResult(101L, "saved"))
            .thenThrow(new IllegalStateException("timeline failed"));
        when(tasks.selectById(7L)).thenReturn(task("RUNNING"));
        when(jdbc.queryForObject(startsWith("select count(*) from review_fanout_unit"),
            eq(Integer.class), eq(50L))).thenReturn(1);

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(IllegalStateException.class).hasMessage("timeline failed");

        verify(runner, never()).runFormal(eq(aggregationPlan), any());
        verify(jdbc).update(startsWith("update review_fanout_snapshot set status='PARTIAL_FAILED'"),
            eq(1), eq(50L));
    }

    @Test
    void truncatedAggregationRetainsPartialReportWithoutCompletingTask() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(unit(11L, 1, "SUCCEEDED", true)));
        when(runner.runFormal(eq(aggregationPlan), any())).thenThrow(
            new WorkflowAgentTruncatedOutputException(200L, "# 部分报告", List.of()));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(WorkflowAgentTruncatedOutputException.class);

        assertThat(task.getReportMarkdown()).isEqualTo("# 部分报告");
        assertThat(task.getErrorCode()).isEqualTo("OUTPUT_TRUNCATED");
        assertThat(task.getStatus()).isEqualTo("FAILED");
        verify(runner, never()).runFormal(eq(childPlan), any());
    }

    @Test
    void blankAggregationNeverCompletesTask() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(unit(11L, 1, "SUCCEEDED", true)));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, " \n"));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("为空");

        assertThat(task.getStatus()).isNotEqualTo("COMPLETED");
        assertThat(task.getReportMarkdown()).isNull();
    }

    @Test
    void truncatedChildRetainsPartialFragmentAndPreventsAggregation() {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(50L))).thenReturn(1);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(unit(11L, 1, "PENDING", false)));
        when(runner.runFormal(eq(childPlan), any())).thenThrow(
            new WorkflowAgentTruncatedOutputException(101L, "## 部分片段", List.of()));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(WorkflowAgentTruncatedOutputException.class);

        ArgumentCaptor<ReviewFanoutRepository.MarkdownFragmentDraft> partial =
            ArgumentCaptor.forClass(ReviewFanoutRepository.MarkdownFragmentDraft.class);
        verify(fanout).retainPartialMarkdownFragment(partial.capture());
        assertThat(partial.getValue().reportMarkdown()).isEqualTo("## 部分片段");
        verify(fanout, never()).replaceMarkdownFragment(any());
        verify(runner, never()).runFormal(eq(aggregationPlan), any());
    }

    @Test
    void lateChildFailureCannotOverwriteCanceledSnapshotOrUnit() {
        JdbcTemplate database = cancellationDatabase();
        coordinator = new ReviewDeepAgentCoordinator(plans, runner, content, planner, fanout,
            tasks, versions, workflowRuns, database, new ObjectMapper(), 100, 10, 2);
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(fanout.orderedUnits(50L)).thenReturn(List.of(unit(11L, 1, "PENDING", false)));
        when(tasks.selectById(7L)).thenAnswer(invocation -> task(database.queryForObject(
            "select status from review_task where id=7", String.class)));
        when(runner.runFormal(eq(childPlan), any())).thenAnswer(invocation -> {
            database.update("update review_task set status='CANCELED' where id=7");
            database.update("update review_fanout_snapshot set status='CANCELED', aggregation_status='CANCELED' where id=50");
            database.update("update review_fanout_unit set status='CANCELED' where id=11");
            throw new IllegalStateException("provider failed after cancellation");
        });

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L)).isInstanceOf(RuntimeException.class);

        assertThat(database.queryForObject("select status from review_task where id=7", String.class)).isEqualTo("CANCELED");
        assertThat(database.queryForObject("select status from review_fanout_snapshot where id=50", String.class)).isEqualTo("CANCELED");
        assertThat(database.queryForObject("select status from review_fanout_unit where id=11", String.class)).isEqualTo("CANCELED");
    }

    @Test
    void cancellationBeforeSnapshotLinkCancelsTheUnlinkedPendingFanout() {
        JdbcTemplate database = cancellationDatabase();
        coordinator = new ReviewDeepAgentCoordinator(plans, runner, content, planner, fanout,
            tasks, versions, workflowRuns, database, new ObjectMapper(), 100, 10, 2);
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(null);
        when(fanout.openSnapshot(any())).thenReturn(50L);
        when(tasks.update(any(), any())).thenAnswer(invocation -> {
            database.update("update review_task set status='CANCELED' where id=7");
            return 0;
        });

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("取消");

        assertThat(database.queryForObject("select status from review_fanout_snapshot where id=50", String.class)).isEqualTo("CANCELED");
        assertThat(database.queryForObject("select status from review_fanout_unit where id=11", String.class)).isEqualTo("CANCELED");
        verify(runner, never()).runFormal(any(), any());
    }

    private JdbcTemplate cancellationDatabase() {
        JdbcTemplate database = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:h2:mem:review_cancel_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        database.execute("create table review_task (id bigint primary key, status varchar(32))");
        database.execute("create table review_fanout_snapshot (id bigint primary key, task_id bigint, status varchar(32), aggregation_status varchar(32), attempt_no int, completed_units int, failed_units int, current_unit_id bigint, updated_at timestamp, canceled_at timestamp)");
        database.execute("create table review_fanout_unit (id bigint primary key, snapshot_id bigint, status varchar(32), attempt_no int, error_code varchar(100), error_message varchar(1000), started_at timestamp, updated_at timestamp)");
        database.update("insert into review_task (id,status) values (7,'RUNNING')");
        database.update("insert into review_fanout_snapshot (id,task_id,status,attempt_no) values (50,7,'PENDING',1)");
        database.update("insert into review_fanout_unit (id,snapshot_id,status,attempt_no) values (11,50,'PENDING',0)");
        return database;
    }

    @Test
    void cancellationBetweenTaskReadAndInitialProgressWritePreventsAllCalls() {
        AtomicReference<String> persistedStatus = cancelOnTaskWrite(1);
        existingSnapshot(List.of(unit(11L, 1, "PENDING", false)));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("取消");

        assertThat(persistedStatus.get()).isEqualTo("CANCELED");
        verify(runner, never()).runFormal(any(), any());
    }

    @Test
    void cancellationBetweenChildReadAndProgressWritePreventsRemainingCalls() {
        AtomicReference<String> persistedStatus = cancelOnTaskWrite(2);
        existingSnapshot(List.of(unit(11L, 1, "PENDING", false), unit(12L, 2, "PENDING", false)));
        when(runner.runFormal(eq(childPlan), any())).thenReturn(new WorkflowAgentRunResult(101L, "## 发现"));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "# 报告"));
        when(jdbc.queryForObject(startsWith("select count(*)"), eq(Integer.class), eq(50L))).thenReturn(0);

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("取消");

        assertThat(persistedStatus.get()).isEqualTo("CANCELED");
        verify(runner, times(1)).runFormal(eq(childPlan), any());
        verify(runner, never()).runFormal(eq(aggregationPlan), any());
    }

    @Test
    void cancellationBetweenAggregationReadAndProgressWritePreventsAggregationCall() {
        AtomicReference<String> persistedStatus = cancelOnTaskWrite(2);
        existingSnapshot(List.of(unit(11L, 1, "SUCCEEDED", true)));
        when(runner.runFormal(eq(aggregationPlan), any())).thenReturn(new WorkflowAgentRunResult(200L, "# 报告"));

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("取消");

        assertThat(persistedStatus.get()).isEqualTo("CANCELED");
        verify(runner, never()).runFormal(any(), any());
    }

    @Test
    void canceledUnitClaimPreventsPaidChildCall() {
        existingSnapshot(List.of(unit(11L, 1, "PENDING", false)));
        when(jdbc.update(startsWith("update review_fanout_unit set status='RUNNING'"), eq(11L)))
            .thenReturn(0);

        assertThatThrownBy(() -> coordinator.execute(task, execution(), 9L))
            .isInstanceOf(BusinessException.class).hasMessageContaining("取消");

        verify(runner, never()).runFormal(any(), any());
    }

    private void existingSnapshot(List<ReviewFanoutUnitEntity> units) {
        when(fanout.findMatchingSnapshot(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(jdbc.queryForObject(startsWith("select attempt_no"), eq(Integer.class), eq(50L))).thenReturn(2);
        when(fanout.orderedUnits(50L)).thenReturn(units);
    }

    private AtomicReference<String> cancelOnTaskWrite(int cancelWrite) {
        AtomicReference<String> persistedStatus = new AtomicReference<>("RUNNING");
        AtomicInteger writes = new AtomicInteger();
        when(tasks.selectById(7L)).thenAnswer(invocation -> task(persistedStatus.get()));
        when(tasks.updateById(any(ReviewTaskEntity.class))).thenAnswer(invocation -> {
            if (writes.incrementAndGet() == cancelWrite) persistedStatus.set("CANCELED");
            persistedStatus.set(((ReviewTaskEntity) invocation.getArgument(0)).getStatus());
            return 1;
        });
        when(tasks.update(any(), any())).thenAnswer(invocation -> {
            if (writes.incrementAndGet() == cancelWrite) persistedStatus.set("CANCELED");
            if ("CANCELED".equals(persistedStatus.get())) return 0;
            persistedStatus.set(((ReviewTaskEntity) invocation.getArgument(0)).getStatus());
            return 1;
        });
        return persistedStatus;
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

    @Test
    void completedAttemptReturnsCommittedAggregationWithoutStartingAnotherRun() {
        task.setStatus("COMPLETED");
        task.setFanoutSnapshotId(50L);
        task.setAggregationRunId(200L);

        ReviewDeepAgentCoordinator.Execution result = coordinator.execute(task, execution(), 9L);

        assertThat(result.snapshotId()).isEqualTo(50L);
        assertThat(result.aggregationRunId()).isEqualTo(200L);
        verify(runner, never()).runFormal(any(), any());
        verify(fanout, never()).openSnapshot(any());
    }

    @Test
    void committedMarkdownRecoveryReconcilesTheStoredReport() {
        task.setStatus("COMPLETED");
        task.setReportMarkdown("# 已提交报告\n\n原始内容");
        task.setFanoutSnapshotId(50L);
        task.setWorkflowAgentRunId(200L);
        when(workflowRuns.belongsToTask(200L, 2L, 7L)).thenReturn(true);
        when(workflowRuns.modelCalls(200L, 2L)).thenReturn(List.of());

        ReviewDeepAgentCoordinator.Execution result = coordinator.recoverCommittedAggregation(task);

        assertThat(result.aggregationRunId()).isEqualTo(200L);
        verify(workflowRuns).reconcileCommitted(200L, "# 已提交报告\n\n原始内容");
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
        value.setReviewMode("DEEP"); value.setSelectedDimensionsJson("[\"台词合理性\",\"时间线连续性\"]");
        value.setReviewScopeType("ALL"); value.setReviewScopeJson("{}"); value.setRoundNo(1);
        value.setCreatedBy(3L); value.setStatus(status); value.setWorkflowAttemptNo(1);
        return value;
    }

    private ReviewFanoutUnitEntity unit(long id, int number, String status, boolean reportSaved) {
        ReviewFanoutUnitEntity unit = new ReviewFanoutUnitEntity();
        unit.setId(id); unit.setSnapshotId(50L); unit.setUnitNo(number);
        unit.setUnitKey("unit-" + number); unit.setStatus(status); unit.setReportSaved(reportSaved);
        unit.setStageType("DIMENSION_MARKDOWN");
        unit.setDimension(number % 2 == 1 ? "台词合理性" : "时间线连续性");
        return unit;
    }

    private AiExecutionContext execution() {
        AiExecutionTaskEntity execution = new AiExecutionTaskEntity();
        execution.id = 10L; execution.executionVersion = 2;
        return new AiExecutionContext(execution, new AiExecutionClaim(10L, 11L, "claim", 2, "AI_REVIEW"));
    }
}
