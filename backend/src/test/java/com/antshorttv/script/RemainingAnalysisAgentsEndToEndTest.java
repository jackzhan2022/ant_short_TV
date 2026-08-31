package com.antshorttv.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.project.ProjectAccessContext;
import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.project.ProjectAccessSource;
import com.antshorttv.project.ProjectCapabilities;
import com.antshorttv.project.ProjectEntity;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import com.antshorttv.workflowagent.agent.WorkflowAgentRecord;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import com.antshorttv.workflowagent.tool.ScreenplayToolDataService;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.antshorttv.workflowagent.tool.WorkflowToolRunState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RemainingAnalysisAgentsEndToEndTest {
    @Autowired private ScreenplayToolDataService tools;
    @Autowired private ScriptWorkflowService workspaceService;
    @Autowired private EpisodeFanoutStore fanoutStore;
    @Autowired private EpisodeFanoutCoordinator fanoutCoordinator;
    @Autowired private AssetRecognitionFinalizer assetFinalizer;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockBean private TenantContextResolver tenantContextResolver;
    @MockBean private ProjectAccessResolver projectAccessResolver;
    @MockBean private WorkflowAgentRunner agentRunner;
    @MockBean private ProjectPermissionGuard projectPermissionGuard;

    private long tenantId;
    private long userId;
    private long projectId;
    private long scriptId;
    private long scriptVersionId;
    private long modelId;

    @BeforeEach
    void setUp() {
        long seed = Math.abs(UUID.randomUUID().getMostSignificantBits() % 500_000_000L) + 20_000_000L;
        tenantId = seed;
        userId = seed + 1;
        projectId = seed + 2;
        modelId = jdbc.queryForObject("select min(id) from ai_model", Long.class);
        String source = "序幕：林夏穿日常装走进客厅，握着完好怀表。\n"
            + "转场：林夏换上晚礼服回到客厅，怀表变成破损状态。";

        jdbc.update("""
            insert into project
              (id, tenant_id, name, code, owner_id, status, created_by, created_at, updated_at)
            values (?, ?, '剩余分析 Agent 端到端项目', ?, ?, 'ACTIVE', ?, now(), now())
            """, projectId, tenantId, "REMAINING_E2E_" + seed, userId, userId);
        jdbc.update("""
            insert into script
              (tenant_id, project_id, title, source_type, content, status, created_by, created_at, updated_at)
            values (?, ?, '无显式集号的短剧', 'MANUAL_EDIT', ?, 'DRAFT', ?, now(), now())
            """, tenantId, projectId, source, userId);
        scriptId = jdbc.queryForObject("select id from script where project_id = ?", Long.class, projectId);
        jdbc.update("""
            insert into script_version
              (tenant_id, project_id, script_id, version_no, source_type, content, status, created_by, created_at)
            values (?, ?, ?, 1, 'MANUAL_EDIT', ?, 'DRAFT', ?, now())
            """, tenantId, projectId, scriptId, source, userId);
        scriptVersionId = jdbc.queryForObject(
            "select id from script_version where script_id = ?", Long.class, scriptId);
        jdbc.update("update script set current_version_id = ? where id = ?", scriptVersionId, scriptId);

        TenantContext tenant = new TenantContext(userId, tenantId, seed + 3, "OWNER");
        ProjectEntity project = new ProjectEntity();
        project.id = projectId;
        project.tenantId = tenantId;
        project.name = "剩余分析 Agent 端到端项目";
        ProjectAccessContext access = new ProjectAccessContext(
            tenant, project, ProjectAccessSource.TENANT_WIDE, null, null,
            Set.of("PROJECT:VIEW_ALL"), new ProjectCapabilities(true, true, true, true, true));
        when(tenantContextResolver.requireActiveMember(tenantId)).thenReturn(tenant);
        when(projectAccessResolver.requireView(tenantId, projectId)).thenReturn(access);
    }

    @Test
    void multiEpisodeFlowPersistsStableFormalDataAndRestoresProgress() throws Exception {
        JsonNode boundaries = boundaries();
        ToolExecutionContext split = scriptContext();
        tools.readCurrentScript(split);
        tools.saveEpisodeSplitting(split, 1, boundaries);
        List<Long> firstIds = activeEpisodeIds();

        tools.saveEpisodeSplitting(split, 1, boundaries);
        assertThat(activeEpisodeIds()).containsExactlyElementsOf(firstIds);
        assertThat(firstIds).hasSize(2);

        saveSummary(firstIds.get(0), "林夏带着完好怀表进入客厅。", "怀表仍然完好");
        saveSummary(firstIds.get(1), "林夏变装后发现怀表已经破损。", "怀表为何破损");
        saveAssets(firstIds.get(0), """
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"林夏","aliases":[],"evidence":"林夏"}],
             "characterLooks":[{"localKey":"l1","characterLocalKey":"c1","variantKey":null,"name":"日常装","description":"日常装","evidence":"日常装","preferred":true}],
             "scenes":[{"localKey":"s1","assetKey":null,"name":"客厅","aliases":[],"evidence":"客厅","description":null,"timeAtmosphere":null,"usageEvidence":"走进客厅"}],
             "props":[{"localKey":"p1","assetKey":null,"name":"怀表","aliases":[],"evidence":"怀表","ownerCharacterLocalKey":"c1","description":null}],
             "propVariants":[{"localKey":"v1","propLocalKey":"p1","variantKey":null,"name":"完好","description":"完好","evidence":"完好怀表","preferred":true}]}
            """);
        saveAssets(firstIds.get(1), """
            {"schemaVersion":1,
             "characters":[{"localKey":"c2","assetKey":null,"name":"林夏","aliases":[],"evidence":"林夏"}],
             "characterLooks":[{"localKey":"l2","characterLocalKey":"c2","variantKey":null,"name":"晚礼服","description":"晚礼服","evidence":"晚礼服","preferred":true}],
             "scenes":[{"localKey":"s2","assetKey":null,"name":"客厅","aliases":[],"evidence":"客厅","description":null,"timeAtmosphere":null,"usageEvidence":"回到客厅"}],
             "props":[{"localKey":"p2","assetKey":null,"name":"怀表","aliases":[],"evidence":"怀表","ownerCharacterLocalKey":"c2","description":null}],
             "propVariants":[{"localKey":"v2","propLocalKey":"p2","variantKey":null,"name":"破损","description":"破损","evidence":"破损状态","preferred":true}]}
            """);

        assertThat(count("character_asset", "script_id = " + scriptId)).isEqualTo(1);
        assertThat(count("scene_asset", "script_id = " + scriptId)).isEqualTo(1);
        assertThat(count("prop_asset", "script_id = " + scriptId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
            select count(*) from asset_visual_variant variant
             join character_asset asset on asset.id = variant.asset_id
            where variant.asset_type = 'CHARACTER' and asset.script_id = ? and variant.deleted_at is null
            """, Integer.class, scriptId)).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject("""
            select count(*) from asset_visual_variant_episode
             where script_id = ? and asset_type = 'PROP' and retired_at is null
            """, Integer.class, scriptId)).isEqualTo(2);

        AnalysisScope scope = createAnalysisScope("EPISODE_SUMMARY");
        WorkflowAgentExecutionPlan plan = plan("short-drama-episode-summary");
        List<EpisodeFanoutCoordinator.EpisodeUnit> units = fanoutStore.currentEpisodes(
            tenantId, projectId, scriptId);
        long snapshotId = fanoutStore.openSnapshot(
            scope.task(), scope.stage(), "short-drama-episode-summary", plan, units,
            EpisodeFanoutCoordinator.episodeSetHash(units), false);
        units.forEach(unit -> {
            fanoutStore.markRunning(snapshotId, unit.episodeId());
            fanoutStore.markSucceeded(snapshotId, unit.episodeId(), null);
        });
        EpisodeFanoutCoordinator.Progress restored = fanoutStore.progress(snapshotId);
        fanoutStore.updateParentProgress(snapshotId, restored);
        assertThat(fanoutStore.progress(snapshotId).status()).isEqualTo("SUCCEEDED");
        assertThat(fanoutStore.runnableUnits(snapshotId)).isEmpty();

        ScriptWorkspaceResponse workspace = workspaceService.workspace(tenantId, projectId);
        assertThat(workspace.episodes()).hasSize(2)
            .allSatisfy(episode -> assertThat(episode.formalSummary()).isNotNull());
        assertThat(workspace.characters()).singleElement()
            .satisfies(character -> assertThat(character.visual().variants()).hasSizeGreaterThanOrEqualTo(2));
        assertThat(workspace.props()).singleElement()
            .satisfies(prop -> assertThat(prop.visual().episodeBindings()).hasSize(2));
        assertThat(workspace.analysis().stages()).singleElement()
            .satisfies(stage -> assertThat(stage.fanout().completed()).isEqualTo(2));
    }

    @Test
    void failuresRollbackAndRetryOnlyTheFailedEpisodeWithoutPrematureRetirement() throws Exception {
        JsonNode boundaries = boundaries();
        ToolExecutionContext initial = scriptContext();
        tools.readCurrentScript(initial);
        tools.saveEpisodeSplitting(initial, 1, boundaries);
        List<Long> stableIds = activeEpisodeIds();

        ToolExecutionContext stale = scriptContext();
        tools.readCurrentScript(stale);
        jdbc.update("update script set content = concat(content, '用户修改') where id = ?", scriptId);
        assertThatThrownBy(() -> tools.saveEpisodeSplitting(stale, 1, boundaries))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.SCRIPT_CONTENT_CHANGED);
        assertThat(activeEpisodeIds()).containsExactlyElementsOf(stableIds);

        jdbc.update("update script set content = replace(content, '用户修改', '') where id = ?", scriptId);
        ToolExecutionContext invalid = scriptContext();
        tools.readCurrentScript(invalid);
        JsonNode invalidCoverage = json.readTree("""
            [{"title":"缺失覆盖","startMarker":"转场：","endMarker":"不存在的结束锚点"}]
            """);
        assertThatThrownBy(() -> tools.saveEpisodeSplitting(invalid, 1, invalidCoverage))
            .isInstanceOf(BusinessException.class);
        assertThat(activeEpisodeIds()).containsExactlyElementsOf(stableIds);

        long episodeId = stableIds.get(0);
        jdbc.update("update script_episode set content = '小满走进仓库。', content_fingerprint = 'ambiguous-e2e' where id = ?",
            episodeId);
        for (String name : List.of("林小满", "陈小满")) {
            jdbc.update("""
                insert into character_asset
                  (tenant_id, project_id, script_id, name, normalized_name, role_type, status,
                   source, content_json, created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, 'MAIN', 'CONFIRMED', 'USER',
                        '{"aliases":[{"name":"小满","evidence":"小满"}]}', ?, now(), now())
                """, tenantId, projectId, scriptId, name, name, userId);
        }
        ToolExecutionContext ambiguous = episodeContext(episodeId);
        tools.readCurrentEpisode(ambiguous);
        JsonNode ambiguousPayload = json.readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","assetKey":null,"name":"小满","aliases":[],"evidence":"小满"}],
             "characterLooks":[],
             "scenes":[{"localKey":"s1","assetKey":null,"name":"仓库","aliases":[],"evidence":"仓库","description":null,"timeAtmosphere":null,"usageEvidence":"走进仓库"}],
             "props":[],"propVariants":[]}
            """);
        assertThatThrownBy(() -> tools.saveEpisodeAssets(ambiguous, ambiguousPayload))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.ENTITY_MATCH_AMBIGUOUS);
        assertThat(count("scene_asset", "script_id = " + scriptId)).isZero();
        assertThat(count("script_episode_asset_analysis", "episode_id = " + episodeId)).isZero();

        AnalysisScope scope = createAnalysisScope("ASSET_RECOGNITION");
        WorkflowAgentExecutionPlan plan = plan("short-drama-asset-recognition");
        when(agentRunner.freezeFormal("short-drama-asset-recognition")).thenReturn(plan);
        long childRunId = createRun("short-drama-asset-recognition");
        AtomicInteger firstPass = new AtomicInteger();
        AtomicInteger finalized = new AtomicInteger();
        assertThatThrownBy(() -> fanoutCoordinator.execute(
            scope.task(), scope.stage(), null, "short-drama-asset-recognition", false,
            (frozen, task, stage, execution, unit) -> {
                firstPass.incrementAndGet();
                if (unit.episodeId().equals(stableIds.get(1))) {
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "单集模型调用失败");
                }
                return new EpisodeFanoutCoordinator.ChildResult(childRunId, List.of());
            }, ignored -> finalized.incrementAndGet()))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.ANALYSIS_AGENT_INCOMPLETE);
        assertThat(firstPass.get()).isEqualTo(2);
        assertThat(finalized.get()).isZero();

        long snapshotId = jdbc.queryForObject(
            "select id from script_analysis_fanout_snapshot where stage_id = ?", Long.class, scope.stage().getId());
        jdbc.update("update script_analysis_fanout_snapshot set status = 'RUNNING' where id = ?", snapshotId);
        long oldRunId = createRun("short-drama-asset-recognition");
        jdbc.update("""
            insert into prop_asset
              (tenant_id, project_id, script_id, name, normalized_name, prop_type, status,
               source, generated_by_run_id, created_by, created_at, updated_at)
            values (?, ?, ?, '旧怀表', '旧怀表', 'KEY_ITEM', 'CONFIRMED', 'AI', ?, ?, now(), now())
            """, tenantId, projectId, scriptId, oldRunId, userId);
        long oldPropId = jdbc.queryForObject(
            "select id from prop_asset where script_id = ? and name = '旧怀表'", Long.class, scriptId);
        assertThatThrownBy(() -> assetFinalizer.finish(snapshotId))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).getErrorCode())
            .isEqualTo(ErrorCode.ANALYSIS_AGENT_INCOMPLETE);
        assertThat(jdbc.queryForObject(
            "select deleted_at is null from prop_asset where id = ?", Boolean.class, oldPropId)).isTrue();

        AtomicInteger retryRuns = new AtomicInteger();
        fanoutCoordinator.execute(scope.task(), scope.stage(), null,
            "short-drama-asset-recognition", false,
            (frozen, task, stage, execution, unit) -> {
                retryRuns.incrementAndGet();
                return new EpisodeFanoutCoordinator.ChildResult(createRun("short-drama-asset-recognition"), List.of());
            }, ignored -> finalized.incrementAndGet());
        assertThat(retryRuns.get()).isEqualTo(1);
        assertThat(finalized.get()).isEqualTo(1);
        assertThat(fanoutStore.progress(snapshotId).status()).isEqualTo("SUCCEEDED");
    }

    private JsonNode boundaries() throws Exception {
        return json.readTree("""
            [
              {"title":"序幕","startMarker":"序幕：","endMarker":"完好怀表。"},
              {"title":"转场","startMarker":"转场：","endMarker":"破损状态。"}
            ]
            """);
    }

    private void saveSummary(long episodeId, String summary, String hook) throws Exception {
        ToolExecutionContext context = episodeContext(episodeId);
        tools.readCurrentEpisode(context);
        tools.saveEpisodeSummary(context, 1, summary,
            json.readTree("[\"关键推进\",\"人物变化\"]"), json.getNodeFactory().textNode(hook));
    }

    private void saveAssets(long episodeId, String payload) throws Exception {
        ToolExecutionContext context = episodeContext(episodeId);
        tools.readCurrentEpisode(context);
        tools.saveEpisodeAssets(context, json.readTree(payload));
    }

    private ToolExecutionContext scriptContext() {
        return new ToolExecutionContext(
            tenantId, userId, projectId, null, scriptId, null, null, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
    }

    private ToolExecutionContext episodeContext(long episodeId) {
        return new ToolExecutionContext(
            tenantId, userId, projectId, episodeId, scriptId, null, null, null,
            Set.of("SCRIPT:VIEW", "SCRIPT:EDIT"), null, new WorkflowToolRunState());
    }

    private List<Long> activeEpisodeIds() {
        return jdbc.queryForList("""
            select id from script_episode
             where script_id = ? and status = 'ACTIVE' and retired_at is null order by episode_no
            """, Long.class, scriptId);
    }

    private int count(String table, String predicate) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }

    private AnalysisScope createAnalysisScope(String stageCode) {
        jdbc.update("""
            insert into script_analysis_task
              (tenant_id, project_id, script_id, script_version_id, workflow_code, status,
               overall_progress, idempotency_key, created_by, created_at, updated_at)
            values (?, ?, ?, ?, 'short-drama-analysis', 'RUNNING', 0, ?, ?, now(), now())
            """, tenantId, projectId, scriptId, scriptVersionId, UUID.randomUUID().toString(), userId);
        long taskId = jdbc.queryForObject(
            "select max(id) from script_analysis_task where script_id = ?", Long.class, scriptId);
        jdbc.update("""
            insert into script_analysis_stage
              (task_id, stage_code, stage_order, status, progress_percent, completed_units,
               total_units, attempt_no, retryable, created_at, updated_at)
            values (?, ?, 1, 'RUNNING', 0, 0, 0, 1, false, now(), now())
            """, taskId, stageCode);
        long stageId = jdbc.queryForObject(
            "select id from script_analysis_stage where task_id = ? and stage_code = ?",
            Long.class, taskId, stageCode);
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(taskId);
        task.setTenantId(tenantId);
        task.setProjectId(projectId);
        task.setScriptId(scriptId);
        task.setScriptVersionId(scriptVersionId);
        task.setCreatedBy(userId);
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setId(stageId);
        stage.setTaskId(taskId);
        stage.setStageCode(stageCode);
        stage.setAttemptNo(1);
        return new AnalysisScope(task, stage);
    }

    private WorkflowAgentExecutionPlan plan(String agentCode) {
        return new WorkflowAgentExecutionPlan(new WorkflowAgentRecord(
            1L, agentCode, agentCode, null, "按当前正式数据执行", modelId,
            new BigDecimal("0.2"), 4096, 8, "ENABLED", 1L, userId, userId,
            LocalDateTime.now(), LocalDateTime.now(), List.of("foundation", "framework"),
            List.of("read_current_episode", "save")), List.of());
    }

    private long createRun(String agentCode) {
        jdbc.update("""
            insert into ai_workflow_agent_run
              (agent_code, run_type, tenant_id, user_id, project_id, episode_id, script_id,
               status, model_id, temperature, max_tokens, max_steps, prompt_snapshot,
               started_at, finished_at, created_at)
            values (?, 'EPISODE', ?, ?, ?, null, ?, 'SUCCESS', ?, 0.2, 4096, 8,
                    'e2e', now(), now(), now())
            """, agentCode, tenantId, userId, projectId, scriptId, modelId);
        return jdbc.queryForObject(
            "select max(id) from ai_workflow_agent_run where tenant_id = ?", Long.class, tenantId);
    }

    private record AnalysisScope(ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage) {}
}
