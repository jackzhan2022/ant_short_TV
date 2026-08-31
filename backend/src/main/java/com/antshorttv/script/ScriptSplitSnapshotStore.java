package com.antshorttv.script;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ScriptSplitSnapshotStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ScriptSplitSnapshotStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public long createOrResume(
        SplitScope scope,
        String contentHash,
        String fallbackReason,
        String plannerVersion,
        List<SplitChunkSeed> chunks
    ) {
        List<Long> existing = jdbc.queryForList("""
            select id from script_split_snapshot
             where tenant_id = ? and project_id = ? and script_id = ? and parent_run_id = ?
               and content_hash = ? and planner_version = ? and status <> 'STALE'
             order by id desc limit 1
            """, Long.class, scope.tenantId(), scope.projectId(), scope.scriptId(),
            scope.parentRunId(), contentHash, plannerVersion);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into script_split_snapshot
                  (tenant_id, project_id, script_id, parent_run_id, content_hash, mode,
                   fallback_reason, status, planner_version, total_chunks,
                   completed_chunks, failed_chunks, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'CHUNK_FALLBACK', ?, 'RUNNING', ?, ?, 0, 0, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, scope.tenantId());
            statement.setLong(2, scope.projectId());
            statement.setLong(3, scope.scriptId());
            statement.setLong(4, scope.parentRunId());
            statement.setString(5, contentHash);
            statement.setString(6, fallbackReason);
            statement.setString(7, plannerVersion);
            statement.setInt(8, chunks.size());
            return statement;
        }, keys);
        long snapshotId = keys.getKey().longValue();
        for (SplitChunkSeed chunk : chunks) {
            jdbc.update("""
                insert into script_split_chunk
                  (snapshot_id, chunk_no, core_start, core_end, context_start, context_end,
                   content_hash, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'PENDING', now(), now())
                """, snapshotId, chunk.chunkNo(), chunk.coreStart(), chunk.coreEnd(),
                chunk.contextStart(), chunk.contextEnd(), chunk.contentHash());
        }
        return snapshotId;
    }

    @Transactional
    public void markChunkSucceeded(
        long snapshotId, int chunkNo, Long aiCallLogId, JsonNode candidates
    ) {
        jdbc.update("""
            update script_split_chunk
               set status = 'SUCCEEDED', ai_call_log_id = ?, candidate_json = ?,
                   error_code = null, error_message = null, updated_at = now()
             where snapshot_id = ? and chunk_no = ?
            """, aiCallLogId, candidates == null ? null : candidates.toString(), snapshotId, chunkNo);
        refreshCounts(snapshotId);
    }

    @Transactional
    public void markChunkFailed(long snapshotId, int chunkNo, String errorCode, String errorMessage) {
        jdbc.update("""
            update script_split_chunk
               set status = 'FAILED', error_code = ?, error_message = ?, updated_at = now()
             where snapshot_id = ? and chunk_no = ?
               and status <> 'SUCCEEDED'
            """, errorCode, errorMessage, snapshotId, chunkNo);
        refreshCounts(snapshotId);
    }

    @Transactional
    public void cancel(long snapshotId) {
        jdbc.update("""
            update script_split_chunk set status = 'CANCELLED', updated_at = now()
             where snapshot_id = ? and status in ('PENDING', 'RUNNING', 'FAILED')
            """, snapshotId);
        jdbc.update("""
            update script_split_snapshot
               set status = 'CANCELLED', finished_at = now(), updated_at = now()
             where id = ? and status not in ('SUCCEEDED', 'STALE')
            """, snapshotId);
    }

    public List<SplitChunk> retryableChunks(long snapshotId) {
        return jdbc.query("""
            select * from script_split_chunk
             where snapshot_id = ? and status in ('PENDING', 'FAILED') order by chunk_no
            """, (row, index) -> chunk(row.getLong("id"), row.getLong("snapshot_id"),
            row.getInt("chunk_no"), row.getInt("core_start"), row.getInt("core_end"),
            row.getInt("context_start"), row.getInt("context_end"),
            row.getString("content_hash"), row.getString("status"),
            row.getObject("ai_call_log_id", Long.class), row.getString("candidate_json")), snapshotId);
    }

    public SplitSnapshot require(long snapshotId) {
        return jdbc.queryForObject("select * from script_split_snapshot where id = ?",
            (row, index) -> new SplitSnapshot(
                row.getLong("id"), row.getLong("parent_run_id"), row.getString("content_hash"),
                row.getString("mode"), row.getString("fallback_reason"), row.getString("status"),
                row.getInt("total_chunks"), row.getInt("completed_chunks"),
                row.getInt("failed_chunks")), snapshotId);
    }

    @Transactional
    public void markStaleForDifferentHash(SplitScope scope, String currentHash) {
        jdbc.update("""
            update script_split_snapshot
               set status = 'STALE', finished_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and script_id = ?
               and content_hash <> ? and status <> 'STALE'
            """, scope.tenantId(), scope.projectId(), scope.scriptId(), currentHash);
    }

    private void refreshCounts(long snapshotId) {
        jdbc.update("""
            update script_split_snapshot
               set completed_chunks = (select count(*) from script_split_chunk
                                         where snapshot_id = ? and status = 'SUCCEEDED'),
                   failed_chunks = (select count(*) from script_split_chunk
                                      where snapshot_id = ? and status = 'FAILED'),
                   status = case
                     when (select count(*) from script_split_chunk
                            where snapshot_id = ? and status = 'SUCCEEDED') = total_chunks
                       then 'SUCCEEDED'
                     when (select count(*) from script_split_chunk
                            where snapshot_id = ? and status = 'FAILED') > 0
                       then 'PARTIAL_FAILED'
                     else 'RUNNING' end,
                   finished_at = case
                     when (select count(*) from script_split_chunk
                            where snapshot_id = ? and status = 'SUCCEEDED') = total_chunks
                       then now() else null end,
                   updated_at = now()
             where id = ?
            """, snapshotId, snapshotId, snapshotId, snapshotId, snapshotId, snapshotId);
    }

    private SplitChunk chunk(
        long id, long snapshotId, int chunkNo, int coreStart, int coreEnd,
        int contextStart, int contextEnd, String contentHash, String status,
        Long aiCallLogId, String candidates
    ) {
        try {
            return new SplitChunk(id, snapshotId, chunkNo, coreStart, coreEnd, contextStart,
                contextEnd, contentHash, status, aiCallLogId,
                candidates == null ? null : json.readTree(candidates));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("分块候选数据损坏。", exception);
        }
    }

    public record SplitScope(long tenantId, long projectId, long scriptId, long parentRunId) {}

    public record SplitChunkSeed(
        int chunkNo, int coreStart, int coreEnd, int contextStart, int contextEnd,
        String contentHash
    ) {}

    public record SplitSnapshot(
        long id, long parentRunId, String contentHash, String mode, String fallbackReason,
        String status, int total, int completed, int failed
    ) {}

    public record SplitChunk(
        long id, long snapshotId, int chunkNo, int coreStart, int coreEnd,
        int contextStart, int contextEnd, String contentHash, String status,
        Long aiCallLogId, JsonNode candidates
    ) {}
}
