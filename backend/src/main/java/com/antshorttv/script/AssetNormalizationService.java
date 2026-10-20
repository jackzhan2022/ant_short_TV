package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.workflowagent.run.*;
import com.antshorttv.workflowagent.tool.ToolExecutionContext;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AssetNormalizationService {
    public static final String AGENT = "short-drama-asset-normalization";
    private static final Map<String,String> TABLES = Map.of(
        "CHARACTER", "character_asset", "SCENE", "scene_asset", "PROP", "prop_asset");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final AssetExtractionCoordination coordination;
    private final TransactionTemplate transactions;
    private final WorkflowAgentRunner runner;

    public AssetNormalizationService(JdbcTemplate jdbc, ObjectMapper json, AssetExtractionCoordination coordination,
                                     PlatformTransactionManager transactions, @org.springframework.context.annotation.Lazy WorkflowAgentRunner runner) {
        this.jdbc=jdbc; this.json=json; this.coordination=coordination;
        this.transactions=new TransactionTemplate(transactions); this.runner=runner;
    }

    public record Outcome(long snapshotId, List<WorkflowAgentModelCall> calls) {}

    Long committedEpisodeAssetRun(ScriptAiOperationEntity operation, long episode, AiExecutionContext c) {
        var runs=jdbc.queryForList("""
            select analysis.generated_by_run_id from script_episode_asset_analysis analysis
            join ai_workflow_agent_run run on run.id=analysis.generated_by_run_id
            where analysis.tenant_id=? and analysis.project_id=? and analysis.script_id=? and analysis.episode_id=?
              and run.task_id=? and run.agent_code='short-drama-asset-recognition' and run.run_type='FORMAL'
              and run.tenant_id=analysis.tenant_id and run.project_id=analysis.project_id
              and run.script_id=analysis.script_id and run.episode_id=analysis.episode_id
              and exists (select 1 from ai_workflow_agent_run_step step join ai_call_log call_log on call_log.id=step.ai_call_log_id
                where step.run_id=run.id and call_log.execution_id=? and call_log.tenant_id=analysis.tenant_id)
            """,Long.class,operation.tenantId,operation.projectId,operation.scriptId,episode,operation.id,c.task().id);
        return runs.isEmpty()?null:runs.get(0);
    }

    public Outcome normalize(long scriptId, long versionId, AiExecutionContext c) {
        long id = prepare(scriptId, versionId, c);
        if ("SUCCEEDED".equals(snapshot(c.task().tenantId, c.task().projectId, scriptId, c.task().id).get("status")))
            return new Outcome(id, List.of());
        try {
            JsonNode frozen=parse(snapshot(c.task().tenantId,c.task().projectId,scriptId,c.task().id).get("input_json"));
            ObjectNode context=compactInventory(frozen);
            context.set("episodes",frozen.path("episodes"));
            var result=runner.runFormal(new WorkflowAgentRunInput(AGENT,
                "以下已包含完整资产清单、全部剧集原文及绑定。一次分析全部输入，直接调用 save_asset_normalization 提交完整结果。\n"
                    + write(context),
                c.task().tenantId, c.task().projectId, null, scriptId, c.task().businessId, null,
                c.task().userId, c.task().id, c.claim().attemptId(), c.task().executionVersion,
                c.task().resolvedModelId == null ? c.task().requestedModelId : c.task().resolvedModelId)
                .withTrustedToolState(Map.of("assetNormalizationContextComplete",true)));
            if (!"SUCCEEDED".equals(snapshot(c.task().tenantId,c.task().projectId,scriptId,c.task().id).get("status")))
                throw new BusinessException(ErrorCode.ANALYSIS_AGENT_INCOMPLETE,"归一 Agent 未提交完整结果。");
            return new Outcome(id,result.modelCalls());
        } catch (RuntimeException failure) {
            transactions.executeWithoutResult(tx -> {
                requireOwner(scriptId,c);
                jdbc.update("update script_asset_normalization_snapshot set status='FAILED',error_message=?,updated_at=now() where id=? and status<>'SUCCEEDED'",
                    failure.getMessage(),id);
            });
            if (failure instanceof BusinessException business && Set.of(
                ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,
                ErrorCode.REQUIRED_TOOL_NOT_CALLED,ErrorCode.ANALYSIS_AGENT_INCOMPLETE).contains(business.getErrorCode()))
                throw new NonRetryableAssetNormalizationException(business);
            throw failure;
        }
    }

    long prepare(long scriptId, long versionId, AiExecutionContext c) {
        return transactions.execute(tx -> {
            requireOwner(scriptId,c);
            requireVersion(c.task().tenantId,c.task().projectId,scriptId,versionId);
            jdbc.update("update ai_execution_task set phase='ASSET_NORMALIZATION',progress=90,updated_at=now() where id=? and execution_version=?",
                c.task().id,c.task().executionVersion);
            var existing=jdbc.queryForList("select id,status,input_fingerprint from script_asset_normalization_snapshot where execution_id=? for update",c.task().id);
            if(!existing.isEmpty()) {
                if(!"SUCCEEDED".equals(existing.get(0).get("status")) && !hash(capture(c.task().tenantId,c.task().projectId,scriptId)).equals(existing.get(0).get("input_fingerprint")))
                    throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"归一输入已变化，请发起新的归一任务。");
                if(!"SUCCEEDED".equals(existing.get(0).get("status"))) jdbc.update(
                    "update script_asset_normalization_snapshot set status='RUNNING',error_message=null,updated_at=now() where execution_id=?",c.task().id);
                return ((Number)existing.get(0).get("id")).longValue();
            }
            JsonNode input=capture(c.task().tenantId,c.task().projectId,scriptId);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("tenant_id",c.task().tenantId); row.put("project_id",c.task().projectId);
            row.put("script_id",scriptId); row.put("script_version_id",versionId); row.put("execution_id",c.task().id);
            row.put("input_json",write(input)); row.put("input_fingerprint",hash(input)); row.put("status","RUNNING");
            row.put("created_at",new java.sql.Timestamp(System.currentTimeMillis())); row.put("updated_at",row.get("created_at"));
            return insert("script_asset_normalization_snapshot",row);
        });
    }

    public JsonNode inventory(ToolExecutionContext c) {
        var snapshot=toolSnapshot(c);
        return compactInventory(parse(snapshot.get("input_json")));
    }

    private ObjectNode compactInventory(JsonNode input) {
        ObjectNode output=json.createObjectNode();
        ArrayNode assets=output.putArray("assets");
        for(JsonNode a:input.path("assets")) {
            ObjectNode compact=assets.addObject();
            for(String field:List.of("assetType","id","name","identity","appearance","description","plot_function","related_character"))
                if(a.hasNonNull(field)) compact.set(field,a.get(field));
            compact.put("hasImage",a.hasNonNull("main_image_url"));
            compact.set("source",a.path("source"));
            JsonNode content=parse(a.path("content_json").asText("{}"));
            for(String field:List.of("aliases","evidence","lastEvidence","ownerCharacterLocalKey"))
                if(content.has(field)) compact.set(field,content.get(field));
        }
        Set<String> activeAssets=new HashSet<>();
        input.path("assets").forEach(a -> activeAssets.add(AssetNormalizationDecision.key(a.path("assetType").asText(),a.path("id").asLong())));
        Set<String> boundAssets=new HashSet<>();
        input.path("bindings").forEach(b -> boundAssets.add(AssetNormalizationDecision.key(b.path("asset_type").asText(),b.path("asset_id").asLong())));
        for(String collection:List.of("bindings","historicalBindings","variants")) {
            ArrayNode compact=output.putArray(collection);
            for(JsonNode item:input.path(collection)) {
                String key=AssetNormalizationDecision.key(item.path("asset_type").asText(),item.path("asset_id").asLong());
                if(!activeAssets.contains(key) || (collection.equals("variants") && !item.path("deleted_at").isNull())
                    || (collection.equals("historicalBindings") && boundAssets.contains(key))) continue;
                ObjectNode entry=compact.addObject();
                for(String field:List.of("id","asset_type","asset_id","episode_id","variant_id","name","appearance","content_json"))
                    if(item.hasNonNull(field)) entry.set(field,item.get(field));
            }
        }
        ArrayNode episodes=output.putArray("episodes");
        input.path("episodes").forEach(e -> episodes.addObject().setAll(Map.of(
            "id",e.path("id"),"episodeNo",e.path("episode_no"),"title",e.path("title"))));
        output.put("assetCount",assets.size());
        output.put("complete",true);
        return output;
    }

    public JsonNode episodes(ToolExecutionContext c, JsonNode arguments) {
        JsonNode input=parse(toolSnapshot(c).get("input_json"));
        Set<Long> requested=new HashSet<>();
        arguments.path("episodeIds").forEach(id -> requested.add(id.asLong()));
        ArrayNode result=json.createArrayNode();
        input.path("episodes").forEach(e -> { if(requested.remove(e.path("id").asLong())) result.add(e); });
        if(!requested.isEmpty()) throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,"请求的剧集不属于归一快照。");
        return json.createObjectNode().set("episodes",result);
    }

    public JsonNode publish(ToolExecutionContext c, JsonNode decision) {
        return transactions.execute(tx -> {
            coordination.requireOwned(c);
            Map<String,Object> row=toolSnapshot(c);
            if("SUCCEEDED".equals(row.get("status"))) return parse(row.get("result_json"));
            requireVersion(c.tenantId(),c.projectId(),c.scriptId(),((Number)row.get("script_version_id")).longValue());
            JsonNode input=parse(row.get("input_json"));
            if(!hash(capture(c.tenantId(),c.projectId(),c.scriptId())).equals(row.get("input_fingerprint")))
                throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"归一期间资产或剧集已变化，请重新发起归一任务。");
            var validated=AssetNormalizationDecision.validate(input,decision);
            int merged=0;
            for(var group:validated.groups()) for(long member:group.members()) if(member!=group.retainId()) {
                merge(c,group.type(),member,group.retainId()); merged++;
            }
            for(var split:validated.splits()) split(c,split);
            for(var entry:TABLES.entrySet()) {
                AssetVisualPrimaryRepair.repair(jdbc,c.tenantId(),c.projectId(),c.scriptId(),entry.getKey(),entry.getValue());
                repairImagePointers(c,entry.getKey(),entry.getValue());
            }
            ObjectNode result=json.createObjectNode().put("saved",true).put("mergedAssets",merged)
                .put("splitGroups",validated.splits().size()).put("snapshotId",((Number)row.get("id")).longValue());
            jdbc.update("update script_asset_normalization_snapshot set decision_json=?,result_json=?,status='SUCCEEDED',agent_run_id=?,error_message=null,updated_at=now() where id=?",
                write(decision),write(result),c.agentRunId(),row.get("id"));
            return result;
        });
    }

    private void merge(ToolExecutionContext c,String type,long source,long target) {
        String table=TABLES.get(type);
        var from=asset(c,type,source); var to=asset(c,type,target);
        ObjectNode content=(ObjectNode)parse(to.get("content_json"));
        Set<String> names=new LinkedHashSet<>();
        names.add(String.valueOf(from.get("name")));
        parse(from.get("content_json")).path("aliases").forEach(a -> names.add(a.asText()));
        content.path("aliases").forEach(a -> names.add(a.asText()));
        ArrayNode aliases=content.putArray("aliases"); names.forEach(aliases::add);
        jdbc.update("update "+table+" set content_json=?,updated_at=now() where id=? and tenant_id=? and project_id=?",
            write(content),target,c.tenantId(),c.projectId());
        // Preserve every variant ID and image. Avoid generated primary/preferred marker collisions.
        jdbc.update("update asset_visual_variant set is_primary=false where tenant_id=? and project_id=? and asset_type=? and asset_id=?",
            c.tenantId(),c.projectId(),type,source);
        jdbc.update("update asset_visual_variant set prompt=coalesce(nullif(trim(prompt),''),?) where tenant_id=? and project_id=? and asset_type=? and asset_id=?",
            from.get("prompt"),c.tenantId(),c.projectId(),type,source);
        jdbc.update("update asset_visual_variant_episode set is_preferred=false where tenant_id=? and project_id=? and asset_type=? and asset_id=? and episode_id in "
            + "(select episode_id from (select episode_id from asset_visual_variant_episode where tenant_id=? and project_id=? and asset_type=? and asset_id=? and is_preferred=true and retired_at is null) preferred)",
            c.tenantId(),c.projectId(),type,source,c.tenantId(),c.projectId(),type,target);
        for(String ref:List.of("asset_visual_variant","asset_visual_variant_episode","storyboard_asset_reference","asset_image_batch_item"))
            jdbc.update("update "+ref+" set asset_id=?,updated_at=now() where tenant_id=? and project_id=? and asset_type=? and asset_id=?",
                target,c.tenantId(),c.projectId(),type,source);
        // Redirect previous merges directly to the surviving identity; retain the original records.
        jdbc.update("update "+table+" set merge_target_id=?,updated_at=now() where tenant_id=? and project_id=? and merge_target_id=?",
            target,c.tenantId(),c.projectId(),source);
        jdbc.update("update "+table+" set merge_target_id=?,deleted_at=now(),updated_at=now() where id=? and tenant_id=? and project_id=?",
            target,source,c.tenantId(),c.projectId());
        if(to.get("main_image_url")==null && from.get("main_image_url")!=null)
            jdbc.update("update "+table+" set main_image_url=?,main_image_result_id=? where id=? and tenant_id=? and project_id=?",
                from.get("main_image_url"),from.get("main_image_result_id"),target,c.tenantId(),c.projectId());
    }

    private void split(ToolExecutionContext c,AssetNormalizationDecision.Split split) {
        long target=split.targetId()==null ? cloneAsset(c,split) : split.targetId();
        Map<Long,Long> variants=new HashMap<>();
        for(long bindingId:split.bindings()) {
            var binding=jdbc.queryForMap("select * from asset_visual_variant_episode where id=? and tenant_id=? and project_id=? for update",bindingId,c.tenantId(),c.projectId());
            long oldVariant=((Number)binding.get("variant_id")).longValue();
            long variant=variants.computeIfAbsent(oldVariant,old -> {
                var copy=new LinkedHashMap<>(jdbc.queryForMap("select * from asset_visual_variant where id=? and tenant_id=? and project_id=?",old,c.tenantId(),c.projectId()));
                copy.remove("id"); copy.remove("primary_marker"); copy.put("asset_id",target); copy.put("is_primary",false);
                return insert("asset_visual_variant",copy);
            });
            jdbc.update("update asset_visual_variant_episode set asset_id=?,variant_id=?,is_preferred=false,updated_at=now() where id=?",target,variant,bindingId);
            jdbc.update("update asset_visual_variant_episode set is_preferred=true where id=? and not exists "
                + "(select 1 from (select id from asset_visual_variant_episode where tenant_id=? and project_id=? and episode_id=? and asset_type=? and asset_id=? and is_preferred=true and retired_at is null) preferred)",
                bindingId,c.tenantId(),c.projectId(),binding.get("episode_id"),split.type(),target);
            jdbc.update("update storyboard_asset_reference set asset_id=?,variant_id=?,updated_at=now() where tenant_id=? and project_id=? and asset_type=? and asset_id=? and (variant_id=? or variant_id is null) "
                + "and storyboard_id in (select id from storyboard where tenant_id=? and project_id=? and episode_id=?)",
                target,variant,c.tenantId(),c.projectId(),split.type(),split.sourceId(),oldVariant,c.tenantId(),c.projectId(),binding.get("episode_id"));
        }
    }

    private long cloneAsset(ToolExecutionContext c,AssetNormalizationDecision.Split split) {
        var copy=new LinkedHashMap<>(asset(c,split.type(),split.sourceId()));
        copy.remove("id"); copy.remove("active_identity_name"); copy.put("name",split.newName());
        copy.put("normalized_name",AssetIdentityNormalizer.normalize(split.newName()));
        copy.put("merge_target_id",null); copy.put("main_image_url",null); copy.put("main_image_result_id",null);
        copy.put("content_json","{\"aliases\":[]}");
        return insert(TABLES.get(split.type()),copy);
    }

    private Map<String,Object> asset(ToolExecutionContext c,String type,long id) {
        return jdbc.queryForMap("select * from "+TABLES.get(type)+" where id=? and tenant_id=? and project_id=? for update",id,c.tenantId(),c.projectId());
    }

    private void repairImagePointers(ToolExecutionContext c,String type,String table) {
        var rows=jdbc.queryForList("select a.id,v.current_image_url,v.current_image_result_id from "+table+" a join asset_visual_variant v "
            +"on v.asset_id=a.id and v.tenant_id=a.tenant_id and v.project_id=a.project_id and v.asset_type=? and v.is_primary=true and v.deleted_at is null "
            +"where a.tenant_id=? and a.project_id=? and a.deleted_at is null and (a.main_image_url is null or trim(a.main_image_url)='') "
            +"and v.generation_status='COMPLETED' and v.current_image_url is not null",type,c.tenantId(),c.projectId());
        for(var row:rows) jdbc.update("update "+table+" set main_image_url=?,main_image_result_id=?,updated_at=now() where id=? and tenant_id=? and project_id=?",
            row.get("current_image_url"),row.get("current_image_result_id"),row.get("id"),c.tenantId(),c.projectId());
    }

    private JsonNode capture(long tenant,long project,long script) {
        Integer downstream=jdbc.queryForObject("select count(*) from ai_execution_task where tenant_id=? and project_id=? and scene in ('storyboard_breakdown','prompt_generate') and status in ('PENDING','RUNNING')",Integer.class,tenant,project);
        if(downstream!=null && downstream>0) throw new com.antshorttv.execution.AiExecutionDeferredException("等待已提交的分镜或提示词任务完成后归一。");
        Integer activeImages=jdbc.queryForObject("select count(*) from ai_image_task where tenant_id=? and project_id=? and deleted_at is null and task_type in ('CHARACTER','SCENE','PROP') and status in ('PENDING','RUNNING')",Integer.class,tenant,project);
        if(activeImages!=null && activeImages>0) throw new com.antshorttv.execution.AiExecutionDeferredException("等待资产图片任务完成后归一。");
        ObjectNode input=json.createObjectNode(); ArrayNode assets=input.putArray("assets");
        for(String type:List.of("CHARACTER","SCENE","PROP")) for(var row:jdbc.queryForList("select * from "+TABLES.get(type)
            +" where tenant_id=? and project_id=? and (script_id=? or script_id is null) and deleted_at is null order by id for update",tenant,project,script)) {
            ObjectNode a=json.valueToTree(row); a.put("assetType",type); assets.add(a);
        }
        input.set("variants",json.valueToTree(jdbc.queryForList("select v.* from asset_visual_variant v where v.tenant_id=? and v.project_id=? order by v.id for update",tenant,project)));
        Set<String> activeAssets=new HashSet<>();
        assets.forEach(a -> activeAssets.add(AssetNormalizationDecision.key(a.path("assetType").asText(),a.path("id").asLong())));
        for(JsonNode v:input.path("variants")) if(!v.hasNonNull("deleted_at")
            && activeAssets.contains(AssetNormalizationDecision.key(v.path("asset_type").asText(),v.path("asset_id").asLong()))
            && "GENERATING".equals(v.path("generation_status").asText()))
            throw new com.antshorttv.execution.AiExecutionDeferredException("等待资产图片生成完成后归一。");
        input.set("bindings",json.valueToTree(jdbc.queryForList("select * from asset_visual_variant_episode where tenant_id=? and project_id=? and script_id=? and binding_status='ACTIVE' and retired_at is null order by id for update",tenant,project,script)));
        input.set("historicalBindings",json.valueToTree(jdbc.queryForList("select * from asset_visual_variant_episode where tenant_id=? and project_id=? and script_id=? and retired_at is not null order by id for update",tenant,project,script)));
        input.set("episodes",json.valueToTree(jdbc.queryForList("select id,episode_no,title,content,content_fingerprint from script_episode where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null order by id for update",tenant,project,script)));
        input.set("references",json.valueToTree(jdbc.queryForList("select * from storyboard_asset_reference where tenant_id=? and project_id=? order by id for update",tenant,project)));
        return input;
    }

    private Map<String,Object> toolSnapshot(ToolExecutionContext c) {
        if(c.executionId()==null) throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_UNAUTHORIZED,"归一工具仅允许正式后台任务调用。");
        return snapshot(c.tenantId(),c.projectId(),c.scriptId(),c.executionId());
    }
    private Map<String,Object> snapshot(long tenant,long project,long script,long execution) {
        return jdbc.queryForMap("select * from script_asset_normalization_snapshot where tenant_id=? and project_id=? and script_id=? and execution_id=?",tenant,project,script,execution);
    }
    private void requireOwner(long script,AiExecutionContext c) {
        coordination.requireOwned(c.task().tenantId,c.task().projectId,script,c.task().id,c.task().executionVersion,c.claim().attemptId());
    }
    private void requireVersion(long tenant,long project,long script,long version) {
        var current=jdbc.queryForList("select current_version_id from script where tenant_id=? and project_id=? and id=? and deleted_at is null for update",Long.class,tenant,project,script);
        if(current.size()!=1 || !Objects.equals(current.get(0),version)) throw new BusinessException(ErrorCode.ANALYSIS_EPISODE_SNAPSHOT_CHANGED,"归一绑定的剧本版本已变化。");
    }
    private long insert(String table,Map<String,Object> row) {
        return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(row.keySet().toArray(String[]::new))
            .usingGeneratedKeyColumns("id").executeAndReturnKey(row).longValue();
    }
    private String hash(JsonNode input) { return EpisodePromptContextFactory.hash(write(input)); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(Exception e) { throw new IllegalStateException(e); } }
    private JsonNode parse(Object value) { try { return value==null ? json.createObjectNode() : json.readTree(String.valueOf(value)); } catch(Exception e) { throw new IllegalStateException("归一数据无法读取。",e); } }
}
