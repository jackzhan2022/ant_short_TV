package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.ScriptReviewAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunInput;
import com.antshorttv.workflowagent.run.WorkflowAgentRunResult;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final boolean enabled;
    private final int unitCharacters;
    private final int unitOverlap;
    private final int maxConcurrency;

    public ReviewDeepAgentCoordinator(ReviewAgentExecutionPlanFactory plans, WorkflowAgentRunner runner,
        ReviewContentService contentService, ReviewUnitPlanner planner, ReviewFanoutRepository fanout,
        ReviewTaskMapper tasks, ReviewScriptVersionMapper versions, JdbcTemplate jdbc, ObjectMapper json,
        @Value("${ai.workflow-agent.review-deep-enabled:false}") boolean enabled,
        @Value("${review.workflow.deep-unit-characters:24000}") int unitCharacters,
        @Value("${review.workflow.deep-unit-overlap:1200}") int unitOverlap,
        @Value("${review.workflow.deep-max-concurrency:3}") int maxConcurrency) {
        this.plans = plans; this.runner = runner; this.contentService = contentService; this.planner = planner;
        this.fanout = fanout; this.tasks = tasks; this.versions = versions; this.jdbc = jdbc; this.json = json;
        this.enabled = enabled; this.unitCharacters = unitCharacters; this.unitOverlap = unitOverlap;
        this.maxConcurrency = Math.max(1, maxConcurrency);
    }

    public boolean enabled() { return enabled; }

    public Execution execute(ReviewTaskEntity task, AiExecutionContext execution, Long modelId) {
        if (execution == null) throw invalid("AI 调用必须先创建执行和积分预占。");
        ReviewScriptVersionEntity version = versions.selectById(task.getScriptVersionId());
        if (version == null) throw invalid("审核版本不存在。");
        List<String> dimensions = list(task.getSelectedDimensionsJson());
        Map<String, Object> scopeMap = map(task.getReviewScopeJson());
        ReviewContentService.FrozenReview frozen = contentService.freeze(version.getContent(),
            task.getReviewScopeType(), scopeMap, dimensions);
        List<ReviewUnitPlanner.Unit> planned = planner.plan(version.getContent(), task.getReviewScopeType(),
            scopeMap, frozen, unitCharacters, unitOverlap);
        WorkflowAgentExecutionPlan childPlan = plans.freeze(dimensions, "DEEP_CHILD");
        String unitSetHash = ReviewContentService.hash(planned.stream()
            .map(unit -> unit.unitKey() + ":" + unit.fingerprint()).collect(java.util.stream.Collectors.joining("|")));
        int attempt = task.getWorkflowAttemptNo() == null ? 1 : Math.max(1, task.getWorkflowAttemptNo());
        Long snapshotId = fanout.findMatchingSnapshot(task.getId(), frozen.versionHash(),
            frozen.scopeHash(), frozen.dimensionsHash(), unitSetHash);
        if (snapshotId == null) {
            attempt = task.getWorkflowAttemptNo() == null ? 1 : task.getWorkflowAttemptNo() + 1;
            snapshotId = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(task.getTenantId(),
                task.getProjectId(), task.getId(), version.getId(), attempt, ScriptReviewAgentBootstrap.AGENT_CODE,
                childPlan.agent().revision(), skillRevisions(childPlan), modelId, task.getSelectedDimensionsJson(),
                task.getReviewScopeJson(), frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                unitSetHash, planned.size(), maxConcurrency));
            for (ReviewUnitPlanner.Unit unit : planned) fanout.addUnit(new ReviewFanoutRepository.UnitDraft(
                snapshotId, unit.unitNo(), unit.unitKey(), stringify(Map.of("unitKey", unit.unitKey())),
                unit.startOffset(), unit.endOffset(), unit.fingerprint()));
        } else {
            attempt = jdbc.queryForObject("select attempt_no from review_fanout_snapshot where id = ?", Integer.class, snapshotId);
        }
        task.setFanoutSnapshotId(snapshotId); task.setWorkflowAgentCode(ScriptReviewAgentBootstrap.AGENT_CODE);
        task.setWorkflowAgentRevision(childPlan.agent().revision()); task.setWorkflowPhase("DEEP_CHILD");
        task.setWorkflowAttemptNo(attempt); task.setCurrentStage("DEEP_UNITS");
        task.setCurrentAction("正在逐单元深度审核"); task.setOverallProgress(20); task.setUpdatedAt(LocalDateTime.now());
        tasks.updateById(task);
        jdbc.update("update review_fanout_snapshot set status = 'RUNNING', updated_at = now() where id = ?", snapshotId);

        List<WorkflowAgentModelCall> calls = new ArrayList<>();
        List<ReviewFanoutUnitEntity> units = fanout.orderedUnits(snapshotId);
        int completed = (int) units.stream().filter(unit -> "SUCCEEDED".equals(unit.getStatus())
            && Boolean.TRUE.equals(unit.getCandidateSaved())).count();
        int failed = (int) units.stream().filter(unit -> "FAILED".equals(unit.getStatus())).count();
        jdbc.update("update review_fanout_snapshot set completed_units=?, failed_units=?, updated_at=now() where id=?",
            completed, failed, snapshotId);
        for (ReviewFanoutUnitEntity unit : units) {
            if ("SUCCEEDED".equals(unit.getStatus()) && Boolean.TRUE.equals(unit.getCandidateSaved())) continue;
            requireNotCanceled(task.getId());
            jdbc.update("update review_fanout_unit set status='RUNNING', attempt_no=attempt_no+1, error_code=null, error_message=null, started_at=now(), updated_at=now() where id=?", unit.getId());
            jdbc.update("update review_fanout_snapshot set current_unit_id=?, updated_at=now() where id=?", unit.getId(), snapshotId);
            try {
                WorkflowAgentRunResult child = runner.runFormal(childPlan, input(task, execution, modelId,
                    new ReviewToolScope(task.getProjectId(), version.getId(), snapshotId, unit.getId(), attempt,
                        "DEEP_CHILD", dimensions), "审核当前冻结单元并保存候选问题；不得保存正式审核结果。"));
                calls.addAll(child.modelCalls());
                ReviewFanoutUnitEntity after = fanout.orderedUnits(snapshotId).stream()
                    .filter(candidate -> candidate.getId().equals(unit.getId())).findFirst().orElseThrow();
                if (!"SUCCEEDED".equals(after.getStatus()) || !Boolean.TRUE.equals(after.getCandidateSaved())) {
                    throw invalid("DEEP 子 Agent 未保存完整候选结果。");
                }
                completed++;
                jdbc.update("update review_fanout_unit set completed_at=now(), updated_at=now() where id=?", unit.getId());
                jdbc.update("update review_fanout_snapshot set completed_units=?, current_unit_id=?, updated_at=now() where id=?",
                    completed, unit.getId(), snapshotId);
                task.setOverallProgress(20 + (int) Math.floor(60.0 * completed / units.size()));
                task.setCurrentAction("深度审核单元 %d/%d".formatted(completed, units.size()));
                task.setUpdatedAt(LocalDateTime.now()); tasks.updateById(task);
            } catch (RuntimeException failure) {
                jdbc.update("update review_fanout_unit set status='FAILED', error_code=?, error_message=?, updated_at=now() where id=?",
                    failure.getClass().getSimpleName(), trim(failure.getMessage()), unit.getId());
                int failedNow = jdbc.queryForObject(
                    "select count(*) from review_fanout_unit where snapshot_id=? and status='FAILED'",
                    Integer.class, snapshotId);
                jdbc.update("update review_fanout_snapshot set status='PARTIAL_FAILED', failed_units=?, updated_at=now() where id=?",
                    failedNow, snapshotId);
                throw failure;
            }
        }

        requireNotCanceled(task.getId());
        WorkflowAgentExecutionPlan aggregationPlan = plans.freeze(dimensions, "DEEP_AGGREGATION");
        jdbc.update("update review_fanout_snapshot set status='AGGREGATING', aggregation_status='RUNNING', current_unit_id=null, updated_at=now() where id=?", snapshotId);
        task.setWorkflowPhase("DEEP_AGGREGATION"); task.setCurrentStage("DEEP_AGGREGATION");
        task.setCurrentAction("正在汇总跨单元问题并生成正式审核结果"); task.setOverallProgress(85);
        task.setUpdatedAt(LocalDateTime.now()); tasks.updateById(task);
        try {
            WorkflowAgentRunResult aggregation = runner.runFormal(aggregationPlan, input(task, execution, modelId,
                new ReviewToolScope(task.getProjectId(), version.getId(), snapshotId, null, attempt,
                    "DEEP_AGGREGATION", dimensions),
                "读取全部已保存候选，完成跨单元去重与连续性综合，最后仅调用一次正式保存工具。"));
            calls.addAll(aggregation.modelCalls());
            ReviewTaskEntity committed = tasks.selectById(task.getId());
            if (committed == null || !"COMPLETED".equals(committed.getStatus())
                || !aggregation.runId().equals(committed.getWorkflowAgentRunId())) {
                throw invalid("DEEP 聚合 Agent 未提交本次正式结果。");
            }
            committed.setAggregationRunId(aggregation.runId());
            committed.setUpdatedAt(LocalDateTime.now());
            tasks.updateById(committed);
            jdbc.update("update review_fanout_snapshot set status='SUCCEEDED', aggregation_status='SUCCEEDED', aggregation_run_id=?, completed_at=now(), updated_at=now() where id=?", aggregation.runId(), snapshotId);
            return new Execution(snapshotId, aggregation.runId(), List.copyOf(calls));
        } catch (RuntimeException failure) {
            jdbc.update("update review_fanout_snapshot set status='FAILED', aggregation_status='FAILED', updated_at=now() where id=?", snapshotId);
            throw failure;
        }
    }

    private WorkflowAgentRunInput input(ReviewTaskEntity task, AiExecutionContext execution, Long modelId,
        ReviewToolScope scope, String prompt) {
        return new WorkflowAgentRunInput(ScriptReviewAgentBootstrap.AGENT_CODE, prompt, task.getTenantId(),
            task.getProjectId(), null, null, task.getId(), null, task.getCreatedBy(), execution.task().id,
            execution.claim().attemptId(), execution.task().executionVersion, modelId, scope);
    }

    private void requireNotCanceled(Long taskId) {
        if ("CANCELED".equals(tasks.selectById(taskId).getStatus())) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消。");
        }
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
