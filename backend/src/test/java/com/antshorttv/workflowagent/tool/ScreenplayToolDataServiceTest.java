package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
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
    private WorkflowToolRegistry registry;
    @Autowired
    private WorkflowToolSchemaValidator schemaValidator;
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
    void adjacentEpisodesExposeOnlyBoundedContinuityExcerpts() {
        String previous = "前".repeat(900) + "PREVIOUS_END";
        String next = "NEXT_START" + "后".repeat(900);
        jdbc.update("""
            update script_episode set content = ?
             where project_id = ? and episode_no = 1
            """, previous, projectId);
        jdbc.update("""
            update script_episode set content = ?
             where project_id = ? and episode_no = 3
            """, next, projectId);

        jdbc.update("""
            insert into script_episode_summary
              (tenant_id, project_id, script_id, episode_id, schema_version, content_json,
               source, created_by, updated_by, created_at, updated_at)
            select tenant_id, project_id, script_id, id, 1, '{"summary":"previous summary"}',
                   'USER', ?, ?, now(), now()
              from script_episode where project_id = ? and episode_no = 1
            """, context.userId(), context.userId(), projectId);
        JsonNode adjacent = service.readAdjacentEpisodes(context);

        assertThat(adjacent.path("next").path("summary").isNull()).isTrue();
        assertThat(adjacent.path("previous").has("content")).isFalse();
        assertThat(adjacent.path("previous").path("summary").asText())
            .isEqualTo("previous summary");
        assertThat(adjacent.path("previous").path("endingExcerpt").asText())
            .endsWith("PREVIOUS_END");
        assertThat(adjacent.path("next").path("openingExcerpt").asText())
            .startsWith("NEXT_START");
        assertThat(adjacent.path("previous").path("contentTruncated").asBoolean()).isTrue();
        assertThat(adjacent.path("next").path("contentTruncated").asBoolean()).isTrue();
        assertThat(adjacent.path("previous").path("endingExcerpt").asText())
            .hasSizeLessThanOrEqualTo(600);
        assertThat(adjacent.path("next").path("openingExcerpt").asText())
            .hasSizeLessThanOrEqualTo(600);
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
    void plansStructureFromTrustedScopeWithoutReturningTheCompleteScript() {
        String source = "开场\n" + "剧情推进。".repeat(5000) + "\n\n内景 客厅 夜\n" + "冲突。".repeat(5000);
        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        long modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        jdbc.update("""
            insert into ai_workflow_agent_run
              (agent_code, run_type, tenant_id, user_id, project_id, status, model_id,
               temperature, max_tokens, max_steps, prompt_snapshot, started_at, created_at)
            values ('short-drama-episode-splitting', 'PROJECT', ?, ?, ?, 'RUNNING', ?,
                    0.2, 16384, 8, 'prompt', now(), now())
            """, tenantId, context.userId(), projectId, modelId);
        long runId = jdbc.queryForObject("select max(id) from ai_workflow_agent_run", Long.class);
        WorkflowToolRunState runState = new WorkflowToolRunState();
        runState.beginSplitFallback("CONTEXT_PREFLIGHT");
        ToolExecutionContext scoped = new ToolExecutionContext(
            tenantId, context.userId(), projectId, null, scriptId, null, null, runId,
            Set.of("SCRIPT:VIEW"), null, runState);

        JsonNode result = service.readScriptStructure(scoped);

        assertThat(result.path("contentHash").asText()).isNotBlank();
        assertThat(result.path("snapshotKey").asText()).isNotBlank();
        assertThat(result.path("totalChunks").asInt()).isGreaterThan(1);
        schemaValidator.validate(registry.require("read_script_structure").outputSchema(), result);
        assertThat(result.path("segmentCatalog").path("firstSegmentId").asText()).isEqualTo("S0001");
        assertThat(result.path("segmentCatalog").path("lastSegmentId").asText()).isEqualTo("S0004");
        assertThat(result.path("anchors")).anySatisfy(anchor -> {
            assertThat(anchor.path("startSegmentId").asText()).isEqualTo("S0003");
            assertThat(anchor.path("previousSegmentId").asText()).isEqualTo("S0002");
        });
        assertThat(result.toString()).doesNotContain(source);
        assertThat(scoped.runState().require("splitSnapshotId", Long.class)).isPositive();
        assertThat(scoped.runState().require("currentScriptHash", String.class))
            .isEqualTo(result.path("contentHash").asText());
        assertThat(jdbc.queryForObject("""
            select fallback_reason from script_split_snapshot where parent_run_id = ?
            """, String.class, runId)).isEqualTo("CONTEXT_PREFLIGHT");
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

    @Test
    void savesFormalEpisodeAssetsWithLooksPropStatesAndSceneUsage() throws Exception {
        String content = "林小满穿着红裙走进咖啡厅，拿起她的手机。手机屏幕碎裂。";
        jdbc.update("update script_episode set content = ?, content_fingerprint = 'asset-fp' where id = ?",
            content, episodeId);
        ToolExecutionContext assetContext = summaryContext();
        service.readCurrentEpisode(assetContext);
        JsonNode saved = service.saveEpisodeAssets(assetContext, new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree("""
                {
                  "schemaVersion":1,
                  "characters":[{"localKey":"char_1","assetKey":null,"name":"林小满","aliases":[],"evidence":"林小满","prompt":"canonical character prompt"}],
                  "characterLooks":[{"localKey":"look_1","characterLocalKey":"char_1","variantKey":null,"name":"红裙造型","description":"穿着红裙","evidence":"穿着红裙","preferred":true,"prompt":"red dress delta"}],
                  "scenes":[{"localKey":"scene_1","assetKey":null,"name":"咖啡厅","aliases":[],"evidence":"咖啡厅","description":null,"timeAtmosphere":"白天","usageEvidence":"走进咖啡厅","prompt":"canonical scene prompt"}],
                  "props":[{"localKey":"prop_1","assetKey":null,"name":"林小满的手机","aliases":[{"name":"手机","evidence":"手机"}],"evidence":"她的手机","ownerCharacterLocalKey":"char_1","description":null,"prompt":"canonical prop prompt"}],
                  "propVariants":[{"localKey":"state_1","propLocalKey":"prop_1","variantKey":null,"name":"屏幕碎裂","description":"屏幕碎裂","evidence":"手机屏幕碎裂","preferred":true,"prompt":"cracked screen delta"}]
                }
                """));

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(saved.path("counts").path("characters").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id = ?",
            Integer.class, scriptId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from asset_visual_variant_episode where episode_id = ? and retired_at is null",
            Integer.class, episodeId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select content_json from asset_visual_variant_episode where episode_id = ? and asset_type = 'SCENE'",
            String.class, episodeId)).contains("白天");
        assertThat(jdbc.queryForObject("select count(*) from script_episode_asset_analysis where episode_id = ?",
            Integer.class, episodeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select prompt from character_asset where script_id = ?",
            String.class, scriptId)).isEqualTo("canonical character prompt");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where name = '红裙造型'",
            String.class)).isEqualTo("red dress delta");

        long characterId = jdbc.queryForObject("select id from character_asset where script_id = ?", Long.class, scriptId);
        long lookId = jdbc.queryForObject("select id from asset_visual_variant where name = '红裙造型'", Long.class);
        ToolExecutionContext retryContext = summaryContext();
        service.readCurrentEpisode(retryContext);
        service.saveEpisodeAssets(retryContext, new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"char_1","assetKey":"c_%d","name":"林小满","aliases":[],"evidence":"林小满","prompt":"replacement character prompt"}],
             "characterLooks":[{"localKey":"look_1","characterLocalKey":"char_1","variantKey":"v_%d","name":"红裙造型","description":"穿着红裙","evidence":"穿着红裙","preferred":true,"prompt":"replacement look delta"}],
             "scenes":[],"props":[],"propVariants":[]}
            """.formatted(characterId, lookId)));

        assertThat(jdbc.queryForObject("select prompt from character_asset where id = ?", String.class, characterId))
            .isEqualTo("canonical character prompt");
        assertThat(jdbc.queryForObject("select prompt from asset_visual_variant where id = ?", String.class, lookId))
            .isEqualTo("red dress delta");
    }

    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void formalAssetCoverageAndAutomaticEventCommitOrRollbackTogether() throws Exception {
        long versionId = jdbc.queryForObject("select id from script_version where script_id=?", Long.class, scriptId);
        jdbc.update("""
            insert into script_analysis_task
              (tenant_id,project_id,script_id,script_version_id,workflow_code,status,overall_progress,
               idempotency_key,created_by,created_at,updated_at)
            values (?,?,?,?,'SCRIPT_INITIAL_ANALYSIS','RUNNING',0,?, ?,now(),now())
            """,tenantId,projectId,scriptId,versionId,"event-atomic-"+episodeId,context.userId());
        long taskId = jdbc.queryForObject("select id from script_analysis_task where script_id=?", Long.class,scriptId);
        jdbc.update("""
            insert into ai_execution_task
              (tenant_id,user_id,project_id,scene,capability,business_type,business_id,status,phase,
               client_idempotency_key,trace_id,created_at,updated_at)
            values (?,?,?,'script_analysis','TEXT','SCRIPT_ANALYSIS_TASK',?,'RUNNING','ANALYSIS',?,?,now(),now())
            """,tenantId,context.userId(),projectId,taskId,"event-atomic-"+episodeId,"event-atomic-"+episodeId);
        long executionId=jdbc.queryForObject("select id from ai_execution_task where project_id=?",Long.class,projectId);
        jdbc.update("update script_analysis_task set execution_id=? where id=?",executionId,taskId);
        ToolExecutionContext assetContext = new ToolExecutionContext(tenantId,context.userId(),projectId,
            episodeId,scriptId,taskId,null,null,executionId,null,1,Set.of("SCRIPT:VIEW","SCRIPT:EDIT"),null,new WorkflowToolRunState());
        service.readCurrentEpisode(assetContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,"characters":[],"scenes":[],"props":[],"characterLooks":[],"propVariants":[]}
            """);
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status -> {
            service.saveEpisodeAssets(assetContext,payload);
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from script_episode_asset_analysis where episode_id=?",Integer.class,episodeId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from episode_auto_storyboard_event where episode_id=?",Integer.class,episodeId)).isZero();
        service.saveEpisodeAssets(assetContext,payload);
        service.saveEpisodeAssets(assetContext,payload);
        assertThat(jdbc.queryForObject("select count(*) from script_episode_asset_analysis where episode_id=?",Integer.class,episodeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from episode_auto_storyboard_event where episode_id=?",Integer.class,episodeId)).isEqualTo(1);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ALL", "CHARACTER", "SCENE", "PROP"})
    void scopedSavesKeepOtherTypesAndHumanPrompts(String scope) throws Exception {
        jdbc.update("update script_episode set content='小满走进客厅，拿起怀表。', content_fingerprint='scope-fp' where id=?", episodeId);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var all = json.readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","name":"小满","aliases":[],"evidence":"小满","prompt":"角色原稿"}],
             "scenes":[{"localKey":"s1","name":"客厅","aliases":[],"evidence":"客厅","prompt":"场景原稿"}],
             "props":[{"localKey":"p1","name":"怀表","aliases":[],"evidence":"怀表","prompt":"道具原稿"}],
             "characterLooks":[],"propVariants":[]}
            """);
        ToolExecutionContext initial = summaryContext();
        service.readCurrentEpisode(initial);
        service.saveEpisodeAssets(initial, all);
        var bindings = jdbc.queryForList("select id, asset_type, retired_at from asset_visual_variant_episode where episode_id=? order by id", episodeId);
        for (String table : java.util.List.of("character_asset", "scene_asset", "prop_asset")) {
            jdbc.update("update " + table + " set source='USER', prompt='人工提示词' where script_id=?", scriptId);
        }
        var payload = ((com.fasterxml.jackson.databind.node.ObjectNode) all).deepCopy();
        var fields = java.util.Map.of("CHARACTER", "characters", "SCENE", "scenes", "PROP", "props");
        fields.forEach((type, field) -> {
            if (!scope.equals("ALL") && !scope.equals(type)) payload.putArray(field);
            else ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path(field).get(0)).put("prompt", "AI新稿");
        });
        ToolExecutionContext scoped = summaryContext();
        scoped.runState().put("assetScope", scope);
        scoped.runState().put("assetPromptPolicy", "FILL_EMPTY");
        service.readCurrentEpisode(scoped);
        service.saveEpisodeAssets(scoped, payload);
        for (String table : java.util.List.of("character_asset", "scene_asset", "prop_asset")) {
            assertThat(jdbc.queryForObject("select prompt from " + table + " where script_id=?", String.class, scriptId))
                .isEqualTo("人工提示词");
            assertThat(jdbc.queryForObject("select count(*) from " + table + " where script_id=? and deleted_at is null", Integer.class, scriptId)).isEqualTo(1);
        }
        for (var binding : bindings) {
            if (!scope.equals("ALL") && !scope.equals(binding.get("asset_type"))) {
                assertThat(jdbc.queryForObject("select count(*) from asset_visual_variant_episode where id=? and retired_at is null", Integer.class, binding.get("id"))).isEqualTo(1);
            }
        }
        if (!scope.equals("ALL")) {
            assertThatThrownBy(() -> service.saveEpisodeAssets(scoped, all))
                .isInstanceOf(com.antshorttv.common.BusinessException.class).hasMessageContaining("范围");
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"FILL_EMPTY", "REGENERATE_ALL"})
    void scopedPromptPolicyWritesOnlyItsDeclaredTarget(String policy) throws Exception {
        jdbc.update("update script_episode set content='小满出现。', content_fingerprint='prompt-fp' where id=?", episodeId);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        var payload = json.readTree("""
            {"schemaVersion":1,"characters":[{"localKey":"c1","name":"小满","aliases":[],"evidence":"小满","prompt":"原稿"}],
             "scenes":[],"props":[],"characterLooks":[],"propVariants":[]}
            """);
        ToolExecutionContext scoped = summaryContext();
        scoped.runState().put("assetScope", "CHARACTER");
        scoped.runState().put("assetPromptPolicy", policy);
        service.readCurrentEpisode(scoped);
        service.saveEpisodeAssets(scoped, payload);
        jdbc.update("update character_asset set prompt=? where script_id=?", policy.equals("FILL_EMPTY") ? "" : "已有稿", scriptId);
        ((com.fasterxml.jackson.databind.node.ObjectNode) payload.path("characters").get(0)).put("prompt", "新提示词");
        service.saveEpisodeAssets(scoped, payload);
        assertThat(jdbc.queryForObject("select prompt from character_asset where script_id=?", String.class, scriptId)).isEqualTo("新提示词");
    }

    @Test
    void rejectsMissingEvidenceAndRollsBackWholeAssetPayload() throws Exception {
        jdbc.update("update script_episode set content = '林小满出现。', content_fingerprint = 'rollback-fp' where id = ?",
            episodeId);
        ToolExecutionContext assetContext = summaryContext();
        service.readCurrentEpisode(assetContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林小满","aliases":[],"evidence":"林小满"}],
             "characterLooks":[],"scenes":[],
             "props":[{"localKey":"p1","assetKey":null,"name":"不存在的钥匙","aliases":[],"evidence":"钥匙","ownerCharacterLocalKey":null,"description":null}],
             "propVariants":[]}
            """);

        assertThatThrownBy(() -> service.saveEpisodeAssets(assetContext, payload))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("证据");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id = ?",
            Integer.class, scriptId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from script_episode_asset_analysis where episode_id = ?",
            Integer.class, episodeId)).isZero();
    }

    @Test
    void exactAliasReusePreservesTheExistingCanonicalIdentity() throws Exception {
        String content = "林小满，又名小满，走进房间。";
        jdbc.update("update script_episode set content = ?, content_fingerprint = 'alias-fp' where id = ?",
            content, episodeId);
        ToolExecutionContext firstContext = summaryContext();
        service.readCurrentEpisode(firstContext);
        service.saveEpisodeAssets(firstContext, new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林小满","aliases":[{"name":"小满","evidence":"小满"}],"evidence":"林小满","prompt":"林小满角色提示词"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """));

        ToolExecutionContext aliasContext = summaryContext();
        service.readCurrentEpisode(aliasContext);
        service.saveEpisodeAssets(aliasContext, new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c2","assetKey":null,"name":"小满","aliases":[],"evidence":"小满","prompt":"林小满角色提示词"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """));

        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id = ? and deleted_at is null",
            Integer.class, scriptId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from character_asset where script_id = ? and deleted_at is null",
            String.class, scriptId)).isEqualTo("林小满");
        assertThat(jdbc.queryForObject("select content_json from character_asset where script_id = ? and deleted_at is null",
            String.class, scriptId)).contains("小满");
    }

    @Test
    void trustedOpaqueKeyReusesOnlyTheCurrentScriptAsset() throws Exception {
        jdbc.update("update script_episode set content = '林小满出现。', content_fingerprint = 'trusted-fp' where id = ?",
            episodeId);
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status,
               source, created_by, created_at, updated_at)
            values (?, ?, ?, '林小满', '林小满', 'MAIN', 'CONFIRMED', 'USER', ?, now(), now())
            """, tenantId, projectId, scriptId, context.userId());
        long characterId = jdbc.queryForObject(
            "select id from character_asset where script_id = ?", Long.class, scriptId);
        ToolExecutionContext trustedContext = summaryContext();
        service.readCurrentEpisode(trustedContext);
        JsonNode result = service.saveEpisodeAssets(trustedContext,
            new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
                {"schemaVersion":1,
                 "characters":[{"localKey":"c1","assetKey":"c_%d","name":"林小满","aliases":[],"evidence":"林小满","prompt":"林小满角色提示词"}],
                 "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
                """.formatted(characterId)));

        assertThat(result.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
            "select count(*) from character_asset where script_id = ? and deleted_at is null",
            Integer.class, scriptId)).isEqualTo(1);
    }

    @Test
    void exactMatchingDoesNotCrossScriptScope() throws Exception {
        jdbc.update("update script_episode set content = '林小满出现。', content_fingerprint = 'scope-fp' where id = ?",
            episodeId);
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status,
               source, created_by, created_at, updated_at)
            values (?, ?, null, '林小满', '林小满', 'MAIN', 'CONFIRMED', 'USER', ?, now(), now())
            """, tenantId, projectId, context.userId());
        ToolExecutionContext scopedContext = summaryContext();
        service.readCurrentEpisode(scopedContext);
        service.saveEpisodeAssets(scopedContext, new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林小满","aliases":[],"evidence":"林小满","prompt":"林小满角色提示词"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """));

        assertThat(jdbc.queryForObject(
            "select count(*) from character_asset where project_id = ? and deleted_at is null",
            Integer.class, projectId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
            "select count(*) from character_asset where script_id = ? and deleted_at is null",
            Integer.class, scriptId)).isEqualTo(1);
    }

    @Test
    void multipleExactAliasesAreRejectedWithSafeOpaqueCandidates() throws Exception {
        jdbc.update("update script_episode set content = '小满出现。', content_fingerprint = 'ambiguous-fp' where id = ?",
            episodeId);
        for (String name : List.of("林小满", "陈小满")) {
            jdbc.update("""
                insert into character_asset
                  (tenant_id, project_id, script_id, name, normalized_name, role_type, status,
                   source, content_json, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'MAIN', 'CONFIRMED', 'USER',
                        '{"aliases":[{"name":"小满","evidence":"小满"}]}', ?, now(), now())
                """, tenantId, projectId, scriptId, name, name, context.userId());
        }
        ToolExecutionContext ambiguousContext = summaryContext();
        service.readCurrentEpisode(ambiguousContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"小满","aliases":[],"evidence":"小满"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """);

        assertThatThrownBy(() -> service.saveEpisodeAssets(ambiguousContext, payload))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .satisfies(error -> assertThat(((com.antshorttv.common.BusinessException) error).getErrorCode())
                .isEqualTo(com.antshorttv.common.ErrorCode.ENTITY_MATCH_AMBIGUOUS))
            .hasMessageContaining("c_");
    }

    @Test
    void concurrentNoMatchSavesCreateOneFormalIdentity() throws Exception {
        jdbc.update("update script_episode set content = '林小满出现。', content_fingerprint = 'concurrent-fp' where id = ?",
            episodeId);
        ToolExecutionContext first = summaryContext();
        ToolExecutionContext second = summaryContext();
        service.readCurrentEpisode(first);
        service.readCurrentEpisode(second);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林小满","aliases":[],"evidence":"林小满","prompt":"林小满角色提示词"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """);

        CompletableFuture.allOf(
            CompletableFuture.runAsync(() -> service.saveEpisodeAssets(first, payload)),
            CompletableFuture.runAsync(() -> service.saveEpisodeAssets(second, payload))
        ).join();

        assertThat(jdbc.queryForObject(
            "select count(*) from character_asset where script_id = ? and normalized_name = '林小满' and deleted_at is null",
            Integer.class, scriptId)).isEqualTo(1);
    }

    @Test
    void readsCurrentScriptAgainAfterOrdinaryEditAndRecordsTrustedHash() {
        ToolExecutionContext scriptContext = scriptContext();

        JsonNode first = service.readCurrentScript(scriptContext);
        jdbc.update("update script set content = 'edited content', updated_at = now() where id = ?", scriptId);
        JsonNode second = service.readCurrentScript(scriptContext);

        assertThat(first.path("content").asText()).isEqualTo("all episodes");
        assertThat(second.path("content").asText()).isEqualTo("edited content");
        assertThat(second.path("contentHash").asText()).isNotEqualTo(first.path("contentHash").asText());
        assertThat(scriptContext.runState().require("currentScriptHash", String.class))
            .isEqualTo(second.path("contentHash").asText());
    }

    @Test
    void readsTrustedCurrentEpisodeWithFingerprintAndCompactScriptAssetCatalog() {
        String source = "第2集：真相\r\n\r\n场景：夜 内 走廊\r\n△ Serena停下。\r\nSerena：谁在那里？";
        jdbc.update("update script_episode set content = ? where id = ?", source, episodeId);
        jdbc.update("update character_asset set script_id = ?, normalized_name = 'hero', source = 'USER' where project_id = ?",
            scriptId, projectId);
        ToolExecutionContext episodeContext = episodeContext();

        JsonNode episode = service.readCurrentEpisode(episodeContext);

        assertThat(episode.path("episodeKey").asText()).isEqualTo("episode-2");
        assertThat(episode.path("episodeNo").asInt()).isEqualTo(2);
        assertThat(episode.path("content").asText()).isEqualTo(source);
        assertThat(episode.path("contentFingerprint").asText()).isEqualTo("fingerprint-2");
        assertThat(episode.path("assetCatalog").path("characters")).hasSize(0);
        assertThat(episode.path("sourceSegments")).hasSize(4);
        assertThat(episode.path("sourceSegments").get(0).path("id").asText()).isEqualTo("S0001");
        assertThat(episode.path("sourceSegments").get(0).path("requiredCoverage").asBoolean()).isFalse();
        assertThat(episode.path("sourceSegments").get(1).path("type").asText()).isEqualTo("SCENE");
        assertThat(episode.path("sourceSegments").get(3).path("text").asText())
            .isEqualTo("Serena：谁在那里？");
        assertThat(episode.path("sourceSegments").get(3).has("startOffset")).isFalse();
        assertThat(episodeContext.runState().require("currentEpisodeId", Long.class)).isEqualTo(episodeId);
        assertThat(episodeContext.runState().require("currentEpisodeFingerprint", String.class))
            .isEqualTo("fingerprint-2");
        assertThat(episodeContext.runState().require("currentEpisodeSourceSegments", List.class))
            .hasSize(4);
        schemaValidator.validate(registry.require("read_current_episode").outputSchema(), episode);
    }

    @Test
    void rejectsNewAssetWithoutPromptAndRollsBackWholePayload() throws Exception {
        jdbc.update("update script_episode set content = '林小满出现。', content_fingerprint = 'prompt-required-fp' where id = ?",
            episodeId);
        ToolExecutionContext assetContext = summaryContext();
        service.readCurrentEpisode(assetContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林小满","aliases":[],"evidence":"林小满"}],
             "characterLooks":[],"scenes":[],"props":[],"propVariants":[]}
            """);

        assertThatThrownBy(() -> service.saveEpisodeAssets(assetContext, payload))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("提示词");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id = ?",
            Integer.class, scriptId)).isZero();
    }

    @Test
    void assetCatalogExposesPromptPresenceWithoutExposingPromptText() {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               prompt, created_by, created_at, updated_at)
            values (?, ?, ?, 'Serena', 'serena', 'LEAD', 'CONFIRMED', 'USER',
                    'private canonical prompt', ?, now(), now())
            """, tenantId, projectId, scriptId, context.userId());
        long assetId = jdbc.queryForObject("select id from character_asset where script_id = ?",
            Long.class, scriptId);
        jdbc.update("""
            insert into asset_visual_variant
              (tenant_id, project_id, asset_type, asset_id, name, prompt, source_type,
               generation_status, is_primary, created_by, created_at, updated_at)
            values (?, ?, 'CHARACTER', ?, '默认形象', 'private variant prompt', 'USER',
                    'NOT_GENERATED', true, ?, now(), now())
            """, tenantId, projectId, assetId, context.userId());

        JsonNode catalog = service.readCurrentEpisode(episodeContext()).path("assetCatalog");

        JsonNode character = catalog.path("characters").get(0);
        assertThat(character.path("hasPrompt").asBoolean()).isTrue();
        assertThat(character.path("variants").get(0).path("hasPrompt").asBoolean()).isTrue();
        assertThat(catalog.toString()).doesNotContain("private canonical prompt", "private variant prompt");
    }

    @Test
    void readsCurrentEpisodeWithTrustedSpeakerClassificationAndWarnings() {
        jdbc.update("""
            insert into character_asset
              (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
               content_json, created_by, created_at, updated_at)
            values (?, ?, ?, 'Serena', 'serena', 'LEAD', 'CONFIRMED', 'MANUAL',
                    '{"aliases":["Sera"]}', ?, now(), now())
            """, tenantId, projectId, scriptId, context.userId());
        String source = "出场人物：Serena、神秘人\n"
            + "▲ 镜头缓缓移向壁画：画中有人持剑。\n"
            + "Serena：别动。\n"
            + "Sera：听我说。\n"
            + "神秘人：轮到你了。";
        jdbc.update("update script_episode set content = ? where id = ?", source, episodeId);

        JsonNode episode = service.readCurrentEpisode(episodeContext());

        assertThat(episode.path("sourceSegments")).hasSize(5);
        assertThat(episode.path("sourceSegments").get(0).path("type").asText())
            .isEqualTo("METADATA");
        assertThat(episode.path("sourceSegments").get(1).path("type").asText())
            .isEqualTo("ACTION");
        assertThat(episode.path("sourceSegments").get(2).path("type").asText())
            .isEqualTo("DIALOGUE");
        assertThat(episode.path("sourceSegments").get(3).path("type").asText())
            .isEqualTo("DIALOGUE");
        assertThat(episode.path("sourceSegments").get(4).path("type").asText())
            .isEqualTo("ACTION");
        assertThat(episode.path("sourceSegments").get(4).path("classificationWarning").asText())
            .isEqualTo("SOURCE_SPEAKER_UNCONFIRMED");
        schemaValidator.validate(registry.require("read_current_episode").outputSchema(), episode);
    }

    @Test
    void analysisReadsOriginalDialogueIndependentlyOfCharacterCatalog() {
        String source = "第2集：真相\r\n时间：晚上\r\n镜头：拉近\r\n"
            + "Serena：别动。\r\nSera：听我说。\r\n神秘人：轮到你了。";
        jdbc.update("update script_episode set content = ? where id = ?", source, episodeId);
        JsonNode baseline = null;
        for (String name : List.of("", "Serena", "神秘人")) {
            if (!name.isEmpty()) {
                jdbc.update("""
                    insert into character_asset
                      (tenant_id, project_id, script_id, name, normalized_name, role_type, status, source,
                       content_json, created_by, created_at, updated_at)
                    values (?, ?, ?, ?, ?, 'LEAD', 'CONFIRMED', 'MANUAL',
                            '{"aliases":["Sera"]}', ?, now(), now())
                    """, tenantId, projectId, scriptId, name, name.toLowerCase(), context.userId());
            }
            ToolExecutionContext analysisContext = new ToolExecutionContext(
                tenantId, context.userId(), projectId, episodeId, scriptId, 100L, 101L, 777L,
                Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
            JsonNode episode = service.readCurrentEpisode(analysisContext);
            assertThat(episode.path("content").asText()).isEqualTo(source);
            assertThat(episode.path("sourceSegments")).extracting(value -> value.path("type").asText())
                .containsExactly("METADATA", "ACTION", "ACTION", "DIALOGUE", "DIALOGUE", "DIALOGUE");
            if (baseline == null) baseline = episode.path("sourceSegments");
            assertThat(episode.path("sourceSegments")).isEqualTo(baseline);
            schemaValidator.validate(registry.require("read_current_episode").outputSchema(), episode);
        }
    }

    @Test
    void trustedStoryboardExecutionReadsCurrentEpisodeWithoutHttpPrincipalPermissionLookup() {
        ToolExecutionContext storyboardContext = new ToolExecutionContext(
            tenantId, context.userId(), projectId, episodeId, scriptId, 9001L, null, 8001L,
            7001L, 6001L, 1, Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null,
            new WorkflowToolRunState());

        JsonNode episode = service.readCurrentEpisode(storyboardContext);

        assertThat(episode.path("episodeNo").asInt()).isEqualTo(2);
        assertThat(episode.path("classificationWarnings")).isEmpty();
        schemaValidator.validate(registry.require("read_current_episode").outputSchema(), episode);
        verify(permissionGuard, never()).require(anyLong(), anyLong(), anyString());
    }

    @Test
    void currentEpisodeReadRejectsCrossScriptAndInactiveEpisodes() {
        ToolExecutionContext wrongScript = new ToolExecutionContext(
            tenantId, context.userId(), projectId, episodeId, scriptId + 999, null, null, 777L,
            Set.of("SCRIPT:VIEW"), null, new WorkflowToolRunState());
        assertThatThrownBy(() -> service.readCurrentEpisode(wrongScript))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);

        jdbc.update("update script_episode set status = 'RETIRED', retired_at = now() where id = ?", episodeId);
        assertThatThrownBy(() -> service.readCurrentEpisode(episodeContext()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class);
    }

    @Test
    void currentEpisodeReadRejectsContentBeyondItsDeclaredBound() {
        jdbc.update("update script_episode set content = repeat('x', 200001) where id = ?", episodeId);

        assertThatThrownBy(() -> service.readCurrentEpisode(episodeContext()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("过长");
    }

    @Test
    void savesOneCurrentGlobalUnderstandingAndRejectsAStaleRead() throws Exception {
        ToolExecutionContext scriptContext = scriptContext();
        service.readCurrentScript(scriptContext);
        JsonNode content = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"logline":"一句话","synopsis":"简介","genres":[],"themes":[],
             "worldSetting":"","coreConflict":"冲突","relationships":[],
             "turningPoints":[],"ending":"","endingHook":"",
             "narrativeStyle":"快节奏","targetAudience":"大众"}
            """);

        JsonNode saved = service.saveGlobalUnderstanding(scriptContext, 1, content);

        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from script_global_understanding where script_id = ?",
            Integer.class, scriptId)).isEqualTo(1);

        jdbc.update("update script set content = 'changed while analyzing', updated_at = now() where id = ?", scriptId);
        assertThatThrownBy(() -> service.saveGlobalUnderstanding(scriptContext, 1, content))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("发生变化");
        assertThat(jdbc.queryForObject("select count(*) from script_global_understanding where script_id = ?",
            Integer.class, scriptId)).isEqualTo(1);
    }

    @Test
    void rejectsSaveWithoutReadingTheCurrentScriptFirst() throws Exception {
        JsonNode content = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"storyOverview":"概要","mainConflict":"冲突","worldSetting":"世界",
             "themes":["成长"],"relationships":[],"turningPoints":[],
             "narrativeStyle":"快节奏","targetAudience":"大众"}
            """);

        assertThatThrownBy(() -> service.saveGlobalUnderstanding(scriptContext(), 1, content))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("先读取");
    }

    @Test
    void rollsBackFormalDocumentWhenAnalysisStageCannotBeCommitted() throws Exception {
        ToolExecutionContext invalidStageContext = new ToolExecutionContext(
            tenantId, context.userId(), projectId, null, scriptId, 999999L, 888888L, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
        service.readCurrentScript(invalidStageContext);
        JsonNode content = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"storyOverview":"概要","mainConflict":"冲突","worldSetting":"世界",
             "themes":["成长"],"relationships":[],"turningPoints":[],
             "narrativeStyle":"快节奏","targetAudience":"大众"}
            """);

        assertThatThrownBy(() -> service.saveGlobalUnderstanding(invalidStageContext, 1, content))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("阶段不匹配");
        assertThat(jdbc.queryForObject(
            "select count(*) from script_global_understanding where script_id = ?",
            Integer.class, scriptId)).isZero();
    }

    @Test
    void savesExactCompleteEpisodeSplitAndKeepsReplayStable() throws Exception {
        String source = "简介\n第1集\nA\n\n第2集\nB\n";
        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        ToolExecutionContext scriptContext = scriptContext();
        service.readCurrentScript(scriptContext);
        JsonNode boundaries = splitBoundaries();

        JsonNode first = service.saveEpisodeSplitting(scriptContext, 1, boundaries);
        List<Long> firstIds = jdbc.queryForList("""
            select id from script_episode where script_id = ? and status = 'ACTIVE' and retired_at is null
             order by episode_no
            """, Long.class, scriptId);
        JsonNode replay = service.saveEpisodeSplitting(scriptContext, 1, boundaries);
        List<Long> replayIds = jdbc.queryForList("""
            select id from script_episode where script_id = ? and status = 'ACTIVE' and retired_at is null
             order by episode_no
            """, Long.class, scriptId);

        assertThat(first.path("saved").asBoolean()).isTrue();
        assertThat(replay.path("episodeCount").asInt()).isEqualTo(2);
        assertThat(replayIds).containsExactlyElementsOf(firstIds);
        assertThat(String.join("", jdbc.queryForList("""
            select content from script_episode
             where script_id = ? and status = 'ACTIVE' and retired_at is null order by episode_no
            """, String.class, scriptId))).isEqualTo(source);
    }

    @Test
    void savesReferencedEvidenceAndRejectsChangedEpisodeBeforeResolvingReferences() throws Exception {
        jdbc.update("update script_episode set content = ?, content_fingerprint = 'ref-fp' where id = ?",
            "第一集\r\n林小满穿着红裙。\n走进咖啡厅。", episodeId);
        ToolExecutionContext assetContext = summaryContext();
        service.readCurrentEpisode(assetContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,"maxItems":200,
             "characters":[{"localKey":"c1","name":"林小满","aliases":["小满"],"evidenceRef":{"segmentId":"S0002"},"prompt":"林小满角色提示词"}],
             "characterLooks":[{"localKey":"l1","characterLocalKey":"c1","name":"红裙","preferred":true,"evidenceRef":{"segmentId":"S0002"},"prompt":"性别:女；衣着描述:红裙"}],
             "scenes":[{"localKey":"s1","name":"咖啡厅","evidenceRef":{"segmentId":"S0003"},"usageEvidenceRef":{"segmentId":"S0003"},"prompt":"咖啡厅场景提示词"}]}
            """);
        assertThat(service.saveEpisodeAssets(assetContext, payload).path("saved").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select content_json from character_asset where script_id = ?",
            String.class, scriptId)).contains("林小满穿着红裙。");
        jdbc.update("update script_episode set content = '正文已修改' where id = ?", episodeId);
        assertThatThrownBy(() -> service.saveEpisodeAssets(assetContext, payload))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("发生变化");
    }

    @Test
    void reportsPreferredConflictAcrossLocalKeysResolvingToSameAssetWithPaths() throws Exception {
        jdbc.update("update script_episode set content = ?, content_fingerprint = 'preferred-fp' where id = ?",
            "林小满又名小满，穿红裙，后来换成白裙。", episodeId);
        ToolExecutionContext assetContext = summaryContext();
        service.readCurrentEpisode(assetContext);
        JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":1,"characters":[
              {"localKey":"c1","name":"林小满","aliases":[{"name":"小满","evidence":"又名小满"}],"evidence":"林小满","prompt":"林小满角色提示词"},
              {"localKey":"c2","name":"小满","aliases":[],"evidence":"小满","prompt":"林小满角色提示词"}],
             "characterLooks":[
              {"localKey":"l1","characterLocalKey":"c1","name":"红裙","evidence":"红裙","preferred":true,"prompt":"性别:女；衣着描述:红裙"},
              {"localKey":"l2","characterLocalKey":"c2","name":"白裙","evidence":"白裙","preferred":true,"prompt":"性别:女；衣着描述:白裙"}],
             "scenes":[],"props":[],"propVariants":[]}
            """);
        assertThatThrownBy(() -> service.saveEpisodeAssets(assetContext, payload))
            .hasMessageContaining("$.characterLooks[0].preferred")
            .hasMessageContaining("$.characterLooks[1].preferred");
        assertThat(jdbc.queryForObject("select count(*) from character_asset where script_id = ?",
            Integer.class, scriptId)).isZero();
    }

    @Test
    void splittingReadAndV2SaveUseDistinctSegmentIdsForRepeatedText() throws Exception {
        String source = "\r\n【黑场转场】\r\n\r\n【黑场转场】\r\n";
        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        ToolExecutionContext splitContext = scriptContext();
        splitContext.runState().put("scriptSegmentProtocol", true);
        JsonNode read = service.readCurrentScript(splitContext);
        schemaValidator.validate(registry.require("read_current_script").outputSchema(), read);
        assertThat(read.path("contentFormat").asText()).isEqualTo("SEGMENTED_V1");
        assertThat(read.path("content").asText()).contains("[S0001]", "[S0002]");
        assertThat(service.readCurrentScript(scriptContext()).path("content").asText()).isEqualTo(source);

        JsonNode input = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            {"schemaVersion":2,"episodes":[
              {"title":"开场","startSegmentId":"S0001","endSegmentId":"S0001"},
              {"title":"尾声","startSegmentId":"S0002","endSegmentId":"S0002"}]}
            """);
        schemaValidator.validate(registry.require("save_episode_splitting").inputSchema(), input);
        JsonNode saved = service.saveEpisodeSplitting(splitContext, 2, input.path("episodes"));
        assertThat(saved.path("episodeCount").asInt()).isEqualTo(2);
        assertThat(String.join("", jdbc.queryForList("""
            select content from script_episode where script_id = ? and status = 'ACTIVE'
              and retired_at is null order by episode_no
            """, String.class, scriptId))).isEqualTo(source);
        jdbc.update("update script set content = ? where id = ?", source + "变更", scriptId);
        assertThatThrownBy(() -> service.saveEpisodeSplitting(splitContext, 2, input.path("episodes")))
            .hasMessageContaining("发生变化");
    }

    @Test
    void segmentSaveAllowsNumberingGapsDuplicatesAndSplitHeadingsWithoutLosingText() throws Exception {
        String source = "\r\n第六集\r\n甲\r\n乙\r\n第八集\r\n丙\r\n第八集\r\n丁\r\n ";
        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        ToolExecutionContext splitContext = scriptContext();
        service.readCurrentScript(splitContext);
        JsonNode ranges = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            [{"title":"六上","startSegmentId":"S0001","endSegmentId":"S0002"},
             {"title":"六下","startSegmentId":"S0003","endSegmentId":"S0003"},
             {"title":"八","startSegmentId":"S0004","endSegmentId":"S0007"}]
            """);
        JsonNode saved = service.saveEpisodeSplitting(splitContext, 2, ranges);
        assertThat(saved.path("saved").asBoolean()).isTrue();
        assertThat(saved.path("episodeCount").asInt()).isEqualTo(3);
        assertThat(String.join("", jdbc.queryForList("""
            select content from script_episode where script_id = ? and status = 'ACTIVE'
             and retired_at is null order by episode_no
            """, String.class, scriptId))).isEqualTo(source);
    }

    @Test
    void segmentSaveRejectsGapsBeforeChangingFormalEpisodes() throws Exception {
        jdbc.update("update script set content = 'A\nB\nC' where id = ?", scriptId);
        ToolExecutionContext splitContext = scriptContext();
        service.readCurrentScript(splitContext);
        JsonNode ranges = new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            [{"title":"漏段","startSegmentId":"S0002","endSegmentId":"S0003"}]
            """);
        assertThatThrownBy(() -> service.saveEpisodeSplitting(splitContext, 2, ranges))
            .hasMessageContaining("连续覆盖");
        assertThat(jdbc.queryForObject("""
            select count(*) from script_episode where script_id = ? and status = 'ACTIVE'
              and retired_at is null
            """, Integer.class, scriptId)).isEqualTo(3);
    }

    @Test
    void splitSaveRejectsStaleSourceAndRollsBackWhenStageCannotCommit() throws Exception {
        String source = "简介\n第1集\nA\n\n第2集\nB\n";
        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        ToolExecutionContext stale = scriptContext();
        service.readCurrentScript(stale);
        int activeBefore = jdbc.queryForObject(
            "select count(*) from script_episode where script_id = ? and retired_at is null",
            Integer.class, scriptId);
        jdbc.update("update script set content = 'changed' where id = ?", scriptId);

        assertThatThrownBy(() -> service.saveEpisodeSplitting(stale, 1, splitBoundaries()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("发生变化");
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode where script_id = ? and retired_at is null",
            Integer.class, scriptId)).isEqualTo(activeBefore);

        jdbc.update("update script set content = ? where id = ?", source, scriptId);
        ToolExecutionContext invalidStage = new ToolExecutionContext(
            tenantId, context.userId(), projectId, null, scriptId, 999999L, 888888L, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
        service.readCurrentScript(invalidStage);
        assertThatThrownBy(() -> service.saveEpisodeSplitting(invalidStage, 1, splitBoundaries()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("阶段不匹配");
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode where script_id = ? and retired_at is null",
            Integer.class, scriptId)).isEqualTo(activeBefore);
    }

    @Test
    void summarySaveOverwritesFormalDocumentAndAllReadsUseIt() throws Exception {
        ToolExecutionContext summaryContext = summaryContext();
        service.readCurrentEpisode(summaryContext);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();

        JsonNode first = service.saveEpisodeSummary(summaryContext, 1, "第一版概要",
            json.readTree("[\"亮点一\",\"亮点二\"]"), json.nullNode());
        JsonNode second = service.saveEpisodeSummary(summaryContext, 1, "第二版概要",
            json.readTree("[\"新亮点一\",\"新亮点二\",\"新亮点三\"]"),
            json.getNodeFactory().textNode("结尾悬念"));

        assertThat(first.path("saved").asBoolean()).isTrue();
        assertThat(second.path("summaryId").asLong()).isEqualTo(first.path("summaryId").asLong());
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode_summary where episode_id = ?",
            Integer.class, episodeId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
            "select content_json from script_episode_summary where episode_id = ?",
            String.class, episodeId)).contains("第二版概要", "新亮点三", "结尾悬念")
            .doesNotContain("第一版概要");
        assertThat(service.readEpisodeScript(context).path("summary").asText())
            .isEqualTo("第二版概要");
        assertThat(service.listEpisodeScripts(context).path("episodes").get(1).path("summary").asText())
            .isEqualTo("第二版概要");
        assertThat(service.readProjectFullScript(context).path("episodes").get(1).path("summary").asText())
            .isEqualTo("第二版概要");
    }

    @Test
    void summarySaveRejectsMissingReadAndStaleEpisodeContent() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThatThrownBy(() -> service.saveEpisodeSummary(summaryContext(), 1, "概要",
            json.readTree("[\"一\",\"二\"]"), json.nullNode()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("先读取");

        ToolExecutionContext stale = summaryContext();
        service.readCurrentEpisode(stale);
        jdbc.update("update script_episode set content = '用户已修改' where id = ?", episodeId);
        assertThatThrownBy(() -> service.saveEpisodeSummary(stale, 1, "过期概要",
            json.readTree("[\"一\",\"二\"]"), json.nullNode()))
            .isInstanceOf(com.antshorttv.common.BusinessException.class)
            .hasMessageContaining("发生变化");
        assertThat(jdbc.queryForObject(
            "select count(*) from script_episode_summary where episode_id = ?",
            Integer.class, episodeId)).isZero();
    }

    private ToolExecutionContext scriptContext() {
        return new ToolExecutionContext(
            tenantId, context.userId(), projectId, null, scriptId, null, null, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
    }

    private ToolExecutionContext episodeContext() {
        return new ToolExecutionContext(
            tenantId, context.userId(), projectId, episodeId, scriptId, null, null, 777L,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
    }

    private ToolExecutionContext summaryContext() {
        return new ToolExecutionContext(
            tenantId, context.userId(), projectId, episodeId, scriptId, null, null, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
    }

    private JsonNode splitBoundaries() throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree("""
            [
              {"title":"第一集","startMarker":"第1集","endMarker":"A"},
              {"title":"第二集","startMarker":"第2集","endMarker":"B"}
            ]
            """);
    }

    private String screenplay(String dialogue) {
        return """
            ## S01 | 内景 · 咖啡厅 | 黄昏

            小明走进咖啡厅。

            小明：（微笑）%s
            """.formatted(dialogue);
    }
}
