package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EpisodeFanoutCoordinator {
    private final EpisodeFanoutStore store;
    private final WorkflowAgentRunner runner;
    private final int concurrency;

    public EpisodeFanoutCoordinator(
        EpisodeFanoutStore store,
        WorkflowAgentRunner runner,
        @Value("${ai.workflow-agent.fanout-concurrency:3}") int concurrency
    ) {
        this.store = store;
        this.runner = runner;
        this.concurrency = Math.max(1, Math.min(16, concurrency));
    }

    public Result execute(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        AiExecutionContext executionContext,
        String agentCode,
        boolean fullRegeneration,
        ChildExecutor childExecutor,
        Finalizer finalizer
    ) {
        List<EpisodeUnit> episodes = store.currentEpisodes(
            task.getTenantId(), task.getProjectId(), task.getScriptId());
        if (episodes.isEmpty()) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE, "没有可处理的当前正式剧集。");
        }
        WorkflowAgentExecutionPlan plan = runner.freezeFormal(agentCode);
        String snapshotHash = episodeSetHash(episodes);
        long snapshotId = store.openSnapshot(
            task, stage, agentCode, plan, episodes, snapshotHash, fullRegeneration);
        if (store.cancellationRequested(snapshotId)) {
            store.cancel(snapshotId);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "逐集 Agent 阶段已取消。");
        }
        List<EpisodeUnit> runnable = store.runnableUnits(snapshotId);
        List<WorkflowAgentModelCall> calls = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrency, Math.max(1, runnable.size())));
        try {
            List<CompletableFuture<Void>> futures = runnable.stream().map(unit ->
                CompletableFuture.runAsync(() -> runUnit(
                    snapshotId, plan, task, stage, executionContext, unit, childExecutor, calls), executor)
            ).toList();
            futures.forEach(CompletableFuture::join);
        } finally {
            executor.shutdownNow();
        }
        Progress progress = store.progress(snapshotId);
        store.updateParentProgress(snapshotId, progress);
        String currentHash = episodeSetHash(store.currentEpisodes(
            task.getTenantId(), task.getProjectId(), task.getScriptId()));
        if (!snapshotHash.equals(currentHash) || !store.snapshotMatches(snapshotId, currentHash)) {
            throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,
                "剧集集合在逐集处理期间发生变化，请基于当前剧集重试。");
        }
        if (progress.failed() > 0 || progress.completed() != progress.total()) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE,
                "逐集 Agent 尚有失败或缺失单元。");
        }
        finalizer.finish(snapshotId);
        store.complete(snapshotId);
        return new Result(snapshotId, progress, List.copyOf(calls));
    }

    private void runUnit(
        long snapshotId,
        WorkflowAgentExecutionPlan plan,
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        AiExecutionContext executionContext,
        EpisodeUnit unit,
        ChildExecutor childExecutor,
        List<WorkflowAgentModelCall> calls
    ) {
        if (store.cancellationRequested(snapshotId)) {
            store.cancel(snapshotId);
            return;
        }
        store.markRunning(snapshotId, unit.episodeId());
        try {
            ChildResult result = childExecutor.run(plan, task, stage, executionContext, unit);
            calls.addAll(result.modelCalls());
            store.markSucceeded(snapshotId, unit.episodeId(), result.runId());
        } catch (RuntimeException exception) {
            String code = exception instanceof BusinessException business
                ? business.getErrorCode().name() : "ANALYSIS_CHILD_FAILED";
            store.markFailed(snapshotId, unit.episodeId(), code, safeMessage(exception));
        } finally {
            store.updateParentProgress(snapshotId, store.progress(snapshotId));
        }
    }

    static String episodeSetHash(List<EpisodeUnit> episodes) {
        String source = episodes.stream()
            .map(unit -> unit.episodeId() + ":" + unit.episodeKey() + ":" + unit.contentFingerprint())
            .reduce("", (left, right) -> left + "|" + right);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(1000, message.length()));
    }

    public record EpisodeUnit(Long episodeId, String episodeKey, String contentFingerprint, String status) {}
    public record Progress(int total, int completed, int failed, int running, int pending, String status) {}
    public record ChildResult(Long runId, List<WorkflowAgentModelCall> modelCalls) {
        public ChildResult { modelCalls = modelCalls == null ? List.of() : List.copyOf(modelCalls); }
    }
    public record Result(long snapshotId, Progress progress, List<WorkflowAgentModelCall> modelCalls) {}

    @FunctionalInterface
    public interface ChildExecutor {
        ChildResult run(WorkflowAgentExecutionPlan plan, ScriptAnalysisTaskEntity task,
                        ScriptAnalysisStageEntity stage, AiExecutionContext executionContext,
                        EpisodeUnit episode);
    }

    @FunctionalInterface
    public interface Finalizer {
        void finish(long snapshotId);
    }
}
