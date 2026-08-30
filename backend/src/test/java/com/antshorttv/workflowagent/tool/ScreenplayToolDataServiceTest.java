package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ScreenplayToolDataServiceTest {
    @Autowired
    private ScreenplayToolDataService service;
    @Autowired
    private JdbcTemplate jdbc;
    @MockBean
    private ProjectPermissionGuard permissionGuard;
    @SpyBean
    private EpisodeScriptCurrentSelector currentSelector;

    private long tenantId;
    private long projectId;
    private long scriptId;
    private long episodeId;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        tenantId = Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000) + 10_000;
        projectId = tenantId + 1;
        long userId = tenantId + 2;
        jdbc.update("""
            insert into project
              (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (?, ?, 'Tool Project', ?, ?, 'ACTIVE', ?, now(), now())
            """, projectId, tenantId, "TOOL_" + tenantId, userId, userId);
        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at)
            values (?, ?, 'Tool Script', 'MANUAL_EDIT', 'all episodes', 'DRAFT', ?, now(), now())
            """, tenantId, projectId, userId);
        scriptId = jdbc.queryForObject("select id from script where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into script_version
              (tenant_id, project_id, script_id, version_no, source_type, content, status, created_by, created_at)
            values (?, ?, ?, 1, 'MANUAL_EDIT', 'all episodes', 'DRAFT', ?, now())
            """, tenantId, projectId, scriptId, userId);
        long scriptVersionId = jdbc.queryForObject(
            "select id from script_version where script_id = ?", Long.class, scriptId);
        for (int episodeNo = 1; episodeNo <= 3; episodeNo++) {
            jdbc.update("""
                insert into script_episode
                  (tenant_id, project_id, script_id, script_version_id, stable_key, episode_no,
                   title, content, content_fingerprint, reconciliation_status, status, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, 'MATCHED', 'ACTIVE', now(), now())
                """, tenantId, projectId, scriptId, scriptVersionId, "episode-" + episodeNo,
                episodeNo, "Episode " + episodeNo, "Content " + episodeNo, "fingerprint-" + episodeNo);
        }
        episodeId = jdbc.queryForObject("""
            select id from script_episode where project_id = ? and episode_no = 2
            """, Long.class, projectId);
        context = new ToolExecutionContext(tenantId, userId, projectId, episodeId, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"));
    }

    @Test
    void readsOnlyTrustedProjectAndEpisodeScopeWithDeclaredShapes() {
        JsonNode project = service.readProjectContext(context);
        JsonNode episodes = service.listEpisodeScripts(context);
        JsonNode current = service.readEpisodeScript(context);
        JsonNode adjacent = service.readAdjacentEpisodes(context);

        assertThat(project.path("projectId").asLong()).isEqualTo(projectId);
        assertThat(project.path("name").asText()).isEqualTo("Tool Project");
        assertThat(episodes.path("episodes")).hasSize(3);
        assertThat(current.path("episodeId").asLong()).isEqualTo(episodeId);
        assertThat(current.path("content").asText()).isEqualTo("Content 2");
        assertThat(adjacent.path("previous").path("episodeNo").asInt()).isEqualTo(1);
        assertThat(adjacent.path("next").path("episodeNo").asInt()).isEqualTo(3);
        verify(permissionGuard, org.mockito.Mockito.atLeastOnce())
            .require(tenantId, projectId, "SCRIPT:VIEW");
    }

    @Test
    void readsTheCompleteProjectScriptInEpisodeOrder() {
        jdbc.update("update script_episode set content = 'Current Content 3' where project_id = ? and episode_no = 3",
            projectId);

        JsonNode fullScript = service.readProjectFullScript(context);

        assertThat(fullScript.path("episodes")).hasSize(3);
        assertThat(fullScript.path("episodes").get(0).path("episodeNo").asInt()).isEqualTo(1);
        assertThat(fullScript.path("episodes").get(2).path("episodeNo").asInt()).isEqualTo(3);
        assertThat(fullScript.path("episodes").get(2).path("content").asText())
            .isEqualTo("Current Content 3");
        verify(permissionGuard).require(tenantId, projectId, "SCRIPT:VIEW");
    }

    @Test
    void returnsAnEmptyEpisodeArrayWhenProjectHasNoEpisodes() {
        jdbc.update("delete from script_episode where project_id = ?", projectId);

        JsonNode fullScript = service.readProjectFullScript(context);

        assertThat(fullScript.path("episodes")).isEmpty();
    }

    @Test
    void validatesScreenplayFormatWithoutAccessingAnotherScope() {
        JsonNode valid = service.validateScreenplayFormat("""
            ## S01 | 内景 · 咖啡厅 | 黄昏

            小明走进咖啡厅。

            小明：（微笑）你好。
            """);
        JsonNode invalid = service.validateScreenplayFormat("plain prose");
        assertThat(valid.path("valid").asBoolean()).isTrue();
        assertThat(invalid.path("valid").asBoolean()).isFalse();
        assertThat(invalid.path("errors")).isNotEmpty();
    }

    @Test
    void readsLatestAnalysisAndCurrentAssetsWithinProject() {
        long userId = context.userId();
        long scriptVersionId = jdbc.queryForObject(
            "select id from script_version where script_id = ?", Long.class, scriptId);
        jdbc.update("""
            insert into script_analysis_task
              (tenant_id, project_id, script_id, script_version_id, workflow_code, status,
               overall_progress, idempotency_key, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'default', 'SUCCEEDED', 100, ?, ?, now(), now())
            """, tenantId, projectId, scriptId, scriptVersionId, "analysis-" + UUID.randomUUID(), userId);
        long taskId = jdbc.queryForObject(
            "select id from script_analysis_task where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into script_analysis_stage
              (task_id, stage_code, stage_order, status, progress_percent, completed_units,
               total_units, attempt_no, retryable, created_at, updated_at)
            values (?, 'SUMMARY', 1, 'SUCCEEDED', 100, 1, 1, 1, false, now(), now())
            """, taskId);
        long stageId = jdbc.queryForObject(
            "select id from script_analysis_stage where task_id = ?", Long.class, taskId);
        jdbc.update("""
            insert into script_analysis_result
              (task_id, stage_id, result_type, schema_version, status, normalized_json, created_at, updated_at)
            values (?, ?, 'SUMMARY', '1', 'SUCCEEDED', '{"summary":"ok"}', now(), now())
            """, taskId, stageId);
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, name, role_type, status, created_by, created_at, updated_at)
            values (?, ?, 'Hero', 'MAIN', 'ACTIVE', ?, now(), now())
            """, tenantId, projectId, userId);

        JsonNode analysis = service.readScriptAnalysis(context);
        JsonNode assets = service.readScriptAssets(context);
        assertThat(analysis.path("task").path("overall_progress").asInt()).isEqualTo(100);
        assertThat(analysis.path("stages")).hasSize(1);
        assertThat(analysis.path("stages").get(0).path("normalized_json").asText())
            .contains("summary");
        assertThat(assets.path("characters")).hasSize(1);
        assertThat(assets.path("characters").get(0).path("name").asText()).isEqualTo("Hero");
    }

    @Test
    void saveCreatesAndSelectsNewVersionsWhileRetainingPriorVersions() {
        JsonNode first = service.saveEpisodeScript(context, screenplay("First rewritten screenplay"));
        JsonNode second = service.saveEpisodeScript(context, screenplay("Second rewritten screenplay"));

        assertThat(first.path("versionNo").asInt()).isEqualTo(1);
        assertThat(second.path("versionNo").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            select count(*) from script_episode_version where episode_id = ?
            """, Integer.class, episodeId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            select count(*) from script_episode_version where episode_id = ? and is_current = true
            """, Integer.class, episodeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            select content from script_episode where id = ?
            """, String.class, episodeId)).isEqualTo(screenplay("Second rewritten screenplay"));
    }

    @Test
    void failedCurrentSelectionRollsBackCreatedVersionAndEpisodeContent() {
        doThrow(new IllegalStateException("selection failed")).when(currentSelector)
            .selectCurrent(anyLong(), anyLong(), anyLong(), anyLong(), anyString());

        assertThatThrownBy(() -> service.saveEpisodeScript(context, screenplay("Must roll back")))
            .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("""
            select count(*) from script_episode_version where episode_id = ?
            """, Integer.class, episodeId)).isZero();
        assertThat(jdbc.queryForObject("select content from script_episode where id = ?",
            String.class, episodeId)).isEqualTo("Content 2");
    }

    @Test
    void saveRejectsInvalidScreenplayBeforeCreatingVersion() {
        assertThatThrownBy(() -> service.saveEpisodeScript(context, "plain prose"))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode_version where episode_id = ?",
            Integer.class, episodeId)).isZero();
    }

    private String screenplay(String dialogue) {
        return """
            ## S01 | 内景 · 咖啡厅 | 黄昏

            小明走进咖啡厅。

            小明：（微笑）%s
            """.formatted(dialogue);
    }
}
