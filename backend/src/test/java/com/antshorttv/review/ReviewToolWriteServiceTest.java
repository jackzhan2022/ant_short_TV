package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "review.workflow.features.semantic-review=true",
    "review.workflow.features.anomaly-gate=true"
})
@Transactional
class ReviewToolWriteServiceTest {
    @Autowired private ReviewToolWriteService writes;
    @Autowired private ReviewToolReadService reads;
    @Autowired private ReviewContentService contentService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private ReviewSemanticAuditRepository semanticAudits;
    private final String script = "第1集\n1-1 客厅 日 内\n林夏：你好\n顾言：再见";
    private ReviewContentService.FrozenReview frozen;
    private long modelId;
    private long childRunId;
    private long unitId;

    @BeforeEach
    void seed() {
        frozen = contentService.freeze(script, "ALL", Map.of(), List.of("台词合理性"));
        modelId = jdbc.queryForObject("select id from ai_model order by id limit 1", Long.class);
        jdbc.update("insert into review_project (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at) values (98701, 98700, 'R', 'TXT', ?, 'ACTIVE', 1, now(), now())", script);
        jdbc.update("insert into review_script_version (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at) values (98702, 98700, 98701, 1, 'TXT', ?, 1, now(), now())", script);
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, review_scope_json,
               version_hash, scope_hash, dimensions_hash, status, overall_progress,
               idempotency_key, created_by, created_at, updated_at)
            values (98703, 98700, 98701, 98702, 1, 'DEEP', '["台词合理性"]',
                    'ALL', '{}', ?, ?, ?, 'RUNNING', 20, 'write-98703', 1, now(), now())
            """, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash());
        jdbc.update("""
            insert into review_fanout_snapshot
              (id, tenant_id, project_id, task_id, script_version_id, attempt_no, agent_code,
               agent_revision, skill_revisions_json, model_id, review_mode,
               selected_dimensions_json, review_scope_json, version_hash, scope_hash,
               dimensions_hash, unit_set_hash, status, total_units, completed_units,
               failed_units, max_concurrency, created_at, updated_at)
            values (98704, 98700, 98701, 98703, 98702, 1, 'script-review', 1, '[]', ?,
                    'DEEP', '["台词合理性"]', '{}', ?, ?, ?, 'units', 'RUNNING', 1, 0, 0, 1, now(), now())
            """, modelId, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash());
        jdbc.update("insert into review_fanout_unit (id, snapshot_id, unit_no, unit_key, scope_json, start_offset, end_offset, content_fingerprint, status, attempt_no, candidate_saved, created_at, updated_at) values (98705, 98704, 1, 'offset-0', '{}', 0, ?, ?, 'RUNNING', 1, false, now(), now())",
            script.length(), ReviewContentService.hash(script));
        unitId = 98705;
        childRunId = insertRun("REVIEW_CHILD");
    }

    @Test
    void savesOneValidatedUnitCandidateWithoutFormalIssuesAndReadsCompleteCoverage() throws Exception {
        JsonNode saved = writes.saveUnitResult(childContext(), unitPayload("顾言：再见"));
        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from review_unit_result", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_candidate_audit", Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
            "select status from review_candidate_audit where snapshot_id = 98704", String.class
        )).isEqualTo("VALID");
        assertThat(jdbc.queryForObject("select count(*) from review_issue", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from review_fanout_unit where id = 98705", String.class)).isEqualTo("SUCCEEDED");
        JsonNode units = writes.readUnitResults(aggregationContext(), json.readTree("{\"page\":1,\"pageSize\":50}"));
        assertThat(units.path("units")).hasSize(1);
    }

    @Test
    void preservesOneImmutableSemanticDecisionPerCandidate() throws Exception {
        writes.saveUnitResult(childContext(), unitPayload("顾言：再见"));
        long candidateId = jdbc.queryForObject(
            "select id from review_candidate_audit where snapshot_id = 98704", Long.class
        );
        long qualityRunId = insertRun("REVIEW_SEMANTIC_QUALITY");
        var decision = new ReviewSemanticAuditRepository.DecisionDraft(
            98700L, 98701L, 98703L, 98704L, candidateId, qualityRunId,
            "NEEDS_HUMAN_REVIEW", new BigDecimal("0.6200"), "存在合理替代解释",
            "LOW", json.readTree("[\"episode:1/scene:1-1/offset:4\"]"), "dialogue-1"
        );

        semanticAudits.saveDecision(decision);

        assertThat(jdbc.queryForMap(
            "select decision, confidence, rationale, severity_decision from review_semantic_decision where candidate_id = ?",
            candidateId
        )).containsEntry("DECISION", "NEEDS_HUMAN_REVIEW")
            .containsEntry("RATIONALE", "存在合理替代解释")
            .containsEntry("SEVERITY_DECISION", "LOW");
        JsonNode aggregationInput = writes.readUnitResults(
            aggregationContext(), json.readTree("{\"page\":1,\"pageSize\":50}"));
        assertThat(aggregationInput.path("humanReviewFindings")).hasSize(1);
        assertThat(aggregationInput.path("humanReviewFindings").get(0).path("candidateId").asLong())
            .isEqualTo(candidateId);
        assertThat(aggregationInput.path("humanReviewFindings").get(0).path("rationale").asText())
            .isEqualTo("存在合理替代解释");
        assertThatThrownBy(() -> semanticAudits.saveDecision(decision))
            .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void semanticQualityReadsFrozenCandidatesAndAtomicallySavesOneDecisionEach() throws Exception {
        writes.saveUnitResult(childContext(), unitPayload("顾言：再见"));
        long qualityRunId = insertRun("REVIEW_SEMANTIC_QUALITY");
        ToolExecutionContext quality = qualityContext(qualityRunId);

        JsonNode candidates = writes.readCandidates(
            quality, json.readTree("{\"page\":1,\"pageSize\":50}"));
        long candidateId = candidates.path("candidates").get(0).path("candidateId").asLong();
        reads.readContent(quality, json.createObjectNode().put("offset", 0).put("limit", 50000));
        JsonNode payload = json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","decisions":[
              {"candidateId":%d,"decision":"CONFIRMED","confidence":0.91,
               "rationale":"证据直接支持且无合理替代解释","severityDecision":"LOW",
               "evidenceRefs":["%s"]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                candidateId, frozen.segments().get(0).anchor()));

        JsonNode saved = writes.saveSemanticDecisions(quality, payload);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(saved.path("decisionCount").asInt()).isOne();
        assertThat(jdbc.queryForObject(
            "select decision from review_semantic_decision where candidate_id = ?",
            String.class, candidateId)).isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject(
            "select raw_payload_json from review_candidate_audit where id = ?",
            String.class, candidateId)).contains("告别突兀");
        JsonNode aggregationInput = writes.readUnitResults(
            aggregationContext(), json.readTree("{\"page\":1,\"pageSize\":50}"));
        JsonNode auditedCandidate = aggregationInput.path("units").get(0).path("candidates").get(0);
        assertThat(auditedCandidate.path("candidateId").asLong()).isEqualTo(candidateId);
        assertThat(auditedCandidate.path("semanticDecision").path("decision").asText())
            .isEqualTo("CONFIRMED");
        assertThat(auditedCandidate.path("semanticDecision").path("rationale").asText())
            .isEqualTo("证据直接支持且无合理替代解释");
    }

    @Test
    void preservesAllTerminalSemanticStatusesSeverityAdjustmentAndDuplicateClusters() throws Exception {
        var rawCandidates = json.createArrayNode();
        for (int index = 1; index <= 4; index++) {
            rawCandidates.addObject().put("dimension", "台词合理性").put("title", "候选" + index);
        }
        semanticAudits.appendCandidates(new ReviewSemanticAuditRepository.CandidateBatch(
            98700L, 98701L, 98703L, 98704L, unitId, childRunId, ReviewContentService.hash(script)
        ), rawCandidates);
        List<ReviewSemanticAuditRepository.CandidateRecord> candidates = semanticAudits.candidates(98704L);
        String[] statuses = {"CONFIRMED", "NEEDS_HUMAN_REVIEW", "REJECTED", "INSUFFICIENT_EVIDENCE"};
        for (int index = 0; index < statuses.length; index++) {
            semanticAudits.saveDecision(new ReviewSemanticAuditRepository.DecisionDraft(
                98700L, 98701L, 98703L, 98704L, candidates.get(index).id(),
                insertRun("REVIEW_SEMANTIC_QUALITY"), statuses[index], new BigDecimal("0.7500"),
                "语义裁决" + index, index == 0 ? "MEDIUM" : "LOW",
                json.readTree("[\"episode:1/scene:1-1/offset:4\"]"), index < 2 ? "cluster-a" : null
            ));
        }

        List<ReviewSemanticAuditRepository.DecisionRecord> saved = semanticAudits.decisions(98704L);
        assertThat(saved).extracting(ReviewSemanticAuditRepository.DecisionRecord::decision)
            .containsExactlyInAnyOrder(statuses);
        assertThat(saved).filteredOn(decision -> "CONFIRMED".equals(decision.decision()))
            .extracting(ReviewSemanticAuditRepository.DecisionRecord::severityDecision)
            .containsExactly("MEDIUM");
        assertThat(saved).filteredOn(decision -> "cluster-a".equals(decision.duplicateClusterKey()))
            .hasSize(2);
        assertThat(semanticAudits.candidates(98704L)).extracting(
            ReviewSemanticAuditRepository.CandidateRecord::rawPayloadJson)
            .containsExactlyElementsOf(candidates.stream()
                .map(ReviewSemanticAuditRepository.CandidateRecord::rawPayloadJson).toList());
    }

    @Test
    void rollsBackEverySemanticDecisionWhenOneCandidateDecisionIsMalformed() throws Exception {
        var rawCandidates = json.createArrayNode();
        rawCandidates.addObject().put("dimension", "台词合理性").put("title", "候选A");
        rawCandidates.addObject().put("dimension", "台词合理性").put("title", "候选B");
        semanticAudits.appendCandidates(new ReviewSemanticAuditRepository.CandidateBatch(
            98700L, 98701L, 98703L, 98704L, unitId, childRunId, ReviewContentService.hash(script)
        ), rawCandidates);
        List<ReviewSemanticAuditRepository.CandidateRecord> candidates = semanticAudits.candidates(98704L);
        JsonNode payload = json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","decisions":[
              {"candidateId":%d,"decision":"CONFIRMED","confidence":0.9,
               "rationale":"证据充足","evidenceRefs":["episode:1/scene:1-1/offset:4"]},
              {"candidateId":%d,"decision":"INSUFFICIENT_EVIDENCE","confidence":0.4,
               "rationale":"证据不足","evidenceRefs":[]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                candidates.get(0).id(), candidates.get(1).id()));

        assertThatThrownBy(() -> writes.saveSemanticDecisions(
            qualityContext(insertRun("REVIEW_SEMANTIC_QUALITY")), payload))
            .isInstanceOf(BusinessException.class).hasMessageContaining("证据引用");
        assertThat(semanticAudits.decisionCount(98704L)).isZero();
    }

    @Test
    void deepAggregationRejectsAFormalIssueWhoseSourceCandidateWasRejected() throws Exception {
        writes.saveUnitResult(childContext(), unitPayload("顾言：再见"));
        long candidateId = jdbc.queryForObject(
            "select id from review_candidate_audit where snapshot_id = 98704", Long.class);
        semanticAudits.saveDecision(new ReviewSemanticAuditRepository.DecisionDraft(
            98700L, 98701L, 98703L, 98704L, candidateId,
            insertRun("REVIEW_SEMANTIC_QUALITY"), "REJECTED", new BigDecimal("0.9000"),
            "上下文显示这是正常告别，并非逻辑问题", "LOW",
            json.readTree("[\"%s\"]".formatted(frozen.segments().get(0).anchor())), null));
        JsonNode formal = formalPayload();
        ((com.fasterxml.jackson.databind.node.ObjectNode) formal.path("issues").get(0))
            .set("sourceCandidateIds", json.createArrayNode().add(candidateId));

        assertThatThrownBy(() -> writes.saveResult(aggregationContext(), formal))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("未确认");
        assertThat(jdbc.queryForObject("select count(*) from review_issue", Integer.class)).isZero();
    }

    @Test
    void zeroCandidateSemanticSaveRequiresAnExplicitAnomalyReview() throws Exception {
        writes.saveUnitResult(childContext(), emptyUnitPayload());
        ToolExecutionContext quality = qualityContext(insertRun("REVIEW_SEMANTIC_QUALITY"));
        writes.readCandidates(quality, json.readTree("{\"page\":1,\"pageSize\":50}"));
        reads.readContent(quality, json.createObjectNode().put("offset", 0).put("limit", 50000));
        JsonNode withoutGate = json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","decisions":[]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash()));

        assertThatThrownBy(() -> writes.saveSemanticDecisions(quality, withoutGate))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("零问题异常复核");
        assertThat(jdbc.queryForObject(
            "select count(*) from review_semantic_decision", Integer.class)).isZero();
    }

    @Test
    void failedZeroProblemAnomalyReviewBlocksAFullScoreFormalReport() throws Exception {
        writes.saveUnitResult(childContext(), emptyUnitPayload());
        jdbc.update("""
            insert into review_pipeline_stage
              (tenant_id, project_id, task_id, snapshot_id, stage_key, stage_type, status,
               attempt_no, version_hash, scope_hash, dimensions_hash, input_hash, created_at, updated_at)
            values (98700, 98701, 98703, 98704, 'semantic-quality', 'SEMANTIC_QUALITY',
                    'RUNNING', 1, ?, ?, ?, ?, now(), now())
            """, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(), "0".repeat(64));
        ToolExecutionContext quality = qualityContext(insertRun("REVIEW_SEMANTIC_QUALITY"));
        JsonNode qualityPayload = json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","decisions":[],
             "anomalyReview":{"passed":false,"rationale":"覆盖不足，不能确认无问题",
                              "evidenceRefs":["coverage:incomplete"]}}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash()));
        writes.saveSemanticDecisions(quality, qualityPayload);
        JsonNode formal = emptyFormalPayload();
        ((com.fasterxml.jackson.databind.node.ObjectNode) formal).put("score", 100);

        assertThatThrownBy(() -> writes.saveResult(aggregationContext(), formal))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("异常复核未通过");
        assertThat(jdbc.queryForObject(
            "select status from review_task where id=98703", String.class)).isEqualTo("RUNNING");
    }

    @Test
    void rejectsAnUnverifiableCandidateInsteadOfSilentlySavingAnEmptyUnitResult() throws Exception {
        assertThatThrownBy(() -> writes.saveUnitResult(childContext(), unitPayload("正文中不存在")))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("证据无法在当前范围正文中验证");

        assertThat(jdbc.queryForObject("select count(*) from review_unit_result", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select candidate_saved from review_fanout_unit where id = 98705", Boolean.class)).isFalse();
    }

    @Test
    void savesUnitCandidateWithServerExtractedEvidenceFromOffsets() throws Exception {
        JsonNode saved = writes.saveUnitResult(childContext(), unitPayloadWithOffsets());

        assertThat(saved.path("saved").asBoolean()).isTrue();
        String candidates = jdbc.queryForObject(
            "select candidates_json from review_unit_result where snapshot_id = 98704", String.class);
        JsonNode candidate = json.readTree(candidates).get(0);
        assertThat(candidate.path("evidence")).extracting(JsonNode::asText).containsExactly("顾言：再见");
        assertThat(candidate.path("hits").get(0).path("excerpt").asText()).isEqualTo("顾言：再见");
    }

    @Test
    void savesFormalQuickResultAtomicallyWithServerIdentityAndVerifiedHit() throws Exception {
        jdbc.update("update review_task set review_mode = 'QUICK' where id = 98703");
        long quickRun = insertRun("REVIEW_QUICK");
        JsonNode result = writes.saveResult(quickContext(quickRun), formalPayload());
        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select status from review_task where id = 98703", String.class)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select issue_no from review_issue where task_id = 98703", String.class)).isEqualTo("R1-01");
        assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where task_id = 98703", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_event where task_id = 98703", Integer.class)).isOne();
    }

    @Test
    void replacesStaleFormalIssuesWhenSavingTheSameTaskAgain() throws Exception {
        jdbc.update("insert into review_issue (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, status, manually_resolved, created_at, updated_at) values (98700,98701,98703,98702,1,'R1-01','台词合理性','LOW','过期问题','new',false,now(),now())");
        jdbc.update("update review_task set review_mode = 'QUICK' where id = 98703");

        JsonNode result = writes.saveResult(quickContext(insertRun("REVIEW_QUICK")), formalPayload());

        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id = 98703", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select title from review_issue where task_id = 98703", String.class)).isEqualTo("告别突兀");
        assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where task_id = 98703", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_event where task_id = 98703", Integer.class)).isOne();
    }

    @Test
    void rejectsStaleHashesUnselectedDimensionInvalidSeverityAndDuplicateIdentity() throws Exception {
        jdbc.update("update review_task set review_mode = 'QUICK' where id = 98703");
        long quickRun = insertRun("REVIEW_QUICK");
        JsonNode stale = formalPayload();
        ((com.fasterxml.jackson.databind.node.ObjectNode) stale).put("versionHash", "0".repeat(64));
        assertThatThrownBy(() -> writes.saveResult(quickContext(quickRun), stale))
            .isInstanceOf(BusinessException.class).hasMessageContaining("变化");
        assertThat(jdbc.queryForObject("select count(*) from review_issue", Integer.class)).isZero();
    }

    @Test
    void keepsTheExistingSnapshotWhenFormalResultValidationFails() throws Exception {
        jdbc.update("insert into review_issue (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, status, manually_resolved, created_at, updated_at) values (98700,98701,98703,98702,1,'R1-01','台词合理性','LOW','保留问题','new',false,now(),now())");
        jdbc.update("update review_task set review_mode = 'QUICK' where id = 98703");
        com.fasterxml.jackson.databind.node.ObjectNode stale = (com.fasterxml.jackson.databind.node.ObjectNode) formalPayload();
        stale.put("versionHash", "0".repeat(64));

        assertThatThrownBy(() -> writes.saveResult(quickContext(insertRun("REVIEW_QUICK")), stale))
            .isInstanceOf(BusinessException.class).hasMessageContaining("变化");
        assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id = 98703", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("select title from review_issue where task_id = 98703", String.class)).isEqualTo("保留问题");
    }

    @Test
    void savesAnEmptyCurrentReportWithoutCopyingOrChangingHistoricalIssues() throws Exception {
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, review_scope_json,
               version_hash, scope_hash, dimensions_hash, status, overall_progress,
               idempotency_key, created_by, created_at, updated_at)
            values (98706, 98700, 98701, 98702, 1, 'QUICK', '["台词合理性"]',
                    'ALL', '{}', ?, ?, ?, 'COMPLETED', 100, 'write-98706', 1, now(), now())
            """, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash());
        jdbc.update("""
            insert into review_issue
              (tenant_id, project_id, task_id, script_version_id, round_no, issue_no,
               dimension, severity, title, position_json, excerpt, problem, evidence_json,
               suggestion, status, manually_resolved, created_at, updated_at)
            values (98700, 98701, 98706, 98702, 1, 'R1-01', '台词合理性', 'LOW',
                    '告别突兀', '{}', '顾言：再见', '缺少回应', '["顾言：再见"]',
                    '补反应', 'new', false, now(), now())
            """);
        jdbc.update("update review_task set round_no = 2, review_mode = 'QUICK' where id = 98703");

        writes.saveResult(quickContext(insertRun("REVIEW_QUICK")), emptyFormalPayload());

        assertThat(jdbc.queryForObject(
            "select count(*) from review_issue where task_id = 98703", Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
            "select status from review_issue where task_id = 98706", String.class)).isEqualTo("new");
        assertThat(jdbc.queryForObject(
            "select count(*) from review_issue_event where task_id = 98703", Integer.class)).isZero();
    }

    @Test
    void historicalIssuesDoNotTriggerAnomalyReviewForCurrentCandidates() throws Exception {
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, review_scope_json,
               version_hash, scope_hash, dimensions_hash, status, overall_progress,
               idempotency_key, created_by, created_at, updated_at)
            values (98706, 98700, 98701, 98702, 1, 'DEEP', '["台词合理性"]',
                    'ALL', '{}', ?, ?, ?, 'COMPLETED', 100, 'history-98706', 1, now(), now())
            """, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash());
        jdbc.update("""
            insert into review_issue
              (tenant_id, project_id, task_id, script_version_id, round_no, issue_no,
               dimension, severity, title, status, manually_resolved, created_at, updated_at)
            values (98700, 98701, 98706, 98702, 1, 'R1-01', '台词合理性', 'LOW',
                    '历史问题', 'new', false, now(), now())
            """);
        writes.saveUnitResult(childContext(), unitPayload("顾言：再见"));
        long candidateId = jdbc.queryForObject(
            "select id from review_candidate_audit where snapshot_id = 98704", Long.class);
        ToolExecutionContext quality = qualityContext(insertRun("REVIEW_SEMANTIC_QUALITY"));
        writes.readCandidates(quality, json.readTree("{\"page\":1,\"pageSize\":50}"));
        reads.readContent(quality, json.createObjectNode().put("offset", 0).put("limit", 50000));
        JsonNode payload = json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","decisions":[
              {"candidateId":%d,"decision":"CONFIRMED","confidence":0.91,
               "rationale":"当前证据充足","severityDecision":"LOW",
               "evidenceRefs":["%s"]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                candidateId, frozen.segments().get(0).anchor()));

        JsonNode saved = writes.saveSemanticDecisions(quality, payload);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(semanticAudits.decisionCount(98704L)).isOne();
    }

    private JsonNode unitPayload(String excerpt) throws Exception {
        return json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s",
             "contentFingerprint":"%s","coverage":{"complete":true,"anchors":["%s"]},
             "candidates":[{"dimension":"台词合理性","severity":"LOW","title":"告别突兀",
               "problem":"缺少回应","evidence":["%s"],"suggestion":"补反应",
               "hits":[{"anchor":"%s","excerpt":"%s"}]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                ReviewContentService.hash(script), frozen.segments().get(0).anchor(), excerpt,
                frozen.segments().get(0).anchor(), excerpt));
    }

    private JsonNode unitPayloadWithOffsets() throws Exception {
        int start = script.indexOf("顾言：再见");
        int end = start + "顾言：再见".length();
        return json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s",
             "contentFingerprint":"%s","coverage":{"complete":true,"anchors":["%s"]},
             "candidates":[{"dimension":"台词合理性","severity":"LOW","title":"告别突兀",
               "problem":"缺少回应","evidence":["模型改写的说明"],"suggestion":"补反应",
               "hits":[{"anchor":"%s","excerpt":"模型改写","startOffset":%d,"endOffset":%d}]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                ReviewContentService.hash(script), frozen.segments().get(0).anchor(),
                frozen.segments().get(0).anchor(), start, end));
    }

    private JsonNode formalPayload() throws Exception {
        return json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","score":82,
             "conclusion":"整体可用","coverage":{"complete":true,"anchors":["episode:1/scene:1-1/offset:4"]},
             "issues":[{"dimension":"台词合理性","severity":"LOW","title":"告别突兀",
               "problem":"缺少回应","evidence":["顾言：再见"],"suggestion":"补反应",
               "hits":[{"anchor":"episode:1/scene:1-1/offset:4","excerpt":"顾言：再见"}]}]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash()));
    }

    private JsonNode emptyUnitPayload() throws Exception {
        return json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s",
             "contentFingerprint":"%s","coverage":{"complete":true,"anchors":["%s"]},
             "candidates":[]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
                ReviewContentService.hash(script), frozen.segments().get(0).anchor()));
    }

    private JsonNode emptyFormalPayload() throws Exception {
        return json.readTree("""
            {"versionHash":"%s","scopeHash":"%s","dimensionsHash":"%s","score":82,
             "conclusion":"未发现新增问题","coverage":{"complete":true,"anchors":[]},"issues":[]}
            """.formatted(frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash()));
    }

    private ToolExecutionContext childContext() {
        ToolExecutionContext context = context(childRunId, new ReviewToolScope(98701L, 98702L, 98704L, unitId, 1,
            "DEEP_CHILD", List.of("台词合理性")));
        reads.readContent(context, json.createObjectNode().put("offset", 0).put("limit", 50000));
        return context;
    }

    private ToolExecutionContext aggregationContext() {
        return context(insertRun("REVIEW_AGGREGATION"), new ReviewToolScope(98701L, 98702L, 98704L,
            null, 1, "DEEP_AGGREGATION", List.of("台词合理性")));
    }

    private ToolExecutionContext qualityContext(long runId) {
        return context(runId, new ReviewToolScope(98701L, 98702L, 98704L,
            null, 1, "DEEP_SEMANTIC", List.of("台词合理性")));
    }

    private ToolExecutionContext quickContext(long runId) {
        ToolExecutionContext context = context(runId, new ReviewToolScope(98701L, 98702L, null, null, 1,
            "QUICK", List.of("台词合理性")));
        reads.readContent(context, json.createObjectNode().put("offset", 0).put("limit", 50000));
        return context;
    }

    private ToolExecutionContext context(long runId, ReviewToolScope scope) {
        return new ToolExecutionContext(98700L, 1L, null, null, null, 98703L, null, runId,
            null, null, null, Set.of(), null, new WorkflowToolRunState(), scope);
    }

    private long insertRun(String runType) {
        jdbc.update("insert into ai_workflow_agent_run (agent_code, run_type, tenant_id, user_id, task_id, status, model_id, temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at) values ('script-review', ?, 98700, 1, 98703, 'RUNNING', ?, 0.1, 4096, 20, '', now(), now())", runType, modelId);
        return jdbc.queryForObject("select max(id) from ai_workflow_agent_run", Long.class);
    }
}
