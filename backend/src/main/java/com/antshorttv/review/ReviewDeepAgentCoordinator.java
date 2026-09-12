package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.ScriptReviewAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunRepository;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.run.WorkflowAgentTruncatedOutputException;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReviewDeepAgentCoordinator {
    private final ReviewAgentExecutionPlanFactory plans;
    private final WorkflowAgentRunner runner;
    private final ReviewContentService contentService;
    private final ReviewUnitPlanner planner;
    private final ReviewFanoutRepository fanout;
    private final ReviewTaskMapper tasks;
    private final ReviewScriptVersionMapper versions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final int unitCharacters;
    private final int unitOverlap;
    private final int maxConcurrency;
    private final ReviewPromptCacheContextFactory cacheContexts;
    private final ReviewDimensionRunScheduler dimensionScheduler;
    private final WorkflowAgentRunRepository workflowRuns;

    public ReviewDeepAgentCoordinator(ReviewAgentExecutionPlanFactory plans, WorkflowAgentRunner runner,
        ReviewContentService contentService, ReviewUnitPlanner planner, ReviewFanoutRepository fanout,
        ReviewTaskMapper tasks, ReviewScriptVersionMapper versions,
        WorkflowAgentRunRepository workflowRuns,
        JdbcTemplate jdbc, ObjectMapper json,
        @Value("${review.workflow.deep-unit-characters:24000}") int unitCharacters,
        @Value("${review.workflow.deep-unit-overlap:1200}") int unitOverlap,
        @Value("${review.workflow.deep-max-concurrency:3}") int maxConcurrency) {
        this.plans = plans; this.runner = runner; this.contentService = contentService; this.planner = planner;
        this.fanout = fanout; this.tasks = tasks; this.versions = versions;
        this.workflowRuns = workflowRuns;
        this.jdbc = jdbc; this.json = json;
        this.unitCharacters = unitCharacters; this.unitOverlap = unitOverlap;
        this.maxConcurrency = Math.max(1, maxConcurrency);
        this.cacheContexts = new ReviewPromptCacheContextFactory(json);
        this.dimensionScheduler = new ReviewDimensionRunScheduler();
    }

    public boolean canRecoverCommittedAggregation(ReviewTaskEntity task) {
        return task != null && "COMPLETED".equals(task.getStatus()) && "DEEP".equals(task.getReviewMode())
            && task.getFanoutSnapshotId() != null && task.getWorkflowAgentRunId() != null
            && (task.getAggregationRunId() == null
                || task.getWorkflowAgentRunId().equals(task.getAggregationRunId()));
    }

    public Execution recoverCommittedAggregation(ReviewTaskEntity task) {
        if (!canRecoverCommittedAggregation(task)) {
            throw invalid("审核任务不存在可恢复的聚合提交。");
        }
        Long runId = task.getWorkflowAgentRunId();
        if (!workflowRuns.belongsToTask(runId, task.getTenantId(), task.getId())) {
            throw invalid("聚合运行与审核任务不匹配。");
        }
        String committedOutput = task.getReportMarkdown();
        workflowRuns.reconcileCommitted(runId, committedOutput);
        task.setAggregationRunId(runId);
        task.setUpdatedAt(LocalDateTime.now());
        updateUnlessCanceled(task);
        updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='SUCCEEDED', aggregation_status='SUCCEEDED', aggregation_run_id=?, completed_at=coalesce(completed_at, now()), updated_at=now() where id=? and task_id=?",
            runId, task.getFanoutSnapshotId(), task.getId());
        return new Execution(task.getFanoutSnapshotId(), runId,
            List.copyOf(workflowRuns.modelCalls(runId, task.getTenantId())));
    }

    public Execution execute(ReviewTaskEntity task, AiExecutionContext execution, Long modelId) {
        if (execution == null) throw invalid("AI 调用必须先创建执行和积分预占。");
        requireNotCanceled(task.getId());
        if ("COMPLETED".equals(task.getStatus()) && task.getFanoutSnapshotId() != null
            && task.getAggregationRunId() != null) {
            return new Execution(task.getFanoutSnapshotId(), task.getAggregationRunId(), List.of());
        }
        ReviewScriptVersionEntity version = versions.selectById(task.getScriptVersionId());
        if (version == null) throw invalid("审核版本不存在。");
        List<String> dimensions = list(task.getSelectedDimensionsJson());
        ReviewDimension.parseAll(dimensions);
        Map<String, Object> scopeMap = map(task.getReviewScopeJson());
        ReviewContentService.FrozenReview frozen = contentService.freeze(version.getContent(),
            task.getReviewScopeType(), scopeMap, dimensions);
        try {
            return executeMarkdown(task, execution, modelId, version, dimensions, frozen);
        } catch (RuntimeException failure) {
            reconcileCanceledFanout(task);
            throw failure;
        }
    }

    private Execution executeMarkdown(ReviewTaskEntity task, AiExecutionContext execution, Long modelId,
        ReviewScriptVersionEntity version, List<String> dimensions, ReviewContentService.FrozenReview frozen) {
        WorkflowAgentExecutionPlan childPlan = plans.freeze(dimensions, "MARKDOWN_DEEP_CHILD");
        String skillRevisions = skillRevisions(childPlan);
        ReviewPromptCacheContextFactory.CacheContext cacheContext = cacheContexts.build(
            task.getTenantId(), modelId, childPlan.agent().revision(), skillRevisions, frozen);
        List<ReviewUnitPlanner.Unit> contentUnits = planner.plan(version.getContent(), task.getReviewScopeType(),
            map(task.getReviewScopeJson()), frozen, unitCharacters, unitOverlap);
        String unitSetHash = ReviewContentService.hash(contentUnits.stream()
            .flatMap(unit -> dimensions.stream().map(dimension -> dimension + ":" + unit.unitKey()
                + ":" + unit.fingerprint()))
            .collect(java.util.stream.Collectors.joining("|")));
        int attempt = task.getWorkflowAttemptNo() == null ? 1 : Math.max(1, task.getWorkflowAttemptNo());
        Long snapshotId = fanout.findMatchingSnapshot(task.getId(), frozen.versionHash(),
            frozen.scopeHash(), frozen.dimensionsHash(), unitSetHash);
        if (snapshotId == null) {
            attempt = task.getWorkflowAttemptNo() == null ? 1 : task.getWorkflowAttemptNo() + 1;
            snapshotId = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(task.getTenantId(),
                task.getProjectId(), task.getId(), version.getId(), attempt, ScriptReviewAgentBootstrap.AGENT_CODE,
                childPlan.agent().revision(), skillRevisions, modelId, task.getSelectedDimensionsJson(),
                task.getReviewScopeJson(), frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                unitSetHash, contentUnits.size() * dimensions.size(), maxConcurrency));
            int unitNo = 1;
            for (ReviewUnitPlanner.Unit contentUnit : contentUnits) {
                for (String dimension : dimensions) {
                    fanout.addUnit(new ReviewFanoutRepository.UnitDraft(snapshotId, unitNo++,
                        contentUnit.unitKey() + "-" + ReviewContentService.hash(dimension).substring(0, 8),
                        "DIMENSION_MARKDOWN", dimension,
                        stringify(Map.of("dimension", dimension, "contentUnit", contentUnit.unitKey())),
                        contentUnit.startOffset(), contentUnit.endOffset(), contentUnit.fingerprint()));
                }
            }
        } else {
            attempt = jdbc.queryForObject("select attempt_no from review_fanout_snapshot where id = ?",
                Integer.class, snapshotId);
        }
        task.setFanoutSnapshotId(snapshotId);
        task.setWorkflowAgentCode(ScriptReviewAgentBootstrap.AGENT_CODE);
        task.setWorkflowAgentRevision(childPlan.agent().revision());
        task.setWorkflowPhase("MARKDOWN_DEEP_CHILD");
        task.setWorkflowAttemptNo(attempt);
        task.setCurrentStage("DEEP_UNITS");
        task.setCurrentAction("正在逐维度生成 Markdown 审核片段");
        task.setOverallProgress(20);
        task.setUpdatedAt(LocalDateTime.now());
        updateUnlessCanceled(task);
        updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='RUNNING', updated_at=now() where id=?", snapshotId);

        List<WorkflowAgentModelCall> calls = new ArrayList<>();
        List<ReviewFanoutUnitEntity> units = fanout.orderedUnits(snapshotId);
        int completed = (int) units.stream().filter(unit -> "SUCCEEDED".equals(unit.getStatus())
            && Boolean.TRUE.equals(unit.getReportSaved())).count();
        List<ReviewFanoutUnitEntity> pending = units.stream()
            .filter(unit -> !"SUCCEEDED".equals(unit.getStatus()) || !Boolean.TRUE.equals(unit.getReportSaved()))
            .toList();
        AtomicInteger completedUnits = new AtomicInteger(completed);
        long stableSnapshotId = snapshotId;
        int stableAttempt = attempt;
        dimensionScheduler.execute(pending, maxConcurrency,
            unit -> runMarkdownDimension(task, execution, modelId, version, frozen, stableSnapshotId,
                stableAttempt, childPlan, cacheContext, unit, completedUnits, units.size()))
            .forEach(calls::addAll);
        updateSnapshotUnlessCanceled("update review_fanout_snapshot set completed_units=?, failed_units=0, updated_at=now() where id=?",
            units.size(), snapshotId);

        requireNotCanceled(task.getId());
        List<ReviewFanoutRepository.MarkdownFragment> fragments = fanout.orderedMarkdownFragments(snapshotId);
        if (fragments.size() != units.size() || fragments.stream().anyMatch(
            fragment -> fragment.reportMarkdown() == null || fragment.reportMarkdown().isBlank())) {
            throw invalid("DEEP Markdown 片段未完整生成。");
        }
        WorkflowAgentExecutionPlan aggregationPlan = plans.freeze(dimensions, "MARKDOWN_DEEP_AGGREGATION");
        updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='AGGREGATING', aggregation_status='RUNNING', current_unit_id=null, updated_at=now() where id=?", snapshotId);
        task.setWorkflowPhase("MARKDOWN_DEEP_AGGREGATION");
        task.setCurrentStage("DEEP_AGGREGATION");
        task.setCurrentAction("正在合并 Markdown 片段并生成最终报告");
        task.setOverallProgress(85);
        task.setUpdatedAt(LocalDateTime.now());
        updateUnlessCanceled(task);
        String fragmentText = fragments.stream().map(fragment -> """
            \n--- 单元 %d｜维度：%s｜标识：%s ---
            %s
            """.formatted(fragment.unitNo(), fragment.dimension(), fragment.unitKey(),
                fragment.reportMarkdown())).collect(java.util.stream.Collectors.joining("\n"));
        try {
            requireNotCanceled(task.getId());
            WorkflowAgentRunResult aggregation = runner.runFormal(aggregationPlan,
                input(task, execution, modelId, new ReviewToolScope(task.getProjectId(), version.getId(),
                    snapshotId, null, attempt, "MARKDOWN_DEEP_AGGREGATION", dimensions),
                    "将以下按冻结顺序提供的 Markdown 片段合成一份完整 Markdown 审核报告。"
                        + "按维度、根因和稳定位置合并重复发现，保留全部不同引用；不同根因保持分开。"
                        + "直接返回报告，不调用工具。\n" + fragmentText));
            calls.addAll(aggregation.modelCalls());
            requireNotCanceled(task.getId());
            if (aggregation.output() == null || aggregation.output().isBlank()) {
                throw invalid("DEEP 聚合返回的 Markdown 报告为空。");
            }
            LocalDateTime now = LocalDateTime.now();
            task.setReportMarkdown(aggregation.output());
            task.setWorkflowAgentRunId(aggregation.runId());
            task.setAggregationRunId(aggregation.runId());
            task.setStatus("COMPLETED");
            task.setCurrentStage("COMPLETED");
            task.setOverallProgress(100);
            task.setCurrentAction("Markdown 审核报告已生成");
            task.setErrorCode(null);
            task.setErrorMessage(null);
            task.setCompletedAt(now);
            task.setUpdatedAt(now);
            updateUnlessCanceled(task);
            updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='SUCCEEDED', aggregation_status='SUCCEEDED', aggregation_run_id=?, completed_at=now(), updated_at=now() where id=?",
                aggregation.runId(), snapshotId);
            return new Execution(snapshotId, aggregation.runId(), List.copyOf(calls));
        } catch (RuntimeException failure) {
            if (failure instanceof WorkflowAgentTruncatedOutputException truncated) {
                task.setReportMarkdown(truncated.partialContent());
                task.setWorkflowAgentRunId(truncated.runId());
                task.setStatus("FAILED");
                task.setErrorCode("OUTPUT_TRUNCATED");
                task.setErrorMessage(truncated.getMessage());
                task.setUpdatedAt(LocalDateTime.now());
                updateUnlessCanceled(task);
            }
            updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='FAILED', aggregation_status='FAILED', updated_at=now() where id=?", snapshotId);
            throw failure;
        }
    }

    private List<WorkflowAgentModelCall> runMarkdownDimension(ReviewTaskEntity task,
        AiExecutionContext execution, Long modelId, ReviewScriptVersionEntity version,
        ReviewContentService.FrozenReview frozen, Long snapshotId, int attempt,
        WorkflowAgentExecutionPlan childPlan, ReviewPromptCacheContextFactory.CacheContext cacheContext,
        ReviewFanoutUnitEntity unit, AtomicInteger completedUnits, int totalUnits) {
        requireNotCanceled(task.getId());
        updateUnitUnlessCanceled("update review_fanout_unit set status='RUNNING', attempt_no=attempt_no+1, error_code=null, error_message=null, started_at=now(), updated_at=now() where id=?", unit.getId());
        try {
            requireNotCanceled(task.getId());
            WorkflowAgentRunResult child = runner.runFormal(childPlan, input(task, execution, modelId,
                new ReviewToolScope(task.getProjectId(), version.getId(), snapshotId, unit.getId(), attempt,
                    "MARKDOWN_DEEP_CHILD", List.of(unit.getDimension())), cacheContext.commonPrefix(),
                cacheContext.cacheKey(), "仅审核维度【%s】。完整读取冻结范围，直接返回该维度的非空 Markdown 片段；"
                    .formatted(unit.getDimension()) + "保留精确引文，不生成候选 JSON，不调用保存工具。"));
            requireNotCanceled(task.getId());
            if (child.output() == null || child.output().isBlank()) {
                throw invalid("DEEP 子 Agent 返回的 Markdown 片段为空。");
            }
            String fingerprint = unit.getContentFingerprint() == null
                ? ReviewContentService.hash(frozen.content()) : unit.getContentFingerprint();
            fanout.replaceMarkdownFragment(new ReviewFanoutRepository.MarkdownFragmentDraft(snapshotId,
                unit.getId(), child.runId(), attempt, frozen.versionHash(), frozen.scopeHash(),
                frozen.dimensionsHash(), fingerprint, child.output(), ReviewContentService.hash(child.output())));
            synchronized (completedUnits) {
                int done = completedUnits.incrementAndGet();
                updateSnapshotUnlessCanceled("update review_fanout_snapshot set completed_units=?, current_unit_id=?, updated_at=now() where id=?",
                    done, unit.getId(), snapshotId);
                task.setOverallProgress(20 + (int) Math.floor(60.0 * done / totalUnits));
                task.setCurrentAction("深度审核单元 %d/%d".formatted(done, totalUnits));
                task.setUpdatedAt(LocalDateTime.now());
                updateUnlessCanceled(task);
            }
            return child.modelCalls();
        } catch (RuntimeException failure) {
            if (failure instanceof WorkflowAgentTruncatedOutputException truncated
                && truncated.partialContent() != null && !truncated.partialContent().isBlank()) {
                requireNotCanceled(task.getId());
                String fingerprint = unit.getContentFingerprint() == null
                    ? ReviewContentService.hash(frozen.content()) : unit.getContentFingerprint();
                fanout.retainPartialMarkdownFragment(new ReviewFanoutRepository.MarkdownFragmentDraft(snapshotId,
                    unit.getId(), truncated.runId(), attempt, frozen.versionHash(), frozen.scopeHash(),
                    frozen.dimensionsHash(), fingerprint, truncated.partialContent(),
                    ReviewContentService.hash(truncated.partialContent())));
            }
            updateUnitUnlessCanceled("update review_fanout_unit set status='FAILED', error_code=?, error_message=?, updated_at=now() where id=?",
                failure.getClass().getSimpleName(), trim(failure.getMessage()), unit.getId());
            int failedNow = jdbc.queryForObject(
                "select count(*) from review_fanout_unit where snapshot_id=? and status='FAILED'",
                Integer.class, snapshotId);
            updateSnapshotUnlessCanceled("update review_fanout_snapshot set status='PARTIAL_FAILED', failed_units=?, updated_at=now() where id=?",
                failedNow, snapshotId);
            throw failure;
        }
    }

    private WorkflowAgentRunInput input(ReviewTaskEntity task, AiExecutionContext execution, Long modelId,
        ReviewToolScope scope, String prompt) {
        return input(task, execution, modelId, scope, null, null, prompt);
    }

    private WorkflowAgentRunInput input(ReviewTaskEntity task, AiExecutionContext execution, Long modelId,
        ReviewToolScope scope, String stableContext, String promptCacheKey, String prompt) {
        return new WorkflowAgentRunInput(ScriptReviewAgentBootstrap.AGENT_CODE, prompt, task.getTenantId(),
            task.getProjectId(), null, null, task.getId(), null, task.getCreatedBy(), execution.task().id,
            execution.claim().attemptId(), execution.task().executionVersion, modelId, scope,
            stableContext, promptCacheKey, Map.of());
    }

    private void requireNotCanceled(Long taskId) {
        ReviewTaskEntity current = tasks.selectById(taskId);
        if (current == null || "CANCELED".equals(current.getStatus())) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消。");
        }
    }

    private void updateUnlessCanceled(ReviewTaskEntity task) {
        int updated = tasks.update(task, new LambdaUpdateWrapper<ReviewTaskEntity>()
            .eq(ReviewTaskEntity::getId, task.getId())
            .ne(ReviewTaskEntity::getStatus, "CANCELED"));
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消。");
        }
    }

    private void updateSnapshotUnlessCanceled(String sql, Object... arguments) {
        int updated = jdbc.update(sql + """
             and status<>'CANCELED'
             and exists (select 1 from review_task task
                         where task.id=review_fanout_snapshot.task_id and task.status<>'CANCELED')
            """, arguments);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消或快照已失效。");
        }
    }

    private void updateUnitUnlessCanceled(String sql, Object... arguments) {
        int updated = jdbc.update(sql + """
             and status<>'CANCELED'
             and exists (select 1 from review_fanout_snapshot snapshot
                         join review_task task on task.id=snapshot.task_id
                         where snapshot.id=review_fanout_unit.snapshot_id
                           and snapshot.status<>'CANCELED' and task.status<>'CANCELED')
            """, arguments);
        if (updated == 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消或单元已失效。");
        }
    }

    private void reconcileCanceledFanout(ReviewTaskEntity task) {
        if (task.getFanoutSnapshotId() == null) return;
        jdbc.update("""
            update review_fanout_unit set status='CANCELED', updated_at=now()
             where snapshot_id=? and status in ('PENDING','RUNNING')
               and exists (select 1 from review_fanout_snapshot snapshot
                           join review_task task on task.id=snapshot.task_id
                           where snapshot.id=review_fanout_unit.snapshot_id
                             and task.id=? and task.status='CANCELED')
            """, task.getFanoutSnapshotId(), task.getId());
        jdbc.update("""
            update review_fanout_snapshot
               set status='CANCELED', aggregation_status='CANCELED',
                   canceled_at=coalesce(canceled_at,now()), updated_at=now()
             where id=? and task_id=?
               and exists (select 1 from review_task task
                           where task.id=review_fanout_snapshot.task_id and task.status='CANCELED')
            """, task.getFanoutSnapshotId(), task.getId());
    }

    private List<String> list(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw invalid("审核维度配置无效。"); }
    }

    private Map<String, Object> map(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (Exception exception) { throw invalid("审核范围配置无效。"); }
    }

    private String skillRevisions(WorkflowAgentExecutionPlan plan) {
        return stringify(plan.skillSnapshots().stream().map(skill -> Map.of(
            "code", skill.code(), "revision", skill.revision())).toList());
    }

    private String stringify(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw invalid("审核快照无法序列化。"); }
    }

    private String trim(String value) {
        if (value == null) return null;
        return value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record Execution(Long snapshotId, Long aggregationRunId, List<WorkflowAgentModelCall> modelCalls) {}
}
