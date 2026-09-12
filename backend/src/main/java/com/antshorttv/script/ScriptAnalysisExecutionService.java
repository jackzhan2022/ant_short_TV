package com.antshorttv.script;

import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.execution.AiExecutionClaimLostException;
import com.antshorttv.execution.AiExecutionClaimService;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class ScriptAnalysisExecutionService {
    @Autowired private ScriptAnalysisConfigSnapshotService configSnapshotService;
    @Autowired(required = false) private GlobalUnderstandingAgentAdapter globalUnderstandingAgentAdapter;
    @Autowired(required = false) private EpisodeSplittingAgentAdapter episodeSplittingAgentAdapter;
    @Autowired(required = false) private EpisodeSummaryAgentAdapter episodeSummaryAgentAdapter;
    @Autowired(required = false) private EpisodeFanoutCoordinator episodeFanoutCoordinator;
    @Autowired(required = false) private AssetRecognitionAgentAdapter assetRecognitionAgentAdapter;
    @Autowired(required = false) private AssetRecognitionFinalizer assetRecognitionFinalizer;
    @Autowired(required = false) private AiExecutionClaimService executionClaimService;
    private final ScriptAnalysisTaskMapper taskMapper;
    private final ScriptAnalysisStageMapper stageMapper;
    private final ScriptAnalysisResultMapper resultMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper versionMapper;
    private final ProjectAiConfigService projectAiConfigService;

    public ScriptAnalysisExecutionService(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptAnalysisStageMapper stageMapper,
        ScriptAnalysisResultMapper resultMapper,
        ScriptMapper scriptMapper,
        ScriptVersionMapper versionMapper,
        ProjectAiConfigService projectAiConfigService
    ) {
        this.taskMapper = taskMapper;
        this.stageMapper = stageMapper;
        this.resultMapper = resultMapper;
        this.scriptMapper = scriptMapper;
        this.versionMapper = versionMapper;
        this.projectAiConfigService = projectAiConfigService;
    }

    public void executeTask(Long taskId) {
        executeTask(taskId, null);
    }

    public ScriptAnalysisExecutionOutcome executeTask(Long taskId, AiExecutionContext executionContext) {
        InvocationTracker tracker = new InvocationTracker();
        ScriptAnalysisTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || "COMPLETED".equals(task.getStatus())) {
            return new ScriptAnalysisExecutionOutcome(List.of());
        }
        requireExecutionActive(executionContext);
        ScriptVersionEntity version = versionMapper.selectById(task.getScriptVersionId());
        if (version == null || !task.getScriptVersionId().equals(version.getId())) {
            failTask(task, "SCRIPT_VERSION_NOT_FOUND", "分析绑定的剧本版本不存在。");
            return new ScriptAnalysisExecutionOutcome(List.of());
        }

        task.setStatus("RUNNING");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        List<ScriptAnalysisStageEntity> stages = stageMapper.selectByTask(task.getId());
        for (ScriptAnalysisStageEntity stage : stages) {
            if (isIndependentEpisodeBranch(stage.getStageCode())) continue;
            if ("SUCCEEDED".equals(stage.getStatus())) {
                continue;
            }
            executeStage(task, stage, executionContext, tracker);
            if ("FAILED".equals(stage.getStatus())) {
                RuntimeException failure = new IllegalStateException(
                    task.getErrorMessage() == null ? "Script analysis failed." : task.getErrorMessage());
                throw failure;
            }
        }
        executeEpisodeBranches(task, stages, executionContext, tracker);
        requireExecutionActive(executionContext);
        task.setStatus("COMPLETED");
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setCurrentStage(null);
        task.setCurrentAction("四阶段分析已完成");
        task.setOverallProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return new ScriptAnalysisExecutionOutcome(List.copyOf(tracker.calls));
    }

    private boolean isIndependentEpisodeBranch(String stageCode) {
        return "EPISODE_SUMMARY".equals(stageCode)
            || "CHARACTER_SCENE_RECOGNITION".equals(stageCode);
    }

    private void executeStage(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        requireExecutionActive(executionContext);
        if (!isCurrentVersion(task)) {
            failStaleStage(task, stage);
            return;
        }
        startStage(task, stage);

        try {
            if ("GLOBAL_UNDERSTANDING".equals(stage.getStageCode())) {
                requireAgent(globalUnderstandingAgentAdapter, "剧情全局理解");
                GlobalUnderstandingProgress reading = GlobalUnderstandingProgress.reading();
                stage.setProgressPercent(reading.percent());
                stage.setCurrentAction(reading.action());
                stage.setUpdatedAt(LocalDateTime.now());
                stageMapper.updateById(stage);
                GlobalUnderstandingAgentAdapter.Execution execution =
                    globalUnderstandingAgentAdapter.execute(task, stage, executionContext, frozenModelId(task));
                execution.modelCalls().forEach(tracker::record);
                requireExecutionActive(executionContext);
                stage.setStatus("SUCCEEDED");
                GlobalUnderstandingProgress committed = GlobalUnderstandingProgress.committed();
                stage.setProgressPercent(committed.percent());
                stage.setCompletedUnits(1);
                stage.setTotalUnits(1);
                stage.setCurrentAction(committed.action());
                stage.setFinishedAt(LocalDateTime.now());
                stage.setRetryable(false);
                task.setOverallProgress(25);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                return;
            } else if ("EPISODE_SPLITTING".equals(stage.getStageCode())) {
                requireAgent(episodeSplittingAgentAdapter, "剧集拆分");
                EpisodeSplittingAgentAdapter.Execution execution = episodeSplittingAgentAdapter.execute(
                    task, stage, executionContext, frozenModelId(task));
                execution.modelCalls().forEach(tracker::record);
                requireExecutionActive(executionContext);
                stage.setStatus("SUCCEEDED");
                stage.setProgressPercent(100);
                stage.setCompletedUnits(1);
                stage.setTotalUnits(1);
                stage.setCurrentAction("剧集智能拆分已保存");
                stage.setFinishedAt(LocalDateTime.now());
                stage.setRetryable(false);
                stage.setUpdatedAt(LocalDateTime.now());
                stageMapper.updateById(stage);
                task.setOverallProgress(50);
                task.setUpdatedAt(LocalDateTime.now());
                taskMapper.updateById(task);
                return;
            } else {
                throw new IllegalStateException("不支持的剧本分析阶段：" + stage.getStageCode());
            }
        } catch (AiExecutionClaimLostException exception) {
            throw exception;
        } catch (Exception exception) {
            requireExecutionActive(executionContext);
            recordStageFailure(task, stage, exception);
            failTask(task, stage.getErrorCode(), stage.getErrorMessage());
        }
    }


    private void startStage(ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage) {
        stage.setStatus("RUNNING");
        stage.setProgressPercent(10);
        stage.setCurrentAction(actionFor(stage.getStageCode()));
        stage.setAttemptNo((stage.getAttemptNo() == null ? 0 : stage.getAttemptNo()) + 1);
        stage.setStartedAt(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());
        stageMapper.updateById(stage);
        task.setCurrentStage(stage.getStageCode());
        task.setCurrentAction(stage.getCurrentAction());
        task.setOverallProgress(Math.max(0, (stage.getStageOrder() - 1) * 25 + 5));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void recordStageFailure(
        ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage, Exception exception
    ) {
        String errorCode = exception instanceof BusinessException businessException
            ? businessException.getErrorCode().name()
            : "AI_ANALYSIS_FAILED";
        String errorMessage = exception.getMessage();
        persistFailure(task, stage, errorCode, errorMessage);
        stage.setStatus("FAILED");
        stage.setProgressPercent(Math.max(10, stage.getProgressPercent() == null ? 10 : stage.getProgressPercent()));
        stage.setErrorCode(errorCode);
        stage.setErrorMessage(errorMessage);
        if (("EPISODE_SUMMARY".equals(stage.getStageCode())
            || "CHARACTER_SCENE_RECOGNITION".equals(stage.getStageCode()))) {
            ScriptAnalysisStageEntity persisted = stageMapper.selectById(stage.getId());
            if (persisted != null) {
                stage.setCompletedUnits(persisted.getCompletedUnits());
                stage.setTotalUnits(persisted.getTotalUnits());
                stage.setProgressPercent(Math.max(stage.getProgressPercent(), persisted.getProgressPercent()));
            }
        }
        if ("GLOBAL_UNDERSTANDING".equals(stage.getStageCode())) {
            try {
                GlobalUnderstandingProgress failure = GlobalUnderstandingProgress.failed(
                    com.antshorttv.common.ErrorCode.valueOf(errorCode));
                stage.setProgressPercent(failure.percent());
                stage.setCurrentAction(failure.action());
            } catch (IllegalArgumentException ignored) {
                stage.setCurrentAction("剧情全局理解失败，可重试");
            }
        }
        stage.setRetryable(true);
        stage.setUpdatedAt(LocalDateTime.now());
        stageMapper.updateById(stage);
    }

    private void executeEpisodeBranches(
        ScriptAnalysisTaskEntity task, List<ScriptAnalysisStageEntity> stages,
        AiExecutionContext executionContext, InvocationTracker tracker
    ) {
        List<EpisodeFanoutCoordinator.Prepared> prepared = new java.util.ArrayList<>();
        for (ScriptAnalysisStageEntity stage : stages) {
            if (!isIndependentEpisodeBranch(stage.getStageCode()) || "SUCCEEDED".equals(stage.getStatus())) continue;
            requireExecutionActive(executionContext);
            if (!isCurrentVersion(task)) {
                failStaleStage(task, stage);
                continue;
            }
            startStage(task, stage);
            try {
                boolean summary = "EPISODE_SUMMARY".equals(stage.getStageCode());
                requireAgent(summary ? episodeSummaryAgentAdapter : assetRecognitionAgentAdapter,
                    summary ? "剧集概要" : "资产识别");
                requireAgent(episodeFanoutCoordinator, "剧集并行执行");
                if (!summary) requireAgent(assetRecognitionFinalizer, "资产识别汇总");
                prepared.add(episodeFanoutCoordinator.prepare(task, stage, executionContext, frozenModelId(task),
                    summary ? com.antshorttv.workflowagent.agent.EpisodeSummaryAgentBootstrap.AGENT_CODE
                        : com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap.AGENT_CODE, false));
            } catch (AiExecutionClaimLostException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                requireExecutionActive(executionContext);
                recordStageFailure(task, stage, exception);
            }
        }

        // Both durable snapshots exist before workers start. Only this thread writes the task.
        java.util.concurrent.ExecutorService workers = java.util.concurrent.Executors.newFixedThreadPool(2);
        var completions = new java.util.concurrent.ExecutorCompletionService<BranchCompletion>(workers);
        AiExecutionClaimLostException ownershipLost = null;
        try {
            for (EpisodeFanoutCoordinator.Prepared branch : prepared) {
                completions.submit(() -> {
                    try {
                        boolean summary = "EPISODE_SUMMARY".equals(branch.stage().getStageCode());
                        EpisodeFanoutCoordinator.Result result = episodeFanoutCoordinator.executePrepared(branch,
                            (plan, currentTask, currentStage, currentExecution, episode) -> {
                                requireExecutionActive(currentExecution);
                                if (summary) {
                                    var child = episodeSummaryAgentAdapter.executeClaimedChild(plan, currentTask,
                                        currentStage, episode, currentExecution, branch.effectiveModelId());
                                    return new EpisodeFanoutCoordinator.ChildResult(child.agentRunId(), child.modelCalls());
                                }
                                var child = assetRecognitionAgentAdapter.executeClaimedChild(plan, currentTask,
                                    currentStage, episode, currentExecution, branch.effectiveModelId());
                                return new EpisodeFanoutCoordinator.ChildResult(child.agentRunId(), child.modelCalls());
                            }, summary ? snapshotId -> { } : assetRecognitionFinalizer::finish);
                        return new BranchCompletion(branch.stage(), result, null);
                    } catch (RuntimeException exception) {
                        return new BranchCompletion(branch.stage(), null, exception);
                    }
                });
            }
            for (int index = 0; index < prepared.size(); index++) {
                BranchCompletion completion = completions.take().get();
                if (ownershipLost != null) continue;
                try {
                    requireExecutionActive(executionContext);
                    if (completion.failure() instanceof AiExecutionClaimLostException lost) throw lost;
                    if (completion.failure() != null) {
                        recordStageFailure(task, completion.stage(), completion.failure());
                    } else if (!isCurrentVersion(task)) {
                        failStaleStage(task, completion.stage());
                    } else {
                        var stage = completion.stage();
                        var result = completion.result();
                        result.modelCalls().forEach(tracker::record);
                        stage.setStatus("SUCCEEDED");
                        stage.setProgressPercent(100);
                        stage.setCompletedUnits(result.progress().completed());
                        stage.setTotalUnits(result.progress().total());
                        stage.setCurrentAction("EPISODE_SUMMARY".equals(stage.getStageCode())
                            ? "全部剧集概要已保存" : "全部剧集角色、场景和道具已保存");
                        stage.setFinishedAt(LocalDateTime.now());
                        stage.setRetryable(false);
                        stage.setErrorCode(null);
                        stage.setErrorMessage(null);
                        stage.setUpdatedAt(LocalDateTime.now());
                        stageMapper.updateById(stage);
                    }
                } catch (AiExecutionClaimLostException lost) {
                    // Drain workers before yielding ownership; do not publish stale terminal states.
                    ownershipLost = lost;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("剧集分析执行被中断。", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            throw new IllegalStateException("剧集分析执行失败。", exception.getCause());
        } finally {
            workers.shutdownNow();
        }
        if (ownershipLost != null) throw ownershipLost;
        for (ScriptAnalysisStageEntity stage : stages) {
            if (isIndependentEpisodeBranch(stage.getStageCode()) && "FAILED".equals(stage.getStatus())) {
                failTask(task, stage.getErrorCode(), stage.getErrorMessage());
                throw new IllegalStateException(stage.getErrorMessage());
            }
        }
    }

    private record BranchCompletion(
        ScriptAnalysisStageEntity stage, EpisodeFanoutCoordinator.Result result, RuntimeException failure
    ) {}

    private void requireAgent(Object component, String stageName) {
        if (component == null) {
            throw new IllegalStateException(stageName + " Workflow Agent 不可用，请检查 Agent 配置和服务组件。");
        }
    }

    private void requireExecutionActive(AiExecutionContext context) {
        if (context != null) {
            if (executionClaimService == null) {
                throw new IllegalStateException("AI execution claim service is unavailable.");
            }
            executionClaimService.requireActive(context.claim());
        }
    }

    private Long frozenModelId(ScriptAnalysisTaskEntity task) {
        if (configSnapshotService == null) {
            throw new IllegalStateException("剧本分析配置快照服务不可用。");
        }
        return configSnapshotService.modelIdFor(task.getId());
    }

    private boolean isCurrentVersion(ScriptAnalysisTaskEntity task) {
        ScriptEntity currentScript = scriptMapper.selectById(task.getScriptId());
        return currentScript != null && task.getScriptVersionId().equals(currentScript.getCurrentVersionId());
    }

    private void failStaleStage(ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage) {
        stage.setStatus("FAILED");
        stage.setProgressPercent(stage.getProgressPercent() == null ? 0 : stage.getProgressPercent());
        stage.setErrorCode("STALE_SCRIPT_VERSION");
        stage.setErrorMessage("分析版本已过期，已停止更新旧结果。");
        stage.setRetryable(false);
        stage.setUpdatedAt(LocalDateTime.now());
        stageMapper.updateById(stage);
        failTask(task, "STALE_SCRIPT_VERSION", "分析版本已过期，已停止更新旧结果。");
    }

    private Long stageDuration(ScriptAnalysisStageEntity stage) {
        if (stage.getStartedAt() == null) {
            return null;
        }
        return Math.max(0, java.time.Duration.between(stage.getStartedAt(), LocalDateTime.now()).toMillis());
    }

    private void persistFailure(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        String errorCode,
        String errorMessage
    ) {
        ScriptAnalysisResultEntity result = new ScriptAnalysisResultEntity();
        result.setTaskId(task.getId());
        result.setStageId(stage.getId());
        result.setResultType(stage.getStageCode());
        result.setSchemaVersion("v1");
        result.setStatus("FAILED");
        result.setExecutionId(task.getExecutionId());
        result.setDurationMs(stageDuration(stage));
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setRetryable(true);
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        resultMapper.insert(result);
    }

    private String actionFor(String stageCode) {
        return switch (stageCode) {
            case "GLOBAL_UNDERSTANDING" -> "正在理解剧情主线、人物关系和核心冲突";
            case "EPISODE_SPLITTING" -> "正在根据剧情节点智能拆分剧集";
            case "EPISODE_SUMMARY" -> "正在提炼每集概要和结尾悬念";
            case "CHARACTER_SCENE_RECOGNITION" -> "正在识别角色、场景和关键道具";
            default -> "正在分析剧本";
        };
    }

    private void failTask(ScriptAnalysisTaskEntity task, String errorCode, String message) {
        task.setStatus("FAILED");
        task.setErrorCode(errorCode);
        task.setErrorMessage(message == null ? "分析失败。" : message);
        task.setCurrentAction("分析失败，可重试失败阶段");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private static final class InvocationTracker {
        private final List<ScriptAnalysisCallEvidence> calls = new CopyOnWriteArrayList<>();

        private void record(WorkflowAgentModelCall invocation) {
            calls.add(new ScriptAnalysisCallEvidence(
                invocation.callLogId(), invocation.modelId(), invocation.providerId(),
                invocation.providerRequestId(), invocation.transportOutcome(), invocation.businessOutcome()
            ));
        }
    }
}
