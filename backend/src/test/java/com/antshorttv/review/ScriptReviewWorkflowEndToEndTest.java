package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import com.antshorttv.workflowagent.agent.WorkflowAgentCommand;
import com.antshorttv.workflowagent.agent.WorkflowAgentRepository;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "ai.workflow-agent.skill-root=skills")
@Transactional
class ScriptReviewWorkflowEndToEndTest {
    @Autowired private ReviewAgentExecutionPlanFactory plans;
    @Autowired private WorkflowAgentRepository agents;
    @Autowired private ReviewContentService content;
    @Autowired private ReviewUnitPlanner planner;
    @Autowired private ReviewFanoutRepository fanout;
    @Autowired private ReviewToolReadService reads;
    @Autowired private ReviewToolWriteService writes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;

    private long tenantId;
    private long userId;
    private long projectId;
    private long versionId;
    private long taskId;
    private long modelId;
    private String source;

    @BeforeEach
    void setUp() {
        long seed = Math.abs(UUID.randomUUID().getMostSignificantBits() % 300_000_000L) + 600_000_000L;
        tenantId = seed;
        userId = seed + 1;
        projectId = seed + 2;
        versionId = seed + 3;
        taskId = seed + 5;
        source = "第1集\n1-1 客厅 日 内\n林夏：你好\n1-2 屋顶 夜 外\n顾言：再见";
        modelId = insertModel(seed);
        agents.create(new WorkflowAgentCommand(
            "script-review", "剧本审核", "端到端测试审核 Agent", "只使用可信审核工具。", modelId,
            new BigDecimal("0.100"), 16384, 12, "ENABLED", allSkillCodes(),
            List.of("read_review_context", "read_review_content", "read_review_issue_history",
                "save_review_unit_result", "read_review_unit_results", "save_review_result")), userId);
        jdbc.update("insert into review_project (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at) values (?, ?, '审核端到端', 'TXT', ?, 'ACTIVE', ?, now(), now())",
            projectId, tenantId, source, userId);
        jdbc.update("insert into review_script_version (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at) values (?, ?, ?, 1, 'TXT', ?, ?, now(), now())",
            versionId, tenantId, projectId, source, userId);
        jdbc.update("update review_project set current_version_id = ? where id = ?", versionId, projectId);
    }

