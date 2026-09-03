package com.antshorttv.script;

import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import java.util.List;

public interface EpisodeFanoutStore {
    List<EpisodeFanoutCoordinator.EpisodeUnit> currentEpisodes(
        Long tenantId, Long projectId, Long scriptId);

    long openSnapshot(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        String agentCode,
        WorkflowAgentExecutionPlan plan,
        Long effectiveModelId,
        List<EpisodeFanoutCoordinator.EpisodeUnit> episodes,
        String episodeSetHash,
        boolean fullRegeneration);

    List<EpisodeFanoutCoordinator.EpisodeUnit> runnableUnits(long snapshotId);

    int markRunning(long snapshotId, Long episodeId);

    void markSucceeded(long snapshotId, Long episodeId, int unitAttemptNo, Long childRunId);

    void markFailed(
        long snapshotId, Long episodeId, int unitAttemptNo, String errorCode, String errorMessage);

    EpisodeFanoutCoordinator.Progress progress(long snapshotId);

    boolean snapshotMatches(long snapshotId, String currentEpisodeSetHash);

    void updateParentProgress(long snapshotId, EpisodeFanoutCoordinator.Progress progress);

    void complete(long snapshotId);

    boolean cancellationRequested(long snapshotId, Long executionId);

    void cancel(long snapshotId);
}
