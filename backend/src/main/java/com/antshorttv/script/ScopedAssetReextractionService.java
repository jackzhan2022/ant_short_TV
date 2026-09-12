package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.agent.AssetRecognitionAgentBootstrap;
import com.antshorttv.workflowagent.run.WorkflowAgentExecutionPlan;
import com.antshorttv.workflowagent.run.WorkflowAgentModelCall;
import com.antshorttv.workflowagent.run.WorkflowAgentRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ScopedAssetReextractionService {
    private final JdbcTemplate jdbc;
    private final WorkflowAgentRunner runner;
    private final AssetRecognitionAgentAdapter recognition;

    ScopedAssetReextractionService(
        JdbcTemplate jdbc,
        WorkflowAgentRunner runner,
        AssetRecognitionAgentAdapter recognition
    ) {
        this.jdbc = jdbc;
        this.runner = runner;
        this.recognition = recognition;
    }

    AssetReextractionPreflight preflight(long tenantId, long projectId, long scriptId,
                                         AssetRecognitionScope scope) {
        int assets = 0;
        int variants = 0;
        int prompts = 0;
        for (String type : types(scope)) {
            String table = table(type);
            assets += count("select count(*) from " + table
                + " where tenant_id=? and project_id=? and script_id=? and deleted_at is null",
                tenantId, projectId, scriptId);
            variants += count("select count(*) from asset_visual_variant variant join " + table
                + " asset on asset.id=variant.asset_id where variant.tenant_id=? and variant.project_id=?"
                + " and variant.asset_type=? and variant.deleted_at is null and asset.script_id=?",
                tenantId, projectId, type, scriptId);
            prompts += count("select count(*) from " + table
                + " where tenant_id=? and project_id=? and script_id=? and deleted_at is null"
                + " and prompt is not null and trim(prompt) <> ''", tenantId, projectId, scriptId);
            prompts += count("select count(*) from asset_visual_variant variant join " + table
                + " asset on asset.id=variant.asset_id where variant.tenant_id=? and variant.project_id=?"
                + " and variant.asset_type=? and variant.deleted_at is null and asset.script_id=?"
                + " and variant.prompt is not null and trim(variant.prompt) <> ''",
                tenantId, projectId, type, scriptId);
        }
        return new AssetReextractionPreflight(scope.name(), assets, variants, prompts,
            assets + variants + prompts > 0);
    }

    ScriptAiOperationExecutionResult execute(
        ScriptAiOperationEntity operation,
        ScopedAssetReextractionRequest request,
        AiExecutionContext executionContext
    ) {
        AssetRecognitionScope scope = parseScope(request.targetType());
        AssetPromptPolicy policy = parsePolicy(request.promptPolicy());
        Snapshot snapshot = loadOrCreate(operation, scope, policy, modelId(executionContext));
        WorkflowAgentExecutionPlan plan = runner.freezeFormal(AssetRecognitionAgentBootstrap.AGENT_CODE);
        List<WorkflowAgentModelCall> calls = new ArrayList<>();
        for (Unit unit : runnableUnits(snapshot.id())) {
            jdbc.update("update scoped_asset_reextraction_unit set status='RUNNING', started_at=now(), updated_at=now() where id=?", unit.id());
            try {
                ScriptAnalysisTaskEntity task = transientTask(operation);
                ScriptAnalysisStageEntity stage = transientStage();
                AssetRecognitionAgentAdapter.Execution child = recognition.executeChild(plan, task, stage,
                    unit.episodeId(), executionContext, snapshot.modelId(), scope, policy);
                jdbc.update("update scoped_asset_reextraction_unit set status='SUCCEEDED', child_run_id=?, error_message=null, finished_at=now(), updated_at=now() where id=?",
                    child.agentRunId(), unit.id());
                calls.addAll(child.modelCalls());
            } catch (RuntimeException failure) {
                jdbc.update("update scoped_asset_reextraction_unit set status='FAILED', error_message=?, finished_at=now(), updated_at=now() where id=?",
                    message(failure), unit.id());
                refresh(snapshot.id(), "FAILED");
                throw failure;
            }
            refresh(snapshot.id(), "RUNNING");
        }
        if (count("select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status <> 'SUCCEEDED'", snapshot.id()) != 0) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE, "仍有剧集未完成资产重提取，不能收口旧资产。");
        }
        finalizeScope(snapshot, scope);
        refresh(snapshot.id(), "SUCCEEDED");
        return new ScriptAiOperationExecutionResult("SCOPED_ASSET_REEXTRACTION", snapshot.id(), List.of(), calls);
    }

    private Snapshot loadOrCreate(
        ScriptAiOperationEntity operation,
        AssetRecognitionScope scope,
        AssetPromptPolicy policy,
        Long modelId
    ) {
        List<Snapshot> existing = jdbc.query("select id, tenant_id, project_id, script_id, model_id from scoped_asset_reextraction_snapshot where operation_id=? for update",
            (rs, row) -> new Snapshot(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("project_id"),
                rs.getLong("script_id"), rs.getObject("model_id", Long.class)), operation.id);
        if (!existing.isEmpty()) {
            Snapshot snapshot = existing.get(0);
            if (snapshot.modelId() != null) return snapshot;
            requireModel(modelId);
            jdbc.update("update scoped_asset_reextraction_snapshot set model_id=?, updated_at=now() where id=?",
                modelId, snapshot.id());
            return new Snapshot(snapshot.id(), snapshot.tenantId(), snapshot.projectId(), snapshot.scriptId(), modelId);
        }
        requireModel(modelId);
        List<Episode> episodes = jdbc.query("select id, stable_key, content_fingerprint from script_episode where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null order by episode_no, id",
            (rs, row) -> new Episode(rs.getLong("id"), rs.getString("stable_key"), rs.getString("content_fingerprint")),
            operation.tenantId, operation.projectId, operation.scriptId);
        if (episodes.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前项目没有可识别的正式剧集。");
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("insert into scoped_asset_reextraction_snapshot (operation_id, tenant_id, project_id, script_id, asset_scope, prompt_policy, model_id, status, total_units, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, 'RUNNING', ?, now(), now())", java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, operation.id); statement.setLong(2, operation.tenantId); statement.setLong(3, operation.projectId);
            statement.setLong(4, operation.scriptId); statement.setString(5, scope.name()); statement.setString(6, policy.name()); statement.setLong(7, modelId); statement.setInt(8, episodes.size());
            return statement;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("无法创建资产重提取快照。");
        for (Episode episode : episodes) {
            jdbc.update("insert into scoped_asset_reextraction_unit (snapshot_id, episode_id, episode_key, content_fingerprint, status, created_at, updated_at) values (?, ?, ?, ?, 'PENDING', now(), now())",
                id.longValue(), episode.id(), episode.key(), episode.fingerprint());
        }
        return new Snapshot(id.longValue(), operation.tenantId, operation.projectId, operation.scriptId, modelId);
    }

    private List<Unit> runnableUnits(long snapshotId) {
        return jdbc.query("select id, episode_id from scoped_asset_reextraction_unit where snapshot_id=? and status in ('PENDING','FAILED') order by id",
            (rs, row) -> new Unit(rs.getLong("id"), rs.getLong("episode_id")), snapshotId);
    }

    private void refresh(long snapshotId, String status) {
        jdbc.update("update scoped_asset_reextraction_snapshot set status=?, completed_units=(select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status='SUCCEEDED'), failed_units=(select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status='FAILED'), finished_at=case when ?='SUCCEEDED' then now() else null end, updated_at=now() where id=?",
            status, snapshotId, snapshotId, status, snapshotId);
    }

    private void finalizeScope(Snapshot snapshot, AssetRecognitionScope scope) {
        for (String type : types(scope)) {
            String table = table(type);
            jdbc.update("update asset_visual_variant variant join " + table + " asset on asset.id=variant.asset_id set variant.deleted_at=now(), variant.updated_at=now() where variant.tenant_id=? and variant.project_id=? and variant.asset_type=? and variant.generated_by_run_id is not null and variant.deleted_at is null and asset.script_id=? and not exists (select 1 from asset_visual_variant_episode binding where binding.variant_id=variant.id and binding.retired_at is null and binding.binding_status='ACTIVE')", snapshot.tenantId(), snapshot.projectId(), type, snapshot.scriptId());
            jdbc.update("update " + table + " asset set deleted_at=now(), updated_at=now() where asset.tenant_id=? and asset.project_id=? and asset.script_id=? and asset.source='AI' and asset.deleted_at is null and not exists (select 1 from asset_visual_variant_episode binding where binding.asset_type=? and binding.asset_id=asset.id and binding.retired_at is null and binding.binding_status='ACTIVE')", snapshot.tenantId(), snapshot.projectId(), snapshot.scriptId(), type);
        }
    }

    private ScriptAnalysisTaskEntity transientTask(ScriptAiOperationEntity operation) {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setTenantId(operation.tenantId); task.setProjectId(operation.projectId); task.setScriptId(operation.scriptId); task.setCreatedBy(operation.createdBy);
        return task;
    }

    private ScriptAnalysisStageEntity transientStage() {
        ScriptAnalysisStageEntity stage = new ScriptAnalysisStageEntity();
        stage.setStageCode("SCOPED_ASSET_REEXTRACTION");
        return stage;
    }

    private AssetRecognitionScope parseScope(String value) {
        try { return AssetRecognitionScope.valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产提取范围必须为 ALL、CHARACTER、SCENE 或 PROP。"); }
    }

    private Long modelId(AiExecutionContext context) {
        return context.task().resolvedModelId == null
            ? context.task().requestedModelId
            : context.task().resolvedModelId;
    }

    private void requireModel(Long modelId) {
        if (modelId == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产重提取任务缺少冻结文本模型。");
        }
    }

    private AssetPromptPolicy parsePolicy(String value) {
        try { return AssetPromptPolicy.valueOf(value == null ? "" : value.trim().toUpperCase()); }
        catch (IllegalArgumentException exception) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "资产提示词策略必须为 FILL_EMPTY 或 REGENERATE_ALL。"); }
    }

    private List<String> types(AssetRecognitionScope scope) {
        return scope == AssetRecognitionScope.ALL ? List.of("CHARACTER", "SCENE", "PROP") : List.of(scope.name());
    }

    private String table(String type) { return switch (type) { case "CHARACTER" -> "character_asset"; case "SCENE" -> "scene_asset"; case "PROP" -> "prop_asset"; default -> throw new IllegalArgumentException("Unsupported asset type"); }; }
    private int count(String sql, Object... args) { Integer value = jdbc.queryForObject(sql, Integer.class, args); return value == null ? 0 : value; }
    private String message(RuntimeException failure) { String value = failure.getMessage(); return value == null ? failure.getClass().getSimpleName() : value.substring(0, Math.min(1000, value.length())); }

    record AssetReextractionPreflight(
        String targetType,
        int existingAssets,
        int existingVariants,
        int existingPrompts,
        boolean requiresConfirmation
    ) {}
    private record Snapshot(long id, long tenantId, long projectId, long scriptId, Long modelId) {}
    private record Episode(long id, String key, String fingerprint) {}
    private record Unit(long id, long episodeId) {}
}
