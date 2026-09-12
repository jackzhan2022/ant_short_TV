package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.List;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ReviewFanoutRepository {
    private final JdbcTemplate jdbc;

    public ReviewFanoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long openSnapshot(SnapshotDraft draft) {
        List<Long> existing = jdbc.queryForList(
            "select id from review_fanout_snapshot where task_id = ? and attempt_no = ?",
            Long.class, draft.taskId(), draft.attemptNo());
        if (!existing.isEmpty()) return existing.get(0);
        KeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                    insert into review_fanout_snapshot
                      (tenant_id, project_id, task_id, script_version_id, attempt_no,
                       agent_code, agent_revision, skill_revisions_json, model_id, review_mode,
                       selected_dimensions_json, review_scope_json, version_hash, scope_hash,
                       dimensions_hash, unit_set_hash, status, total_units, completed_units,
                       failed_units, max_concurrency, created_at, updated_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'DEEP', ?, ?, ?, ?, ?, ?,
                            'PENDING', ?, 0, 0, ?, now(), now())
                    """, Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, draft.tenantId());
                statement.setLong(2, draft.projectId());
                statement.setLong(3, draft.taskId());
                statement.setLong(4, draft.versionId());
                statement.setInt(5, draft.attemptNo());
                statement.setString(6, draft.agentCode());
                statement.setLong(7, draft.agentRevision());
                statement.setString(8, draft.skillRevisionsJson());
                statement.setLong(9, draft.modelId());
                statement.setString(10, draft.dimensionsJson());
                statement.setString(11, draft.scopeJson());
                statement.setString(12, draft.versionHash());
                statement.setString(13, draft.scopeHash());
                statement.setString(14, draft.dimensionsHash());
                statement.setString(15, draft.unitSetHash());
                statement.setInt(16, draft.totalUnits());
                statement.setInt(17, draft.maxConcurrency());
                return statement;
            }, keys);
            return keys.getKey().longValue();
        } catch (DuplicateKeyException duplicate) {
            return jdbc.queryForObject(
                "select id from review_fanout_snapshot where task_id = ? and attempt_no = ?",
                Long.class, draft.taskId(), draft.attemptNo());
        }
    }

    public long addUnit(UnitDraft draft) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into review_fanout_unit
                  (snapshot_id, unit_no, unit_key, stage_type, dimension, scope_json, start_offset, end_offset,
                   content_fingerprint, status, attempt_no, report_saved, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, false, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, draft.snapshotId());
            statement.setInt(2, draft.unitNo());
            statement.setString(3, draft.unitKey());
            statement.setString(4, draft.stageType());
            statement.setString(5, draft.dimension());
            statement.setString(6, draft.scopeJson());
            statement.setInt(7, draft.startOffset());
            statement.setInt(8, draft.endOffset());
            statement.setString(9, draft.fingerprint());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    @Transactional
    public void replaceMarkdownFragment(MarkdownFragmentDraft draft) {
        upsertMarkdownFragment(draft);
        int completed = jdbc.update("""
            update review_fanout_unit
               set status='SUCCEEDED', report_saved=true, child_run_id=?, error_code=null,
                   error_message=null, completed_at=now(), updated_at=now()
             where id=? and snapshot_id=? and status='RUNNING'
               and exists (
                   select 1 from review_fanout_snapshot snapshot
                   join review_task task on task.id=snapshot.task_id
                   where snapshot.id=? and task.status<>'CANCELED'
               )
            """, draft.childRunId(), draft.unitId(), draft.snapshotId(), draft.snapshotId());
        if (completed == 0) {
            throw new BusinessException(ErrorCode.AI_EXECUTION_STATUS_INVALID, "审核任务已取消或单元已失效。");
        }
    }

    @Transactional
    public void retainPartialMarkdownFragment(MarkdownFragmentDraft draft) {
        upsertMarkdownFragment(draft);
    }

    private void upsertMarkdownFragment(MarkdownFragmentDraft draft) {
        int updated = jdbc.update("""
            update review_unit_result
               set child_run_id = ?, attempt_no = ?, version_hash = ?, scope_hash = ?,
                   dimensions_hash = ?, content_fingerprint = ?, report_markdown = ?, payload_hash = ?, updated_at = now()
             where snapshot_id = ? and unit_id = ?
            """, draft.childRunId(), draft.attemptNo(), draft.versionHash(), draft.scopeHash(),
            draft.dimensionsHash(), draft.fingerprint(), draft.reportMarkdown(), draft.payloadHash(),
            draft.snapshotId(), draft.unitId());
        if (updated == 0) {
            jdbc.update("""
                insert into review_unit_result
                  (snapshot_id, unit_id, child_run_id, attempt_no, version_hash, scope_hash,
                   dimensions_hash, content_fingerprint,
                   report_markdown, payload_hash, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, draft.snapshotId(), draft.unitId(), draft.childRunId(), draft.attemptNo(),
                draft.versionHash(), draft.scopeHash(), draft.dimensionsHash(), draft.fingerprint(),
                draft.reportMarkdown(), draft.payloadHash());
        }
    }

    public List<MarkdownFragment> orderedMarkdownFragments(long snapshotId) {
        return jdbc.query("""
            select unit.id unit_id, unit.unit_no, unit.unit_key, unit.dimension, result.report_markdown
              from review_fanout_unit unit
              join review_unit_result result on result.unit_id=unit.id and result.snapshot_id=unit.snapshot_id
             where unit.snapshot_id=? and unit.status='SUCCEEDED' and result.report_markdown is not null
             order by unit.unit_no, unit.id
            """, (row, i) -> new MarkdownFragment(row.getLong("unit_id"), row.getInt("unit_no"),
                row.getString("unit_key"), row.getString("dimension"), row.getString("report_markdown")),
            snapshotId);
    }

    public List<ReviewFanoutUnitEntity> orderedUnits(long snapshotId) {
        return jdbc.query("""
            select * from review_fanout_unit where snapshot_id = ? order by unit_no, id
            """, (row, i) -> {
                ReviewFanoutUnitEntity unit = new ReviewFanoutUnitEntity();
                unit.setId(row.getLong("id"));
                unit.setSnapshotId(row.getLong("snapshot_id"));
                unit.setUnitNo(row.getInt("unit_no"));
                unit.setUnitKey(row.getString("unit_key"));
                unit.setStageType(row.getString("stage_type"));
                unit.setDimension(row.getString("dimension"));
                unit.setScopeJson(row.getString("scope_json"));
                unit.setStartOffset(row.getInt("start_offset"));
                unit.setEndOffset(row.getInt("end_offset"));
                unit.setContentFingerprint(row.getString("content_fingerprint"));
                unit.setStatus(row.getString("status"));
                unit.setReportSaved(row.getBoolean("report_saved"));
                return unit;
            }, snapshotId);
    }

    public Long findMatchingSnapshot(long taskId, String versionHash, String scopeHash, String dimensionsHash,
        String unitSetHash) {
        List<Long> ids = jdbc.queryForList("""
            select id from review_fanout_snapshot
             where task_id = ? and version_hash = ? and scope_hash = ? and dimensions_hash = ?
               and unit_set_hash = ?
               and status not in ('STALE', 'CANCELED')
             order by attempt_no desc limit 1
            """, Long.class, taskId, versionHash, scopeHash, dimensionsHash, unitSetHash);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public record SnapshotDraft(
        long tenantId, long projectId, long taskId, long versionId, int attemptNo,
        String agentCode, long agentRevision, String skillRevisionsJson, long modelId,
        String dimensionsJson, String scopeJson, String versionHash, String scopeHash,
        String dimensionsHash, String unitSetHash, int totalUnits, int maxConcurrency
    ) {}

    public record UnitDraft(
        long snapshotId, int unitNo, String unitKey, String stageType, String dimension, String scopeJson,
        int startOffset, int endOffset, String fingerprint
    ) {
        public UnitDraft(long snapshotId, int unitNo, String unitKey, String scopeJson,
            int startOffset, int endOffset, String fingerprint) {
            this(snapshotId, unitNo, unitKey, "DIMENSION_MARKDOWN", null, scopeJson,
                startOffset, endOffset, fingerprint);
        }
    }

    public record MarkdownFragmentDraft(
        long snapshotId, long unitId, long childRunId, int attemptNo,
        String versionHash, String scopeHash, String dimensionsHash, String fingerprint,
        String reportMarkdown, String payloadHash
    ) {}

    public record MarkdownFragment(
        long unitId, int unitNo, String unitKey, String dimension, String reportMarkdown
    ) {}
}
