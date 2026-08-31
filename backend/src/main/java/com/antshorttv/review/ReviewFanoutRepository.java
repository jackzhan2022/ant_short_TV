package com.antshorttv.review;

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
                  (snapshot_id, unit_no, unit_key, scope_json, start_offset, end_offset,
                   content_fingerprint, status, attempt_no, candidate_saved, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, false, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, draft.snapshotId());
            statement.setInt(2, draft.unitNo());
            statement.setString(3, draft.unitKey());
            statement.setString(4, draft.scopeJson());
            statement.setInt(5, draft.startOffset());
            statement.setInt(6, draft.endOffset());
            statement.setString(7, draft.fingerprint());
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    @Transactional
    public void replaceCandidate(CandidateDraft draft) {
        int updated = jdbc.update("""
            update review_unit_result
               set child_run_id = ?, attempt_no = ?, version_hash = ?, scope_hash = ?,
                   dimensions_hash = ?, content_fingerprint = ?, coverage_json = ?,
                   candidates_json = ?, payload_hash = ?, updated_at = now()
             where snapshot_id = ? and unit_id = ?
            """, draft.childRunId(), draft.attemptNo(), draft.versionHash(), draft.scopeHash(),
            draft.dimensionsHash(), draft.fingerprint(), draft.coverageJson(), draft.candidatesJson(),
            draft.payloadHash(), draft.snapshotId(), draft.unitId());
        if (updated == 0) {
            jdbc.update("""
                insert into review_unit_result
                  (snapshot_id, unit_id, child_run_id, attempt_no, version_hash, scope_hash,
                   dimensions_hash, content_fingerprint, coverage_json, candidates_json,
                   payload_hash, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
                """, draft.snapshotId(), draft.unitId(), draft.childRunId(), draft.attemptNo(),
                draft.versionHash(), draft.scopeHash(), draft.dimensionsHash(), draft.fingerprint(),
                draft.coverageJson(), draft.candidatesJson(), draft.payloadHash());
        }
        jdbc.update("""
            update review_fanout_unit
               set candidate_saved = true, child_run_id = ?, updated_at = now()
             where id = ? and snapshot_id = ?
            """, draft.childRunId(), draft.unitId(), draft.snapshotId());
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
                unit.setScopeJson(row.getString("scope_json"));
                unit.setStartOffset(row.getInt("start_offset"));
                unit.setEndOffset(row.getInt("end_offset"));
                unit.setContentFingerprint(row.getString("content_fingerprint"));
                unit.setStatus(row.getString("status"));
                unit.setCandidateSaved(row.getBoolean("candidate_saved"));
                return unit;
            }, snapshotId);
    }

    public ReviewUnitResultEntity currentCandidate(long snapshotId, long unitId) {
        return jdbc.queryForObject("""
            select * from review_unit_result where snapshot_id = ? and unit_id = ?
            """, (row, i) -> {
                ReviewUnitResultEntity result = new ReviewUnitResultEntity();
                result.setId(row.getLong("id"));
                result.setSnapshotId(row.getLong("snapshot_id"));
                result.setUnitId(row.getLong("unit_id"));
                result.setChildRunId(row.getLong("child_run_id"));
                result.setAttemptNo(row.getInt("attempt_no"));
                result.setVersionHash(row.getString("version_hash"));
                result.setScopeHash(row.getString("scope_hash"));
                result.setDimensionsHash(row.getString("dimensions_hash"));
                result.setContentFingerprint(row.getString("content_fingerprint"));
                result.setCoverageJson(row.getString("coverage_json"));
                result.setCandidatesJson(row.getString("candidates_json"));
                result.setPayloadHash(row.getString("payload_hash"));
                return result;
            }, snapshotId, unitId);
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

    public boolean transitionUnit(long unitId, ReviewUnitStatus expected, ReviewUnitStatus next) {
        return jdbc.update("""
            update review_fanout_unit set status = ?, updated_at = now()
             where id = ? and status = ?
            """, next.name(), unitId, expected.name()) == 1;
    }

    public record SnapshotDraft(
        long tenantId, long projectId, long taskId, long versionId, int attemptNo,
        String agentCode, long agentRevision, String skillRevisionsJson, long modelId,
        String dimensionsJson, String scopeJson, String versionHash, String scopeHash,
        String dimensionsHash, String unitSetHash, int totalUnits, int maxConcurrency
    ) {}

    public record UnitDraft(
        long snapshotId, int unitNo, String unitKey, String scopeJson,
        int startOffset, int endOffset, String fingerprint
    ) {}

    public record CandidateDraft(
        long snapshotId, long unitId, long childRunId, int attemptNo,
        String versionHash, String scopeHash, String dimensionsHash, String fingerprint,
        String coverageJson, String candidatesJson, String payloadHash
    ) {}
}
