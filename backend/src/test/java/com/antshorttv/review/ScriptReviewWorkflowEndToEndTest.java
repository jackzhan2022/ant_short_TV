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
            List.of("read_review_context", "read_review_content")), userId);
        jdbc.update("insert into review_project (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at) values (?, ?, '审核端到端', 'TXT', ?, 'ACTIVE', ?, now(), now())",
            projectId, tenantId, source, userId);
        jdbc.update("insert into review_script_version (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at) values (?, ?, ?, 1, 'TXT', ?, ?, now(), now())",
            versionId, tenantId, projectId, source, userId);
        jdbc.update("update review_project set current_version_id = ? where id = ?", versionId, projectId);
    }

    @Test
    void markdownQuickPlanReadsOnlyItsFrozenSceneWithoutWriteTools() {
        List<String> dimensions = List.of("台词合理性");
        Map<String, Object> scope = Map.of("sceneKeys", List.of("1-2"));
        ReviewContentService.FrozenReview frozen = content.freeze(source, "SCENES", scope, dimensions);
        insertTask(taskId, 1, "QUICK", "SCENES", stringify(scope), dimensions, frozen, "RUNNING");
        WorkflowAgentExecutionPlan plan = plans.freeze(dimensions, "MARKDOWN_QUICK");
        assertThat(plan.agent().toolCodes()).containsExactly("read_review_context", "read_review_content");
        long runId = insertRun(taskId, "REVIEW_QUICK");
        ToolExecutionContext context = context(taskId, runId,
            new ReviewToolScope(projectId, versionId, null, null, 1, "MARKDOWN_QUICK", dimensions));
        assertThat(reads.readContext(context).path("scope").path("sceneKeys").get(0).asText()).isEqualTo("1-2");
        JsonNode visible = reads.readContent(context, json.createObjectNode());
        assertThat(visible.path("segments").get(0).path("content").asText())
            .contains("顾言：再见").doesNotContain("林夏：你好");
    }

    @Test
    void markdownFragmentIsPersistedAndCanceledTaskCannotCommitAnotherFragment() {
        List<String> dimensions = List.of("台词合理性");
        ReviewContentService.FrozenReview frozen = content.freeze(source, "ALL", Map.of(), dimensions);
        insertTask(taskId, 1, "DEEP", "ALL", "{}", dimensions, frozen, "RUNNING");
        long snapshot = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(
            tenantId, projectId, taskId, versionId, 1, "script-review", 1L, "[]", modelId,
            stringify(dimensions), "{}", frozen.versionHash(), frozen.scopeHash(),
            frozen.dimensionsHash(), "markdown-units", 2, 1));
        long first = fanout.addUnit(new ReviewFanoutRepository.UnitDraft(snapshot, 1, "first",
            "DIMENSION_MARKDOWN", dimensions.get(0), "{}", 0, source.length(), frozen.versionHash()));
        long second = fanout.addUnit(new ReviewFanoutRepository.UnitDraft(snapshot, 2, "second",
            "DIMENSION_MARKDOWN", dimensions.get(0), "{}", 0, source.length(), frozen.versionHash()));
        long run = insertRun(taskId, "REVIEW_DEEP_CHILD");
        jdbc.update("update review_fanout_unit set status='RUNNING' where snapshot_id=?", snapshot);
        fanout.replaceMarkdownFragment(new ReviewFanoutRepository.MarkdownFragmentDraft(
            snapshot, first, run, 1, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
            frozen.versionHash(), "# 已保存片段", ReviewContentService.hash("# 已保存片段")));
        assertThat(fanout.orderedMarkdownFragments(snapshot)).hasSize(1);
        assertThat(fanout.orderedMarkdownFragments(snapshot).get(0).reportMarkdown()).isEqualTo("# 已保存片段");
        jdbc.update("update review_task set status='CANCELED' where id=?", taskId);
        assertThatThrownBy(() -> fanout.replaceMarkdownFragment(new ReviewFanoutRepository.MarkdownFragmentDraft(
            snapshot, second, run, 1, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash(),
            frozen.versionHash(), "# 不应完成", ReviewContentService.hash("# 不应完成"))))
            .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select status from review_fanout_unit where id=?", String.class, second))
            .isEqualTo("RUNNING");
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

    private long insertRun(long targetTaskId, String runType) {
        jdbc.update("insert into ai_workflow_agent_run (agent_code, run_type, tenant_id, user_id, project_id, task_id, status, model_id, temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at) values ('script-review', ?, ?, ?, ?, ?, 'RUNNING', ?, 0.1, 16384, 12, '', now(), now())",
            runType, tenantId, userId, projectId, targetTaskId, modelId);
        return jdbc.queryForObject("select max(id) from ai_workflow_agent_run where task_id = ?", Long.class, targetTaskId);
    }

    private ToolExecutionContext context(long targetTaskId, long runId, ReviewToolScope scope) {
        return new ToolExecutionContext(tenantId, userId, projectId, null, null, targetTaskId, null,
            runId, null, null, null, Set.of(), null, new WorkflowToolRunState(), scope);
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
