package com.antshorttv.review;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReviewSemanticAuditRepository {
    private static final Set<String> DECISIONS = Set.of(
        "CONFIRMED", "NEEDS_HUMAN_REVIEW", "REJECTED", "INSUFFICIENT_EVIDENCE"
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ReviewSemanticAuditRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void appendCandidates(CandidateBatch batch, JsonNode candidates) {
        int candidateNo = 1;
        for (JsonNode candidate : candidates) {
            String raw = stringify(candidate);
            String key = ReviewContentService.hash(raw);
            try {
                jdbc.update("""
                    insert into review_candidate_audit
                      (tenant_id, project_id, task_id, snapshot_id, unit_id, discovery_run_id,
                       candidate_key, dimension, candidate_no, status, raw_payload_json,
                       source_fingerprint, created_at)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALID', ?, ?, now())
                    """, batch.tenantId(), batch.projectId(), batch.taskId(), batch.snapshotId(),
                    batch.unitId(), batch.discoveryRunId(), key, candidate.path("dimension").asText(),
                    candidateNo++, raw, batch.sourceFingerprint());
            } catch (DuplicateKeyException duplicate) {
                // Identical retry output is already durably audited.
            }
        }
    }

    public List<CandidateRecord> candidates(long snapshotId) {
        return jdbc.query("""
            select id, unit_id, discovery_run_id, candidate_key, dimension, candidate_no,
                   status, raw_payload_json, validation_errors_json, source_fingerprint
              from review_candidate_audit where snapshot_id = ? order by unit_id, candidate_no, id
            """, (row, index) -> new CandidateRecord(
                row.getLong("id"), row.getLong("unit_id"), row.getLong("discovery_run_id"),
                row.getString("candidate_key"), row.getString("dimension"), row.getInt("candidate_no"),
                row.getString("status"), row.getString("raw_payload_json"),
                row.getString("validation_errors_json"), row.getString("source_fingerprint")
            ), snapshotId);
    }

    public int decisionCount(long snapshotId) {
        Integer count = jdbc.queryForObject(
            "select count(*) from review_semantic_decision where snapshot_id = ?",
            Integer.class, snapshotId);
        return count == null ? 0 : count;
    }

    public List<DecisionRecord> decisions(long snapshotId) {
        return jdbc.query("""
            select candidate_id, decision, confidence, rationale, severity_decision,
                   evidence_refs_json, duplicate_cluster_key, quality_run_id
              from review_semantic_decision
             where snapshot_id = ? order by candidate_id
            """, (row, index) -> new DecisionRecord(
                row.getLong("candidate_id"), row.getString("decision"),
                row.getBigDecimal("confidence"), row.getString("rationale"),
                row.getString("severity_decision"), row.getString("evidence_refs_json"),
                row.getString("duplicate_cluster_key"), row.getLong("quality_run_id")
            ), snapshotId);
    }

    public void recordQualityCoverage(long snapshotId, long qualityRunId, JsonNode coverage) {
        jdbc.update("""
            update review_pipeline_stage
               set run_id=?, coverage_json=?, updated_at=now()
             where snapshot_id=? and stage_key='semantic-quality'
            """, qualityRunId, stringify(coverage), snapshotId);
    }

    public JsonNode qualityCoverage(long snapshotId) {
        List<String> rows = jdbc.queryForList("""
            select coverage_json from review_pipeline_stage
             where snapshot_id=? and stage_key='semantic-quality'
            """, String.class, snapshotId);
        if (rows.isEmpty() || rows.get(0) == null || rows.get(0).isBlank()) return null;
        try {
            return json.readTree(rows.get(0));
        } catch (Exception exception) {
            throw invalid("语义质检覆盖记录损坏。");
        }
    }

    public void saveDecision(DecisionDraft draft) {
        validateDecision(draft);
        String decision = draft.decision().trim().toUpperCase();
        jdbc.update("""
            insert into review_semantic_decision
              (tenant_id, project_id, task_id, snapshot_id, candidate_id, quality_run_id,
               decision, confidence, rationale, severity_decision, evidence_refs_json,
               duplicate_cluster_key, created_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """, draft.tenantId(), draft.projectId(), draft.taskId(), draft.snapshotId(),
            draft.candidateId(), draft.qualityRunId(), decision, draft.confidence(), draft.rationale().trim(),
            draft.severityDecision(), stringify(draft.evidenceRefs()), draft.duplicateClusterKey());
    }

    public void saveDecisionIdempotent(DecisionDraft draft) {
        try {
            saveDecision(draft);
        } catch (DuplicateKeyException duplicate) {
            DecisionRecord existing = decisions(draft.snapshotId()).stream()
                .filter(decision -> decision.candidateId() == draft.candidateId())
                .findFirst().orElseThrow(() -> duplicate);
            String expectedDecision = draft.decision().trim().toUpperCase();
            String expectedEvidence = stringify(draft.evidenceRefs());
            if (!expectedDecision.equals(existing.decision())
                || draft.confidence().compareTo(existing.confidence()) != 0
                || !draft.rationale().trim().equals(existing.rationale())
                || !java.util.Objects.equals(draft.severityDecision(), existing.severityDecision())
                || !expectedEvidence.equals(existing.evidenceRefsJson())
                || !java.util.Objects.equals(draft.duplicateClusterKey(), existing.duplicateClusterKey())) {
                throw invalid("同一候选已存在不同的语义裁决，不能覆盖。");
            }
        }
    }

    public void validateDecision(DecisionDraft draft) {
        String decision = draft.decision() == null ? "" : draft.decision().trim().toUpperCase();
        if (!DECISIONS.contains(decision)) throw invalid("语义质检裁决状态无效。");
        if (draft.confidence() == null || draft.confidence().compareTo(BigDecimal.ZERO) < 0
            || draft.confidence().compareTo(BigDecimal.ONE) > 0) throw invalid("语义质检置信度必须在 0 到 1 之间。");
        if (draft.rationale() == null || draft.rationale().isBlank()) throw invalid("语义质检必须提供裁决理由。");
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw invalid("审核语义审计数据无法序列化。");
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    public record CandidateBatch(
        long tenantId, long projectId, long taskId, long snapshotId, long unitId,
        long discoveryRunId, String sourceFingerprint
    ) {}

    public record CandidateRecord(
        long id, long unitId, long discoveryRunId, String candidateKey, String dimension,
        int candidateNo, String status, String rawPayloadJson, String validationErrorsJson,
        String sourceFingerprint
    ) {}

    public record DecisionDraft(
        long tenantId, long projectId, long taskId, long snapshotId, long candidateId,
        long qualityRunId, String decision, BigDecimal confidence, String rationale,
        String severityDecision, JsonNode evidenceRefs, String duplicateClusterKey
    ) {}

    public record DecisionRecord(
        long candidateId, String decision, BigDecimal confidence, String rationale,
        String severityDecision, String evidenceRefsJson, String duplicateClusterKey,
        long qualityRunId
    ) {}
}
