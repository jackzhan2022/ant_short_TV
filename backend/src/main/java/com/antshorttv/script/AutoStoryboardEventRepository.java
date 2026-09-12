package com.antshorttv.script;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AutoStoryboardEventRepository {
    public static final String POLICY_VERSION = "AUTO_STORYBOARD_V1";
    private final JdbcTemplate jdbc;

    public AutoStoryboardEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long recordPending(Draft draft) {
        try {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                var statement = connection.prepareStatement("""
                    insert into episode_auto_storyboard_event
                      (tenant_id, project_id, script_id, episode_id, episode_key,
                       source_fingerprint, asset_analysis_id, context_snapshot_id, policy_version,
                       status, attempt_no, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, now(), now())
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, draft.tenantId());
                statement.setLong(2, draft.projectId());
                statement.setLong(3, draft.scriptId());
                statement.setLong(4, draft.episodeId());
                statement.setString(5, draft.episodeKey());
                statement.setString(6, draft.sourceFingerprint());
                statement.setLong(7, draft.assetAnalysisId());
                statement.setObject(8, draft.contextSnapshotId());
                statement.setString(9, POLICY_VERSION);
                statement.setLong(10, draft.createdBy());
                return statement;
            }, keys);
            return keys.getKey().longValue();
        } catch (DuplicateKeyException ignored) {
            return requireId(draft);
        }
    }

    @Transactional
    public Optional<Event> claimNext() {
        List<Event> events = jdbc.query("""
            select id, tenant_id, project_id, script_id, episode_id, episode_key,
                   source_fingerprint, asset_analysis_id, context_snapshot_id, policy_version,
                   status, attempt_no, execution_id, created_by
              from episode_auto_storyboard_event
             where status in ('PENDING', 'RETRYABLE', 'BLOCKED_FUNDS')
               and (next_attempt_at is null or next_attempt_at <= now())
             order by case when status = 'PENDING' then 0 else 1 end, id
             limit 1 for update
            """, (row, index) -> event(row));
        if (events.isEmpty()) return Optional.empty();
        Event event = events.get(0);
        int nextAttempt = event.attemptNo() + 1;
        jdbc.update("""
            update episode_auto_storyboard_event
               set status='DISPATCHING', attempt_no=?, error_code=null, error_message=null,
                   updated_at=now() where id=?
            """, nextAttempt, event.id());
        return Optional.of(new Event(
            event.id(), event.tenantId(), event.projectId(), event.scriptId(), event.episodeId(),
            event.episodeKey(), event.sourceFingerprint(), event.assetAnalysisId(),
            event.contextSnapshotId(), event.policyVersion(), "DISPATCHING", nextAttempt,
            event.executionId(), event.createdBy()));
    }

    public int recoverStaleDispatching(int staleSeconds) {
        return jdbc.update("""
            update episode_auto_storyboard_event
               set status='RETRYABLE', error_code='DISPATCH_LEASE_EXPIRED',
                   error_message='自动分镜派发进程中断，事件已恢复。',
                   next_attempt_at=now(), updated_at=now()
             where status='DISPATCHING'
               and updated_at < timestampadd(second, ?, now())
            """, -Math.max(30, staleSeconds));
    }

    public void markDispatched(long id, Long executionId) {
        jdbc.update("""
            update episode_auto_storyboard_event
               set status='DISPATCHED', execution_id=?, finished_at=now(), updated_at=now()
             where id=? and status='DISPATCHING'
            """, executionId, id);
    }

    public void markOutcome(long id, String status, String errorCode, String errorMessage) {
        jdbc.update("""
            update episode_auto_storyboard_event
               set status=?, error_code=?, error_message=?,
                   next_attempt_at=case when ?='BLOCKED_FUNDS' then timestampadd(second, 30, now()) else null end,
                   finished_at=case when ?='BLOCKED_FUNDS' then null else now() end, updated_at=now()
             where id=?
            """, status, errorCode, errorMessage, status, status, id);
    }

    public void markFailed(long id, String errorCode, String errorMessage, boolean retryable) {
        jdbc.update("""
            update episode_auto_storyboard_event
               set status=?, error_code=?, error_message=?,
                   next_attempt_at=case when ? then timestampadd(second, 30, now()) else null end,
                   finished_at=case when ? then null else now() end, updated_at=now()
             where id=?
            """, retryable ? "RETRYABLE" : "FAILED", errorCode, errorMessage,
            retryable, retryable, id);
    }

    private long requireId(Draft draft) {
        return jdbc.queryForObject("""
            select id from episode_auto_storyboard_event
             where tenant_id=? and script_id=? and episode_id=?
               and source_fingerprint=? and policy_version=?
            """, Long.class, draft.tenantId(), draft.scriptId(), draft.episodeId(),
            draft.sourceFingerprint(), POLICY_VERSION);
    }

    private Event event(java.sql.ResultSet row) throws java.sql.SQLException {
        long contextId = row.getLong("context_snapshot_id");
        Long nullableContextId = row.wasNull() ? null : contextId;
        long executionId = row.getLong("execution_id");
        Long nullableExecutionId = row.wasNull() ? null : executionId;
        return new Event(
            row.getLong("id"), row.getLong("tenant_id"), row.getLong("project_id"),
            row.getLong("script_id"), row.getLong("episode_id"), row.getString("episode_key"),
            row.getString("source_fingerprint"), row.getLong("asset_analysis_id"), nullableContextId,
            row.getString("policy_version"), row.getString("status"), row.getInt("attempt_no"),
            nullableExecutionId, row.getLong("created_by"));
    }

    public record Draft(
        Long tenantId, Long projectId, Long scriptId, Long episodeId, String episodeKey,
        String sourceFingerprint, Long assetAnalysisId, Long contextSnapshotId, Long createdBy
    ) {}

    public record Event(
        Long id, Long tenantId, Long projectId, Long scriptId, Long episodeId, String episodeKey,
        String sourceFingerprint, Long assetAnalysisId, Long contextSnapshotId, String policyVersion,
        String status, int attemptNo, Long executionId, Long createdBy
    ) {}
}
