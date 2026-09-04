package com.antshorttv.script;

import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Primary
public class JdbcEpisodeFanoutStore implements EpisodeFanoutStore {
    private final JdbcTemplate jdbc;

    public JdbcEpisodeFanoutStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EpisodeFanoutCoordinator.EpisodeUnit> currentEpisodes(
        Long tenantId, Long projectId, Long scriptId
    ) {
        return jdbc.query("""
            select id, stable_key, content_fingerprint, status from script_episode
             where tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null order by episode_no, id
            """, (row, index) -> new EpisodeFanoutCoordinator.EpisodeUnit(
                row.getLong("id"), row.getString("stable_key"),
                row.getString("content_fingerprint"), row.getString("status")),
            tenantId, projectId, scriptId);
    }

    @Override
    @Transactional
    public long openSnapshot(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        String agentCode,
        WorkflowAgentExecutionPlan plan,
        Long effectiveModelId,
        List<EpisodeFanoutCoordinator.EpisodeUnit> episodes,
        String episodeSetHash,
        boolean fullRegeneration
    ) {
        Integer attempt = stage.getAttemptNo() == null ? 1 : stage.getAttemptNo();
        jdbc.queryForObject(
            "select id from script_analysis_stage where id = ? for update",
            Long.class,
            stage.getId()
        );
        List<Long> sameAttempt = jdbc.queryForList("""
            select id from script_analysis_fanout_snapshot
             where stage_id = ? and attempt_no = ? and agent_code = ?
               and agent_revision = ? and model_id = ?
            """, Long.class, stage.getId(), attempt, agentCode,
            plan.agent().revision(), effectiveModelId);
        if (!sameAttempt.isEmpty()) {
            return sameAttempt.get(0);
        }
        if (!fullRegeneration) {
            List<Long> reusable = jdbc.queryForList("""
                select id from script_analysis_fanout_snapshot
                 where stage_id = ? and agent_code = ? and episode_set_hash = ?
                   and agent_revision = ? and model_id = ?
                   and status in ('RUNNING', 'PARTIAL_FAILED', 'FAILED')
                 order by id desc limit 1
                """, Long.class, stage.getId(), agentCode, episodeSetHash,
                plan.agent().revision(), effectiveModelId);
            if (!reusable.isEmpty()) {
                long snapshotId = reusable.get(0);
                jdbc.update("""
                    update script_analysis_fanout_snapshot
                       set attempt_no = ?, status = 'RUNNING', updated_at = now()
                     where id = ?
                    """, attempt, snapshotId);
                jdbc.update("""
                    update script_analysis_fanout_unit
                       set status = 'STALE', finished_at = now(), updated_at = now(),
                           error_code = 'EXECUTION_INTERRUPTED',
                           error_message = '上一执行轮次在单集处理期间中断，已回收等待重试。'
                     where snapshot_id = ? and status = 'RUNNING'
                    """, snapshotId);
                jdbc.update("""
                    update script_analysis_fanout_unit
                       set status = 'STALE', updated_at = now()
                     where snapshot_id = ? and status = 'FAILED'
                    """, snapshotId);
                return snapshotId;
            }
        }
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into script_analysis_fanout_snapshot
                  (tenant_id, project_id, script_id, task_id, stage_id, stage_code, attempt_no,
                   agent_code, agent_revision, model_id, episode_set_hash, status, total_units,
                   completed_units, failed_units, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, 0, 0, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, task.getTenantId());
            statement.setLong(2, task.getProjectId());
            statement.setLong(3, task.getScriptId());
            statement.setLong(4, task.getId());
            statement.setLong(5, stage.getId());
            statement.setString(6, stage.getStageCode());
            statement.setInt(7, attempt);
            statement.setString(8, agentCode);
            statement.setLong(9, plan.agent().revision());
            statement.setLong(10, effectiveModelId);
            statement.setString(11, episodeSetHash);
            statement.setInt(12, episodes.size());
            return statement;
        }, keys);
        long snapshotId = keys.getKey().longValue();
        for (EpisodeFanoutCoordinator.EpisodeUnit episode : episodes) {
            jdbc.update("""
                insert into script_analysis_fanout_unit
                  (snapshot_id, episode_id, episode_key, content_fingerprint, status,
                   attempt_no, created_at, updated_at)
                values (?, ?, ?, ?, 'PENDING', 0, now(), now())
                """, snapshotId, episode.episodeId(), episode.episodeKey(), episode.contentFingerprint());
        }
        return snapshotId;
    }

    @Override
    public List<EpisodeFanoutCoordinator.EpisodeUnit> runnableUnits(long snapshotId) {
        return jdbc.query("""
            select episode_id, episode_key, content_fingerprint, status
              from script_analysis_fanout_unit
             where snapshot_id = ? and status in ('PENDING', 'FAILED', 'STALE')
             order by id
            """, (row, index) -> new EpisodeFanoutCoordinator.EpisodeUnit(
                row.getLong("episode_id"), row.getString("episode_key"),
                row.getString("content_fingerprint"), row.getString("status")), snapshotId);
    }

    @Override
    @Transactional
    public int markRunning(long snapshotId, Long episodeId) {
        int updated = jdbc.update("""
            update script_analysis_fanout_unit
               set status = 'RUNNING', attempt_no = attempt_no + 1, error_code = null,
                   error_message = null, started_at = now(), finished_at = null, updated_at = now()
             where snapshot_id = ? and episode_id = ? and status in ('PENDING', 'FAILED', 'STALE')
            """, snapshotId, episodeId);
        if (updated != 1) return 0;
        Integer attemptNo = jdbc.queryForObject("""
            select attempt_no from script_analysis_fanout_unit
             where snapshot_id = ? and episode_id = ?
            """, Integer.class, snapshotId, episodeId);
        return attemptNo == null ? 0 : attemptNo;
    }

    @Override public void markSucceeded(
        long snapshotId, Long episodeId, int unitAttemptNo, Long childRunId
    ) {
        jdbc.update("""
            update script_analysis_fanout_unit
               set status = 'SUCCEEDED', child_run_id = ?, error_code = null, error_message = null,
                   finished_at = now(), updated_at = now()
             where snapshot_id = ? and episode_id = ?
               and status = 'RUNNING' and attempt_no = ?
            """, childRunId, snapshotId, episodeId, unitAttemptNo);
    }

    @Override public void markFailed(
        long snapshotId, Long episodeId, int unitAttemptNo, String errorCode, String errorMessage
    ) {
        jdbc.update("""
            update script_analysis_fanout_unit
               set status = 'FAILED', error_code = ?, error_message = ?, finished_at = now(), updated_at = now()
             where snapshot_id = ? and episode_id = ? and status = 'RUNNING' and attempt_no = ?
            """, errorCode, errorMessage, snapshotId, episodeId, unitAttemptNo);
    }

    @Override public EpisodeFanoutCoordinator.Progress progress(long snapshotId) {
        return jdbc.queryForObject("""
            select count(*) total,
                   sum(case when status = 'SUCCEEDED' then 1 else 0 end) completed,
                   sum(case when status = 'FAILED' then 1 else 0 end) failed,
                   sum(case when status = 'RUNNING' then 1 else 0 end) running,
                   sum(case when status in ('PENDING', 'STALE') then 1 else 0 end) pending
              from script_analysis_fanout_unit where snapshot_id = ?
            """, (row, index) -> {
                int total = row.getInt("total");
                int completed = row.getInt("completed");
                int failed = row.getInt("failed");
                int running = row.getInt("running");
                int pending = row.getInt("pending");
                String status = completed == total ? "SUCCEEDED"
                    : running > 0 ? "RUNNING" : pending > 0 ? "PENDING" : "PARTIAL_FAILED";
                return new EpisodeFanoutCoordinator.Progress(total, completed, failed, running, pending, status);
            }, snapshotId);
    }

    @Override public boolean snapshotMatches(long snapshotId, String currentEpisodeSetHash) {
        Integer count = jdbc.queryForObject(
            "select count(*) from script_analysis_fanout_snapshot where id = ? and episode_set_hash = ?",
            Integer.class, snapshotId, currentEpisodeSetHash);
        return count != null && count == 1;
    }

    @Override public void updateParentProgress(long snapshotId, EpisodeFanoutCoordinator.Progress progress) {
        int terminal = progress.completed() + progress.failed();
        int percent = progress.total() == 0 ? 0 : terminal * 100 / progress.total();
        jdbc.update("""
            update script_analysis_stage stage
               set progress_percent = greatest(progress_percent, ?), completed_units = ?, total_units = ?,
                   current_action = ?, retryable = ?, updated_at = now()
             where id = (select fanout.stage_id from script_analysis_fanout_snapshot fanout where fanout.id = ?)
            """, percent, progress.completed(), progress.total(),
            progress.running() > 0 || progress.pending() > 0
                ? "正在逐集处理" : progress.failed() > 0 ? "部分剧集处理失败" : "逐集处理已完成",
            progress.failed() > 0, snapshotId);
        jdbc.update("""
            update script_analysis_fanout_snapshot
               set completed_units = ?, failed_units = ?, status = ?, updated_at = now() where id = ?
            """, progress.completed(), progress.failed(), progress.status(), snapshotId);
    }

    @Override public void complete(long snapshotId) {
        jdbc.update("""
            update script_analysis_fanout_snapshot
               set status = 'SUCCEEDED', completed_units = total_units, failed_units = 0,
                   finished_at = now(), updated_at = now() where id = ?
            """, snapshotId);
    }

    @Override public boolean cancellationRequested(long snapshotId, Long executionId) {
        Integer count = jdbc.queryForObject("""
            select count(*) from script_analysis_fanout_snapshot fanout
              join script_analysis_stage stage on stage.id = fanout.stage_id
              join script_analysis_task task on task.id = fanout.task_id
             where fanout.id = ? and (fanout.status = 'CANCELLED'
               or stage.status = 'CANCELLED' or task.status = 'CANCELLED'
               or (? is not null and exists (
                   select 1 from ai_execution_task execution
                    where execution.id = ? and execution.status = 'CANCELED')))
            """, Integer.class, snapshotId, executionId, executionId);
        return count != null && count > 0;
    }

    @Override public void cancel(long snapshotId) {
        jdbc.update("""
            update script_analysis_fanout_unit set status = 'CANCELLED', finished_at = now(), updated_at = now()
             where snapshot_id = ? and status in ('PENDING', 'RUNNING')
            """, snapshotId);
        jdbc.update("""
            update script_analysis_fanout_snapshot
               set status = 'CANCELLED', cancelled_at = now(), updated_at = now() where id = ?
            """, snapshotId);
    }
}