    @Test
    void quickReviewLoadsOneDimensionUsesTrustedSceneMatchesHistoryAndSavesFormalResult() {
        List<String> dimensions = List.of("台词合理性");
        Map<String, Object> scope = Map.of("sceneKeys", List.of("1-2"));
        ReviewContentService.FrozenReview frozen = content.freeze(source, "SCENES", scope, dimensions);
        long previousTaskId = taskId - 1;
        insertTask(previousTaskId, 1, "QUICK", "ALL", "{}", dimensions,
            content.freeze(source, "ALL", Map.of(), dimensions), "COMPLETED");
        insertPriorIssue(previousTaskId, frozen.segments().get(0).anchor());
        insertTask(taskId, 2, "QUICK", "SCENES", stringify(scope), dimensions, frozen, "RUNNING");

        WorkflowAgentExecutionPlan plan = plans.freeze(dimensions, "QUICK");
        assertThat(plan.agent().skillCodes()).containsExactly(
            "script-review-foundation", "script-review-execution-framework",
            "script-review-dimension-dialogue");
        assertThat(plan.agent().toolCodes()).containsExactly(
            "read_review_context", "read_review_content", "read_review_issue_history", "save_review_result");

        long runId = insertRun(taskId, "REVIEW_QUICK");
        ToolExecutionContext context = context(taskId, runId,
            new ReviewToolScope(projectId, versionId, null, null, 1, "QUICK", dimensions));
        JsonNode trusted = reads.readContext(context);
        assertThat(trusted.path("scope").path("sceneKeys").get(0).asText()).isEqualTo("1-2");
        JsonNode visible = reads.readContent(context, json.createObjectNode().put("offset", 0).put("limit", 50000));
        assertThat(visible.path("segments").get(0).path("content").asText())
            .contains("顾言：再见").doesNotContain("林夏：你好");
        assertThat(reads.readHistory(context, json.createObjectNode().put("page", 1).put("pageSize", 50))
            .path("issues")).hasSize(1);

        ObjectNode quickResult = formalPayload(frozen, "台词合理性", "顾言：再见",
            frozen.segments().get(0).anchor(), "LOW");
        ObjectNode quickHit = (ObjectNode) quickResult.path("issues").get(0).path("hits").get(0);
        quickHit.put("episode", 1);
        quickHit.put("scene", "1-2");
        writes.saveResult(context, quickResult);

        assertThat(jdbc.queryForObject("select status from review_task where id = ?", String.class, taskId))
            .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForMap("select issue_no, status, related_issue_no from review_issue where task_id = ?", taskId))
            .containsEntry("ISSUE_NO", "R2-01")
            .containsEntry("STATUS", "persists")
            .containsEntry("RELATED_ISSUE_NO", "R1-01");
        assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where task_id = ?", Integer.class, taskId))
            .isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_event where task_id = ?", Integer.class, taskId))
            .isOne();
    }

    @Test
    void deepReviewPlansHeadingFreeUnitsPersistsCandidatesAndAggregatesMultiHitFormalResult() {
        source = "甲说：开始。\n\n冲突逐步升级，人物进入仓库寻找证据。\n\n乙说：结束，但线索仍未回收。";
        jdbc.update("update review_script_version set content = ? where id = ?", source, versionId);
        List<String> dimensions = List.of("剧情逻辑与因果");
        ReviewContentService.FrozenReview frozen = content.freeze(source, "ALL", Map.of(), dimensions);
        insertTask(taskId, 1, "DEEP", "ALL", "{}", dimensions, frozen, "RUNNING");
        List<ReviewUnitPlanner.Unit> units = planner.plan(source, "ALL", Map.of(), frozen, 28, 4);
        assertThat(units).hasSizeGreaterThan(1);

        WorkflowAgentExecutionPlan childPlan = plans.freeze(dimensions, "DEEP_CHILD");
        WorkflowAgentExecutionPlan aggregationPlan = plans.freeze(dimensions, "DEEP_AGGREGATION");
        assertThat(childPlan.agent().skillCodes()).doesNotContain("script-review-cross-episode-synthesis");
        assertThat(aggregationPlan.agent().skillCodes()).endsWith("script-review-cross-episode-synthesis");
        String unitSetHash = ReviewContentService.hash(units.stream()
            .map(unit -> unit.unitKey() + ":" + unit.fingerprint()).collect(java.util.stream.Collectors.joining("|")));
        long snapshotId = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(
            tenantId, projectId, taskId, versionId, 1, "script-review", childPlan.agent().revision(),
            "[]", modelId, stringify(dimensions), "{}", frozen.versionHash(), frozen.scopeHash(),
            frozen.dimensionsHash(), unitSetHash, units.size(), 2));

        for (ReviewUnitPlanner.Unit unit : units) {
            long unitId = fanout.addUnit(new ReviewFanoutRepository.UnitDraft(snapshotId, unit.unitNo(),
                unit.unitKey(), "{}", unit.startOffset(), unit.endOffset(), unit.fingerprint()));
            fanout.transitionUnit(unitId, ReviewUnitStatus.PENDING, ReviewUnitStatus.RUNNING);
            long runId = insertRun(taskId, "REVIEW_CHILD");
            ToolExecutionContext child = context(taskId, runId,
                new ReviewToolScope(projectId, versionId, snapshotId, unitId, 1, "DEEP_CHILD", dimensions));
            reads.readContent(child, json.createObjectNode().put("offset", 0).put("limit", 50000));
            ObjectNode payload = hashes(frozen);
            payload.put("contentFingerprint", unit.fingerprint());
            payload.set("coverage", coverage(unit.unitKey()));
            payload.set("candidates", json.createArrayNode());
            writes.saveUnitResult(child, payload);
        }

        assertThat(fanout.orderedUnits(snapshotId)).allSatisfy(unit -> {
            assertThat(unit.getStatus()).isEqualTo("SUCCEEDED");
            assertThat(unit.getCandidateSaved()).isTrue();
        });
        long aggregationRunId = insertRun(taskId, "REVIEW_AGGREGATION");
        ToolExecutionContext aggregation = context(taskId, aggregationRunId,
            new ReviewToolScope(projectId, versionId, snapshotId, null, 1, "DEEP_AGGREGATION", dimensions));
        assertThat(writes.readUnitResults(aggregation,
            json.createObjectNode().put("page", 1).put("pageSize", 100)).path("units")).hasSize(units.size());

        ObjectNode formal = formalPayload(frozen, "剧情逻辑与因果", "甲说：开始。",
            frozen.segments().get(0).anchor(), "HIGH");
        ArrayNode hits = (ArrayNode) formal.path("issues").get(0).path("hits");
        ObjectNode secondHit = hits.addObject();
        secondHit.put("anchor", frozen.segments().get(0).anchor());
        secondHit.put("excerpt", "乙说：结束");
        ((ArrayNode) formal.path("issues").get(0).path("evidence")).add("乙说：结束");
        writes.saveResult(aggregation, formal);

        assertThat(jdbc.queryForObject("select status from review_task where id = ?", String.class, taskId))
            .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id = ?", Integer.class, taskId))
            .isOne();
        assertThat(jdbc.queryForObject("select count(*) from review_issue_hit where task_id = ?", Integer.class, taskId))
            .isEqualTo(2);
    }

    @Test
    void invalidOrForeignOrCanceledSavesNeverCompleteAndIncompleteAggregationCannotFinalize() {
        List<String> dimensions = List.of("台词合理性");
        ReviewContentService.FrozenReview frozen = content.freeze(source, "ALL", Map.of(), dimensions);
        insertTask(taskId, 1, "QUICK", "ALL", "{}", dimensions, frozen, "RUNNING");
        long runId = insertRun(taskId, "REVIEW_QUICK");
        ToolExecutionContext quick = context(taskId, runId,
            new ReviewToolScope(projectId, versionId, null, null, 1, "QUICK", dimensions));
        reads.readContent(quick, json.createObjectNode().put("offset", 0).put("limit", 50000));

        ObjectNode stale = formalPayload(frozen, "台词合理性", "顾言：再见",
            frozen.segments().get(0).anchor(), "LOW");
        stale.put("versionHash", "0".repeat(64));
        assertThatThrownBy(() -> writes.saveResult(quick, stale)).isInstanceOf(BusinessException.class);

        ObjectNode unselected = formalPayload(frozen, "人物动机", "顾言：再见",
            frozen.segments().get(0).anchor(), "LOW");
        assertThatThrownBy(() -> writes.saveResult(quick, unselected)).isInstanceOf(BusinessException.class);

        ObjectNode badSeverity = formalPayload(frozen, "台词合理性", "顾言：再见",
            frozen.segments().get(0).anchor(), "UNKNOWN");
        assertThatThrownBy(() -> writes.saveResult(quick, badSeverity)).isInstanceOf(BusinessException.class);

        ObjectNode absentEvidence = formalPayload(frozen, "台词合理性", "正文不存在的证据",
            frozen.segments().get(0).anchor(), "LOW");
        assertThatThrownBy(() -> writes.saveResult(quick, absentEvidence)).isInstanceOf(BusinessException.class);

        ToolExecutionContext foreign = context(taskId, runId,
            new ReviewToolScope(projectId + 999, versionId, null, null, 1, "QUICK", dimensions));
        assertThatThrownBy(() -> reads.readContext(foreign)).isInstanceOf(BusinessException.class);
        assertUnfinished();

        jdbc.update("update review_task set status = 'CANCELED' where id = ?", taskId);
        assertThatThrownBy(() -> writes.saveResult(quick,
            formalPayload(frozen, "台词合理性", "顾言：再见", frozen.segments().get(0).anchor(), "LOW")))
            .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id = ?", Integer.class, taskId))
            .isZero();

        jdbc.update("update review_task set status = 'RUNNING', review_mode = 'DEEP' where id = ?", taskId);
        long snapshotId = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(
            tenantId, projectId, taskId, versionId, 1, "script-review", 1, "[]", modelId,
            stringify(dimensions), "{}", frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
            "incomplete-units", 1, 1));
        fanout.addUnit(new ReviewFanoutRepository.UnitDraft(snapshotId, 1, "offset-0", "{}", 0,
            source.length(), ReviewContentService.hash(source)));
        ToolExecutionContext aggregation = context(taskId, insertRun(taskId, "REVIEW_AGGREGATION"),
            new ReviewToolScope(projectId, versionId, snapshotId, null, 1, "DEEP_AGGREGATION", dimensions));
        assertThatThrownBy(() -> writes.saveResult(aggregation,
            formalPayload(frozen, "台词合理性", "顾言：再见", frozen.segments().get(0).anchor(), "LOW")))
            .isInstanceOf(BusinessException.class).hasMessageContaining("INCOMPLETE");
        assertUnfinished();
    }

    private long insertModel(long seed) {
        Long providerId = jdbc.queryForObject("select min(id) from ai_provider", Long.class);
        String code = "review-e2e-model-" + seed;
        jdbc.update("insert into ai_model (provider_id, code, name, model_code, service_type, status, is_default, sort, created_at, updated_at) values (?, ?, ?, ?, 'TEXT', 'ENABLED', false, 999, now(), now())",
            providerId, code, code, code);
        long id = jdbc.queryForObject("select id from ai_model where code = ?", Long.class, code);
        jdbc.update("insert into ai_model_capability (model_id, capability, status, created_at, updated_at) values (?, 'TOOL_CALLING', 'ENABLED', now(), now())", id);
        return id;
    }

    private List<String> allSkillCodes() {
        java.util.ArrayList<String> codes = new java.util.ArrayList<>();
        codes.add("script-review-foundation");
        codes.add("script-review-execution-framework");
        Arrays.stream(ReviewDimension.values()).map(ReviewDimension::skillCode).forEach(codes::add);
        codes.add("script-review-cross-episode-synthesis");
        return List.copyOf(codes);
    }

    private void insertTask(long id, int round, String mode, String scopeType, String scopeJson,
        List<String> dimensions, ReviewContentService.FrozenReview frozen, String status) {
        jdbc.update("insert into review_task (id, tenant_id, project_id, script_version_id, round_no, review_mode, selected_dimensions_json, review_scope_type, review_scope_json, version_hash, scope_hash, dimensions_hash, status, overall_progress, idempotency_key, created_by, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 10, ?, ?, now(), now())",
            id, tenantId, projectId, versionId, round, mode, stringify(dimensions), scopeType, scopeJson,
            frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(), status, "review-e2e-" + id, userId);
    }

    private void insertPriorIssue(long previousTaskId, String anchor) {
        jdbc.update("insert into review_issue (tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, position_json, excerpt, problem, evidence_json, suggestion, status, manually_resolved, created_at, updated_at) values (?, ?, ?, ?, 1, 'R1-01', '台词合理性', 'LOW', '告别突兀', '{\"episode\":1,\"scene\":\"1-2\"}', '顾言：再见', '缺少回应', '[\"顾言：再见\"]', '补反应', 'new', false, now(), now())",
            tenantId, projectId, previousTaskId, versionId);
        long issueId = jdbc.queryForObject("select id from review_issue where task_id = ?", Long.class, previousTaskId);
        jdbc.update("insert into review_issue_hit (tenant_id, project_id, task_id, issue_id, hit_no, episode_no, scene_no, anchor_label, excerpt, selected, created_at, updated_at) values (?, ?, ?, ?, 1, 1, '1-2', ?, '顾言：再见', true, now(), now())",
            tenantId, projectId, previousTaskId, issueId, anchor);
    }

    private long insertRun(long targetTaskId, String runType) {
        jdbc.update("insert into ai_workflow_agent_run (agent_code, run_type, tenant_id, user_id, project_id, task_id, status, model_id, temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at) values ('script-review', ?, ?, ?, ?, ?, 'RUNNING', ?, 0.1, 16384, 12, '', now(), now())",
            runType, tenantId, userId, projectId, targetTaskId, modelId);
        return jdbc.queryForObject("select max(id) from ai_workflow_agent_run where task_id = ?", Long.class, targetTaskId);
    }

    private ToolExecutionContext context(long targetTaskId, long runId, ReviewToolScope scope) {
        return new ToolExecutionContext(tenantId, userId, projectId, null, null, targetTaskId, null,
            runId, null, null, null, Set.of(), null, new WorkflowToolRunState(), scope);
    }

    private ObjectNode formalPayload(ReviewContentService.FrozenReview frozen, String dimension,
        String excerpt, String anchor, String severity) {
        ObjectNode payload = hashes(frozen);
        payload.put("score", 82);
        payload.put("conclusion", "整体可用");
        payload.set("coverage", coverage(anchor));
        ObjectNode issue = payload.putArray("issues").addObject();
        issue.put("dimension", dimension);
        issue.put("severity", severity);
        issue.put("title", "告别突兀");
        issue.put("problem", "缺少回应");
        issue.putArray("evidence").add(excerpt);
        issue.put("suggestion", "补反应");
        ObjectNode hit = issue.putArray("hits").addObject();
        hit.put("anchor", anchor);
        hit.put("excerpt", excerpt);
        return payload;
    }

    private ObjectNode hashes(ReviewContentService.FrozenReview frozen) {
        ObjectNode payload = json.createObjectNode();
        payload.put("versionHash", frozen.versionHash());
        payload.put("scopeHash", frozen.scopeHash());
        payload.put("dimensionsHash", frozen.dimensionsHash());
        return payload;
    }

    private ObjectNode coverage(String anchor) {
        ObjectNode coverage = json.createObjectNode();
        coverage.put("complete", true);
        coverage.putArray("anchors").add(anchor);
        return coverage;
    }

    private void assertUnfinished() {
        assertThat(jdbc.queryForObject("select status from review_task where id = ?", String.class, taskId))
            .isEqualTo("RUNNING");
        assertThat(jdbc.queryForObject("select count(*) from review_issue where task_id = ?", Integer.class, taskId))
            .isZero();
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
