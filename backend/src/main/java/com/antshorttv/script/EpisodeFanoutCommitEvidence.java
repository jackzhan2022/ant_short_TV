package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Written in the same transaction as formal episode output, before runner bookkeeping. */
public final class EpisodeFanoutCommitEvidence {
    private EpisodeFanoutCommitEvidence() {}

    public static void record(JdbcTemplate jdbc, ToolExecutionContext context, String fingerprint) {
        Long snapshotId = context.runState().get("fanoutSnapshotId", Long.class);
        Integer attemptNo = context.runState().get("fanoutUnitAttemptNo", Integer.class);
        if (snapshotId == null && attemptNo == null) return;
        int updated = jdbc.update("""
            update script_analysis_fanout_unit
               set child_run_id = ?, updated_at = now()
             where snapshot_id = ? and episode_id = ? and attempt_no = ? and status = 'RUNNING'
               and content_fingerprint = ?
               and exists (
                   select 1 from script_analysis_fanout_snapshot snapshot
                   join script_analysis_stage stage on stage.id = snapshot.stage_id
                   join ai_workflow_agent_run run on run.id = ?
                   where snapshot.id = script_analysis_fanout_unit.snapshot_id
                     and snapshot.attempt_no = stage.attempt_no and snapshot.status <> 'CANCELLED'
                     and snapshot.tenant_id = ? and snapshot.project_id = ? and snapshot.script_id = ?
                     and snapshot.task_id = ? and snapshot.stage_id = ?
                     and run.tenant_id = snapshot.tenant_id and run.project_id = snapshot.project_id
                     and run.script_id = snapshot.script_id and run.task_id = snapshot.task_id
                     and run.analysis_stage_id = snapshot.stage_id and run.episode_id = ?
                     and run.agent_code = snapshot.agent_code and run.model_id = snapshot.model_id
                     and run.run_type = 'FORMAL')
            """, context.agentRunId(), snapshotId, context.episodeId(), attemptNo,
            fingerprint, context.agentRunId(), context.tenantId(),
            context.projectId(), context.scriptId(), context.taskId(), context.analysisStageId(), context.episodeId());
        if (updated != 1) {
            throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,
                "逐集执行归属或源剧集已变化，正式保存已回滚，请基于当前剧集重试。");
        }
    }
}
