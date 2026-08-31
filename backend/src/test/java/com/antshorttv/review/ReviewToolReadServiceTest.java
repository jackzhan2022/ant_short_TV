package com.antshorttv.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.antshorttv.workflowagent.tool.ReviewToolScope;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
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

@SpringBootTest
@Transactional
class ReviewToolReadServiceTest {
    @Autowired private ReviewToolReadService reads;
    @Autowired private ReviewContentService content;
    @Autowired private ReviewFanoutRepository fanout;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    private final String script = "第1集\n1-1 客厅 日 内\n林夏：你好\n1-2 门外 夜 外\n顾言：再见";

    @BeforeEach
    void seed() throws Exception {
        ReviewContentService.FrozenReview frozen = content.freeze(script, "SCENES",
            Map.of("sceneKeys", List.of("1-2")), List.of("台词合理性"));
        jdbc.update("insert into review_project (id, tenant_id, name, source_type, original_content, status, created_by, created_at, updated_at) values (98601, 98600, 'R', 'TXT', ?, 'ACTIVE', 1, now(), now())", script);
        jdbc.update("insert into review_script_version (id, tenant_id, project_id, version_no, source_type, content, created_by, created_at, updated_at) values (98602, 98600, 98601, 1, 'TXT', ?, 1, now(), now())", script);
        jdbc.update("""
            insert into review_task
              (id, tenant_id, project_id, script_version_id, round_no, review_mode,
               selected_dimensions_json, review_scope_type, review_scope_json,
               version_hash, scope_hash, dimensions_hash, status, overall_progress,
               idempotency_key, created_by, created_at, updated_at)
            values (98603, 98600, 98601, 98602, 2, 'QUICK', '["台词合理性"]',
                    'SCENES', '{"sceneKeys":["1-2"]}', ?, ?, ?, 'RUNNING', 20,
                    'read-98603', 1, now(), now())
            """, frozen.versionHash(), frozen.scopeHash(), frozen.dimensionsHash());
    }

    @Test
    void returnsFrozenContextAndOnlySelectedSceneContent() throws Exception {
        var context = context();
        var reviewContext = reads.readContext(context);
        assertThat(reviewContext.path("mode").asText()).isEqualTo("QUICK");
        assertThat(reviewContext.path("dimensions")).extracting(node -> node.asText())
            .containsExactly("台词合理性");
        var page = reads.readContent(context, json.readTree("{\"offset\":0,\"limit\":50000}"));
        String visible = page.path("segments").get(0).path("content").asText();
        assertThat(visible).contains("1-2 门外", "顾言：再见").doesNotContain("1-1 客厅", "林夏：你好");
        assertThat(page.path("hasMore").asBoolean()).isFalse();
    }

    @Test
    void deepUnitContentExposesTheOnlyAnchorAcceptedByCandidateSave() throws Exception {
        ReviewContentService.FrozenReview frozen = content.freeze(script, "SCENES",
            Map.of("sceneKeys", List.of("1-2")), List.of("台词合理性"));
        int start = script.indexOf("1-2");
        String visible = script.substring(start);
        Long modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        long snapshotId = fanout.openSnapshot(new ReviewFanoutRepository.SnapshotDraft(
            98600L, 98601L, 98603L, 98602L, 1, "script-review", 1L, "[]", modelId,
            "[\"台词合理性\"]", "{\"sceneKeys\":[\"1-2\"]}", frozen.versionHash(),
            frozen.scopeHash(), frozen.dimensionsHash(), "unit-set", 1, 1));
        long unitId = fanout.addUnit(new ReviewFanoutRepository.UnitDraft(
            snapshotId, 1, "unit:0001", "{}", start, script.length(), ReviewContentService.hash(visible)));
        ToolExecutionContext unitContext = new ToolExecutionContext(
            98600L, 1L, null, null, null, 98603L, null, 778L, null, null, null,
            Set.of(), null, new WorkflowToolRunState(), new ReviewToolScope(
                98601L, 98602L, snapshotId, unitId, 1, "DEEP_CHILD", List.of("台词合理性")));

        var page = reads.readContent(unitContext, json.readTree("{\"offset\":0,\"limit\":50000}"));

        assertThat(page.path("segments").get(0).path("unitKey").asText()).isEqualTo("unit:0001");
        assertThat(page.path("segments").get(0).path("anchors"))
            .extracting(node -> node.asText()).containsExactly(frozen.segments().get(0).anchor());
    }

    @Test
    void firstRoundHistoryIsEmptyAndLaterRoundIsDimensionFiltered() throws Exception {
        assertThat(reads.readHistory(context(), json.readTree("{\"page\":1,\"pageSize\":50}"))
            .path("issues")).isEmpty();
        jdbc.update("insert into review_issue (id, tenant_id, project_id, task_id, script_version_id, round_no, issue_no, dimension, severity, title, excerpt, problem, suggestion, status, created_at, updated_at) values (98604, 98600, 98601, 98603, 98602, 1, 'R1-001', '台词合理性', 'LOW', '告别突兀', '顾言：再见', '缺少回应', '补反应', 'new', now(), now())");
        jdbc.update("insert into review_issue_hit (id, tenant_id, project_id, task_id, issue_id, hit_no, scene_no, line_no, anchor_label, excerpt, selected, created_at, updated_at) values (98605, 98600, 98601, 98603, 98604, 1, '1-2', 5, 'episode:1/scene:1-2', '顾言：再见', true, now(), now())");
        var history = reads.readHistory(context(), json.readTree("{\"page\":1,\"pageSize\":50}"));
        assertThat(history.path("issues")).hasSize(1);
        assertThat(history.path("issues").get(0).path("hits")).hasSize(1);
    }

    @Test
    void resolvesHistoryIssueIdRegardlessOfJdbcColumnNameCase() {
        assertThat(reads.issueId(Map.of("ID", 98604L))).isEqualTo(98604L);
        assertThat(reads.issueId(Map.of("id", 98605L))).isEqualTo(98605L);
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(98600L, 1L, null, null, null, 98603L, null, 777L,
            null, null, null, Set.of(), null, new WorkflowToolRunState(),
            new ReviewToolScope(98601L, 98602L, null, null, 1, "QUICK", List.of("台词合理性")));
    }
}
