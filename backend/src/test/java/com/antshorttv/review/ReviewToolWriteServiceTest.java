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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@SpringBootTest
@Transactional
class ReviewToolWriteServiceTest {
    @Autowired private ReviewToolWriteService writes;
    @Autowired private ReviewToolReadService reads;
    @Autowired private ReviewContentService contentService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
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
        assertThat(jdbc.queryForObject("select count(*) from review_issue", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select status from review_fanout_unit where id = 98705", String.class)).isEqualTo("SUCCEEDED");
        JsonNode units = writes.readUnitResults(aggregationContext(), json.readTree("{\"page\":1,\"pageSize\":50}"));
        assertThat(units.path("units")).hasSize(1);
    }

    @Test
    void discardsAnUnverifiableLegacyCandidateAndSavesTheRemainingUnitResult() throws Exception {
        JsonNode saved = writes.saveUnitResult(childContext(), unitPayload("正文中不存在"));

        assertThat(saved.path("saved").asBoolean()).isTrue();
        String candidates = jdbc.queryForObject(
            "select candidates_json from review_unit_result where snapshot_id = 98704", String.class);
        assertThat(json.readTree(candidates)).isEmpty();
        assertThat(jdbc.queryForObject("select candidate_saved from review_fanout_unit where id = 98705", Boolean.class)).isTrue();
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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackFormalIssueHitEventAndTaskWhenPersistenceFailsMidTransaction() throws Exception {
        try {
            jdbc.update("insert into review_issue (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, status, manually_resolved, created_at, updated_at) values (98700,98701,98703,98702,1,'R1-02','台词合理性','LOW','冲突占位','new',false,now(),now())");
            jdbc.update("update review_task set review_mode = 'QUICK' where id = 98703");
            long run = insertRun("REVIEW_QUICK");
            com.fasterxml.jackson.databind.node.ObjectNode payload = (com.fasterxml.jackson.databind.node.ObjectNode) formalPayload();
            com.fasterxml.jackson.databind.node.ArrayNode issueList = (com.fasterxml.jackson.databind.node.ArrayNode) payload.path("issues");
            com.fasterxml.jackson.databind.node.ObjectNode second = issueList.get(0).deepCopy();
            second.put("title", "另一个问题");
            second.put("problem", "另一个说明");
            issueList.add(second);

            assertThatThrownBy(() -> writes.saveResult(quickContext(run), payload)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id=98703", Integer.class)).isOne();
            assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where task_id=98703", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from review_issue_event where task_id=98703", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("select status from review_task where id=98703", String.class)).isEqualTo("RUNNING");
        } finally {
            jdbc.update("delete from review_issue_event where task_id=98703");
            jdbc.update("delete from review_issue_hit where task_id=98703");
            jdbc.update("delete from review_issue where task_id=98703");
            jdbc.update("delete from review_unit_result where snapshot_id=98704");
            jdbc.update("delete from review_fanout_unit where snapshot_id=98704");
            jdbc.update("delete from review_fanout_snapshot where id=98704");
            jdbc.update("delete from ai_workflow_agent_run where task_id=98703");
            jdbc.update("delete from review_task where id=98703");
            jdbc.update("delete from review_script_version where id=98702");
            jdbc.update("delete from review_project where id=98701");
        }
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
