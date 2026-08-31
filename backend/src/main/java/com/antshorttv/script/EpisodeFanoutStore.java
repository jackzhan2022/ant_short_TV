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
        List<EpisodeFanoutCoordinator.EpisodeUnit> episodes,
        String episodeSetHash,
        boolean fullRegeneration);

    List<EpisodeFanoutCoordinator.EpisodeUnit> runnableUnits(long snapshotId);

    void markRunning(long snapshotId, Long episodeId);

    void markSucceeded(long snapshotId, Long episodeId, Long childRunId);

    void markFailed(long snapshotId, Long episodeId, String errorCode, String errorMessage);

    EpisodeFanoutCoordinator.Progress progress(long snapshotId);

    boolean snapshotMatches(long snapshotId, String currentEpisodeSetHash);

    void updateParentProgress(long snapshotId, EpisodeFanoutCoordinator.Progress progress);

    void complete(long snapshotId);

    boolean cancellationRequested(long snapshotId);

    void cancel(long snapshotId);
}
