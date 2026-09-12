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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class ScopedAssetReextractionService {
    private final JdbcTemplate jdbc;
    private final WorkflowAgentRunner runner;
    private final AssetRecognitionAgentAdapter recognition;
    private final TransactionTemplate transactions;
    private final AssetExtractionCoordination coordination;
    private final com.antshorttv.workflowagent.run.WorkflowAgentRunRepository runs;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    ScopedAssetReextractionService(
        JdbcTemplate jdbc,
        WorkflowAgentRunner runner,
        AssetRecognitionAgentAdapter recognition,
        PlatformTransactionManager transactionManager,
        AssetExtractionCoordination coordination,
        com.antshorttv.workflowagent.run.WorkflowAgentRunRepository runs,
        com.fasterxml.jackson.databind.ObjectMapper json
    ) {
        this.jdbc = jdbc;
        this.runner = runner;
        this.recognition = recognition;
        this.transactions = new TransactionTemplate(transactionManager);
        this.coordination = coordination;
        this.runs = runs;
        this.json = json;
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
        coordination.acquire(operation.scriptId,executionContext);
        // Keep ownership through settlement and retry. Admission reclaims terminal owners.
        return executeOwned(operation,request,executionContext);
    }

    private ScriptAiOperationExecutionResult executeOwned(
        ScriptAiOperationEntity operation,ScopedAssetReextractionRequest request,AiExecutionContext executionContext
    ) {
        AssetRecognitionScope scope = parseScope(request.targetType());
        AssetPromptPolicy policy = parsePolicy(request.promptPolicy());
        Snapshot snapshot = transaction(() -> {
            requireOwner(operation,executionContext);
            requireCurrentVersion(operation);
            Snapshot loaded=loadOrCreate(operation, scope, policy, modelId(executionContext));
            Integer superseded=jdbc.queryForObject("""
                select count(*) from scoped_asset_reextraction_unit u
                left join script_episode_asset_analysis a on a.episode_id=u.episode_id
                  and a.tenant_id=? and a.script_id=? and a.generated_by_run_id=u.child_run_id
                  and a.content_fingerprint=u.content_fingerprint
                where u.snapshot_id=? and (u.child_run_id is not null or u.status='SUCCEEDED') and a.id is null
                """,Integer.class,operation.tenantId,operation.scriptId,loaded.id());
            if(superseded==null || superseded!=0) throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,
                "资产提交证据已被其他任务更新，请重新确认后发起新的资产提取。");
            return loaded;
        });
        WorkflowAgentExecutionPlan plan = transaction(() -> {
            requireOwner(operation,executionContext);
            return frozenPlan(snapshot.id());
        });
        List<WorkflowAgentModelCall> calls = new ArrayList<>();
        for (Unit unit : runnableUnits(snapshot.id())) {
            transaction(() -> {
                requireOwner(operation,executionContext);
                requireSources(operation,snapshot);
                jdbc.update("update scoped_asset_reextraction_unit set status='RUNNING', started_at=now(), updated_at=now() where id=?", unit.id());
                return null;
            });
            try {
                ScriptAnalysisTaskEntity task = transientTask(operation);
                ScriptAnalysisStageEntity stage = transientStage();
                List<Long> committed=jdbc.queryForList("""
                    select u.child_run_id from scoped_asset_reextraction_unit u
                    join script_episode_asset_analysis a on a.episode_id=u.episode_id
                      and a.generated_by_run_id=u.child_run_id and a.content_fingerprint=u.content_fingerprint
                    where u.id=? and a.tenant_id=? and a.script_id=?
                    """,Long.class,unit.id(),operation.tenantId,operation.scriptId);
                AssetRecognitionAgentAdapter.Execution child = committed.isEmpty()
                    ? recognition.executeChild(plan, task, stage,unit.episodeId(), executionContext, snapshot.modelId(), scope, policy)
                    : new AssetRecognitionAgentAdapter.Execution(committed.get(0),runs.modelCalls(committed.get(0),operation.tenantId));
                transaction(() -> {
                    requireOwner(operation,executionContext);
                    jdbc.update("update scoped_asset_reextraction_unit set status='SUCCEEDED', child_run_id=?, error_message=null, finished_at=now(), updated_at=now() where id=?",
                        child.agentRunId(), unit.id());
                    return null;
                });
                calls.addAll(child.modelCalls());
            } catch (RuntimeException failure) {
                transaction(() -> {
                    requireOwner(operation,executionContext);
                    jdbc.update("update scoped_asset_reextraction_unit set status='FAILED', error_message=?, finished_at=now(), updated_at=now() where id=?",
                        message(failure), unit.id());
                    refresh(snapshot.id(), "FAILED");
                    return null;
                });
                throw failure;
            }
            transaction(() -> {
                requireOwner(operation,executionContext);
                refresh(snapshot.id(), "RUNNING");
                return null;
            });
        }
        if (count("select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status <> 'SUCCEEDED'", snapshot.id()) != 0) {
            throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE, "仍有剧集未完成资产重提取，不能收口旧资产。");
        }
        try {
            transaction(() -> {
                requireOwner(operation,executionContext);
                requireSources(operation,snapshot);
                Integer missing=jdbc.queryForObject("""
                    select count(*) from scoped_asset_reextraction_unit u
                    left join script_episode_asset_analysis a on a.episode_id=u.episode_id
                      and a.generated_by_run_id=u.child_run_id and a.content_fingerprint=u.content_fingerprint
                      and a.tenant_id=? and a.script_id=?
                    where u.snapshot_id=? and (u.status<>'SUCCEEDED' or a.id is null)
                    """,Integer.class,operation.tenantId,operation.scriptId,snapshot.id());
                if(missing==null || missing!=0)throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE,"缺少正式提交证据，不能收口。");
                finalizeScope(snapshot, scope);
                refresh(snapshot.id(), "SUCCEEDED");
                return null;
            });
        } catch (RuntimeException failure) {
            transaction(() -> {
                requireOwner(operation,executionContext);
                refresh(snapshot.id(), "FAILED");
                return null;
            });
            throw failure;
        }
        return new ScriptAiOperationExecutionResult("SCOPED_ASSET_REEXTRACTION", snapshot.id(), List.of(), calls);
    }

    private WorkflowAgentExecutionPlan frozenPlan(long snapshotId) {
        String saved=jdbc.queryForObject("select frozen_plan_json from scoped_asset_reextraction_snapshot where id=? for update",
            String.class,snapshotId);
        try {
            if(saved!=null) return json.readValue(saved,WorkflowAgentExecutionPlan.class);
            WorkflowAgentExecutionPlan plan=runner.freezeFormal(AssetRecognitionAgentBootstrap.AGENT_CODE);
            jdbc.update("update scoped_asset_reextraction_snapshot set frozen_plan_json=?,updated_at=now() where id=?",
                json.writeValueAsString(plan),snapshotId);
            return plan;
        } catch(com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("无法恢复已冻结的资产 Agent 配置。",exception);
        }
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
        return jdbc.query("select id, episode_id from scoped_asset_reextraction_unit where snapshot_id=? and status in ('PENDING','FAILED','RUNNING') order by id",
            (rs, row) -> new Unit(rs.getLong("id"), rs.getLong("episode_id")), snapshotId);
    }

    private void refresh(long snapshotId, String status) {
        jdbc.update("update scoped_asset_reextraction_snapshot set status=?, completed_units=(select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status='SUCCEEDED'), failed_units=(select count(*) from scoped_asset_reextraction_unit where snapshot_id=? and status='FAILED'), finished_at=case when ?='SUCCEEDED' then now() else null end, updated_at=now() where id=?",
            status, snapshotId, snapshotId, status, snapshotId);
    }

    private <T> T transaction(java.util.function.Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
    private void requireOwner(ScriptAiOperationEntity o,AiExecutionContext c) {
        coordination.requireOwned(o.tenantId,o.projectId,o.scriptId,c.task().id,c.task().executionVersion,c.claim().attemptId());
    }
    private void requireCurrentVersion(ScriptAiOperationEntity o) {
        List<Long> current=jdbc.queryForList("select current_version_id from script where id=? and tenant_id=? and project_id=? and deleted_at is null for update",
            Long.class,o.scriptId,o.tenantId,o.projectId);
        if(current.size()!=1 || !java.util.Objects.equals(current.get(0),o.scriptVersionId))
            throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"剧本版本已变化，请重新提交资产重提取。");
    }
    private void requireSources(ScriptAiOperationEntity o,Snapshot s) {
        requireCurrentVersion(o);
        jdbc.queryForList("select id from script_episode where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null for update",
            Long.class,o.tenantId,o.projectId,o.scriptId);
        Integer changed=jdbc.queryForObject("""
            select count(*) from scoped_asset_reextraction_unit u left join script_episode e on e.id=u.episode_id
              and e.tenant_id=? and e.project_id=? and e.script_id=? and e.status='ACTIVE' and e.retired_at is null
            where u.snapshot_id=? and (e.id is null or e.content_fingerprint<>u.content_fingerprint)
            """,Integer.class,o.tenantId,o.projectId,o.scriptId,s.id());
        int active=count("select count(*) from script_episode where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null",o.tenantId,o.projectId,o.scriptId);
        int frozen=count("select count(*) from scoped_asset_reextraction_unit where snapshot_id=?",s.id());
        if(changed==null || changed!=0 || active!=frozen)throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"剧集集合或正文已变化，不能继续重提取。");
    }

    private void finalizeScope(Snapshot snapshot, AssetRecognitionScope scope) {
        for (String type : types(scope)) {
            String table = table(type);
            jdbc.update("""
                update asset_visual_variant_episode b set binding_status='RETIRED',retired_at=now(),updated_at=now()
                where tenant_id=? and project_id=? and script_id=? and asset_type=?
                  and generated_by_run_id is not null and retired_at is null
                  and not exists (select 1 from scoped_asset_reextraction_unit u where u.snapshot_id=? and u.episode_id=b.episode_id)
                """,snapshot.tenantId(),snapshot.projectId(),snapshot.scriptId(),type,snapshot.id());
            jdbc.update("update asset_visual_variant variant set deleted_at=now(),updated_at=now()"
                + " where tenant_id=? and project_id=? and asset_type=? and source_type='AI' and generated_by_run_id is not null"
                + " and deleted_at is null and exists (select 1 from "+table+" asset where asset.id=variant.asset_id"
                + " and asset.tenant_id=variant.tenant_id and asset.project_id=variant.project_id and asset.script_id=?)"
                + " and not exists (select 1 from asset_visual_variant_episode b where b.variant_id=variant.id"
                + " and b.retired_at is null and b.binding_status='ACTIVE')",
                snapshot.tenantId(),snapshot.projectId(),type,snapshot.scriptId());
            jdbc.update("update " + table + " asset set deleted_at=now(),updated_at=now()"
                + " where tenant_id=? and project_id=? and script_id=? and source='AI'"
                + " and generated_by_run_id is not null and deleted_at is null"
                + " and not exists (select 1 from asset_visual_variant_episode b where b.tenant_id=asset.tenant_id"
                + " and b.project_id=asset.project_id and b.asset_type=? and b.asset_id=asset.id"
                + " and b.retired_at is null and b.binding_status='ACTIVE')",
                snapshot.tenantId(),snapshot.projectId(),snapshot.scriptId(),type);
        }
    }

    private ScriptAnalysisTaskEntity transientTask(ScriptAiOperationEntity operation) {
        ScriptAnalysisTaskEntity task = new ScriptAnalysisTaskEntity();
        task.setId(operation.id);
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
