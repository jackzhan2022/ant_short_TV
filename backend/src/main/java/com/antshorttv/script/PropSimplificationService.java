package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.execution.*;
import com.antshorttv.rbac.ProjectPermissionGuard;
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
public class PropSimplificationService {
    public static final String AGENT = "short-drama-prop-simplification";
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final WorkflowAgentRunner runner;
    private final ProjectPermissionGuard permissions;
    private final ScriptAiOperationService operations;
    private final AssetNormalizationGuard normalization;
    private final PropProductionService production;
    private final AiExecutionService executions;
    private final AiExecutionResponseMapper responses;

    public PropSimplificationService(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager transactions,
        @org.springframework.context.annotation.Lazy WorkflowAgentRunner runner, ProjectPermissionGuard permissions, ScriptAiOperationService operations,
        AssetNormalizationGuard normalization, PropProductionService production,
        AiExecutionService executions, AiExecutionResponseMapper responses) {
        this.jdbc=jdbc; this.json=json; this.transactions=new TransactionTemplate(transactions); this.runner=runner;
        this.permissions=permissions; this.operations=operations; this.normalization=normalization;
        this.production=production; this.executions=executions; this.responses=responses;
    }

    public AiExecutionResponse submit(long tenant, long project, String key) {
        var user=permissions.require(tenant,project,"ELEMENT:AI_EXTRACT");
        return transactions.execute(tx -> {
            var script=script(tenant,project,true);
            normalization.requireReady(tenant,project);
            var active=jdbc.queryForList("""
                select execution_id from script_ai_operation where tenant_id=? and project_id=?
                  and script_id=? and operation_type='PROP_SIMPLIFICATION' and status in ('PENDING','RUNNING')
                  and created_by=? order by id desc limit 1
                """,Long.class,tenant,project,script.get("id"),user.userId());
            if(!active.isEmpty()) return responses.toResponse(executions.requireTask(active.get(0)));
            return operations.submit(user,project,AiBusinessScene.SCRIPT_CHARACTER_SCENE_RECOGNITION,
                "PROP_SIMPLIFICATION",number(script,"id"),number(script,"current_version_id"),Map.of(),key,"prop-simplification");
        });
    }

    public ObjectNode status(long tenant,long project) {
        permissions.require(tenant,project,"ELEMENT:VIEW");
        ObjectNode result=json.createObjectNode();
        var rows=jdbc.queryForList("select * from prop_simplification_plan where tenant_id=? and project_id=? order by id desc limit 1",tenant,project);
        if(!rows.isEmpty()) {
            var row=rows.get(0); result.put("id",number(row,"id")).put("status",String.valueOf(row.get("status")))
                .put("executionId",number(row,"execution_id"));
            result.set("decision",parse(row.get("decision_json")));
            JsonNode input=parse(row.get("input_json"));
            ObjectNode inventory=result.putObject("inventory");
            for(String field:List.of("props","scenes","variants")) inventory.set(field,input.path(field));
            var episodes=inventory.putArray("episodes");
            input.path("episodes").forEach(e -> episodes.addObject().put("id",e.path("id").asLong()).put("episode_no",e.path("episode_no").asInt()));
            if(row.get("error_message")!=null) result.put("errorMessage",String.valueOf(row.get("error_message")));
        } else result.put("status","IDLE");
        result.set("production",production.catalog(tenant,project));
        var applied=jdbc.queryForList("select id from prop_simplification_plan where tenant_id=? and project_id=? and status='APPLIED' order by id desc limit 1",Long.class,tenant,project);
        if(!applied.isEmpty()) result.put("appliedPlanId",applied.get(0));
        var active=jdbc.queryForList("select execution_id from script_ai_operation where tenant_id=? and project_id=? and operation_type='PROP_SIMPLIFICATION' and status in ('PENDING','RUNNING') order by id desc limit 1",Long.class,tenant,project);
        if(!active.isEmpty()) result.put("executionId",active.get(0)).put("status","RUNNING");
        if(active.isEmpty()) {
            var latest=jdbc.queryForList("select execution_id,status,error_message from script_ai_operation where tenant_id=? and project_id=? and operation_type='PROP_SIMPLIFICATION' order by id desc limit 1",tenant,project);
            if(!latest.isEmpty() && "FAILED".equals(latest.get(0).get("status"))
                && (!result.has("executionId") || number(latest.get(0),"execution_id")>=result.path("executionId").asLong()))
                result.put("status","FAILED").put("executionId",number(latest.get(0),"execution_id"))
                    .put("errorMessage",latest.get(0).get("error_message")==null?"精简分析失败，可重新提交。":String.valueOf(latest.get(0).get("error_message")));
        }
        return result;
    }

    ScriptAiOperationExecutionResult analyze(ScriptAiOperationEntity operation,AiExecutionContext context) {
        long id=transactions.execute(tx -> {
            var script=script(operation.tenantId,operation.projectId,true);
            check(number(script,"current_version_id")==operation.scriptVersionId,"剧本版本已变化，请重新分析。");
            var existing=jdbc.queryForList("select id,status from prop_simplification_plan where execution_id=?",context.task().id);
            if(!existing.isEmpty()) return number(existing.get(0),"id");
            JsonNode input=capture(operation.tenantId,operation.projectId,operation.scriptId);
            check(!input.path("props").isEmpty(),"请先提取道具后再精简。");
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("tenant_id",operation.tenantId);row.put("project_id",operation.projectId);row.put("script_id",operation.scriptId);
            row.put("script_version_id",operation.scriptVersionId);row.put("execution_id",context.task().id);
            row.put("input_fingerprint",hash(input));row.put("input_json",write(input));row.put("status","RUNNING");
            row.put("created_by",context.task().userId);row.put("created_at",now());row.put("updated_at",now());
            return insert("prop_simplification_plan",row);
        });
        var snapshot=plan(operation.tenantId,operation.projectId,id,false);
        if(Set.of("READY","APPLIED","REVERTED").contains(String.valueOf(snapshot.get("status"))))
            return new ScriptAiOperationExecutionResult("PROP_SIMPLIFICATION",id,List.of());
        try {
            var run=runner.runFormal(new WorkflowAgentRunInput(AGENT,
                "完整道具、形态、场景、剧集原文和使用绑定如下。提交制作建议，不改变任何正式资产。\n"+snapshot.get("input_json"),
                operation.tenantId,operation.projectId,null,operation.scriptId,operation.id,null,context.task().userId,
                context.task().id,context.claim().attemptId(),context.task().executionVersion,
                context.task().resolvedModelId==null?context.task().requestedModelId:context.task().resolvedModelId));
            check("READY".equals(plan(operation.tenantId,operation.projectId,id,false).get("status")),"分析未保存完整方案。");
            return new ScriptAiOperationExecutionResult("PROP_SIMPLIFICATION",id,List.of(),run.modelCalls());
        } catch(RuntimeException failure) {
            jdbc.update("update prop_simplification_plan set status='FAILED',error_message=?,updated_at=now() where id=? and status<>'READY'",
                failure.getMessage(),id);
            if(failure instanceof BusinessException business && Set.of(ErrorCode.VALIDATION_ERROR,ErrorCode.WORKFLOW_AGENT_TOOL_INVALID,
                ErrorCode.REQUIRED_TOOL_NOT_CALLED,ErrorCode.WORKFLOW_AGENT_STEP_LIMIT,ErrorCode.ANALYSIS_AGENT_INCOMPLETE).contains(business.getErrorCode()))
                throw new NonRetryablePropSimplificationException(business);
            throw failure;
        }
    }

    public JsonNode save(ToolExecutionContext context,JsonNode decision) {
        return transactions.execute(tx -> {
            var task=executions.requireTaskForUpdate(context.executionId());
            check("RUNNING".equals(task.status) && Objects.equals(task.executionVersion,context.executionVersion())
                && task.claimExpiresAt!=null && task.claimExpiresAt.isAfter(java.time.LocalDateTime.now())
                && Objects.equals(task.tenantId,context.tenantId()) && Objects.equals(task.projectId,context.projectId())
                && Objects.equals(task.userId,context.userId()),
                "分析任务所有权已变化。");
            var rows=jdbc.queryForList("select * from prop_simplification_plan where tenant_id=? and project_id=? and script_id=? and execution_id=? for update",
                context.tenantId(),context.projectId(),context.scriptId(),context.executionId());
            check(rows.size()==1,"方案不属于当前执行任务。");var row=rows.get(0);
            if("READY".equals(row.get("status"))) return json.createObjectNode().put("saved",true);
            PropSimplificationDecision.validate(parse(row.get("input_json")),decision);
            jdbc.update("update prop_simplification_plan set decision_json=?,status='READY',error_message=null,updated_at=now() where id=?",
                write(decision),row.get("id"));
            return json.createObjectNode().put("saved",true).put("planId",number(row,"id"));
        });
    }

    public ObjectNode apply(long tenant,long project,long id,JsonNode decision) {
        var user=permissions.require(tenant,project,"ELEMENT:EDIT");
        transactions.executeWithoutResult(tx -> {
            var script=script(tenant,project,true);var row=plan(tenant,project,id,true);
            if("APPLIED".equals(row.get("status"))) return;
            check("READY".equals(row.get("status")),"只有待确认方案可以应用。");
            check(number(script,"id")==number(row,"script_id") && number(script,"current_version_id")==number(row,"script_version_id"),"剧本版本已变化，请重新分析。");
            requireIdle(tenant,project);
            JsonNode input=parse(row.get("input_json"));
            check(hash(capture(tenant,project,number(row,"script_id"))).equals(row.get("input_fingerprint")),"资产、形态或剧本已变化，请重新分析。");
            PropSimplificationDecision.validate(input,decision);
            var previous=jdbc.queryForList("select * from prop_production_policy where tenant_id=? and project_id=? and script_id=?",tenant,project,row.get("script_id"));
            jdbc.update("delete from prop_production_policy where tenant_id=? and project_id=? and script_id=?",tenant,project,row.get("script_id"));
            for(JsonNode group:decision.path("groups")) applyGroup(tenant,project,user.userId(),row,input,group);
            var variantIds=new ArrayList<Long>();
            capture(tenant,project,number(row,"script_id")).path("variants").forEach(v -> variantIds.add(v.path("id").asLong()));
            jdbc.update("update prop_simplification_plan set status='APPLIED',decision_json=?,previous_policies_json=?,applied_variant_ids_json=?,updated_at=now() where id=?",
                write(decision),write(previous),write(variantIds),id);
        });
        return status(tenant,project);
    }

    public ObjectNode undo(long tenant,long project,long id) {
        permissions.require(tenant,project,"ELEMENT:EDIT");
        transactions.executeWithoutResult(tx -> {
            script(tenant,project,true);var row=plan(tenant,project,id,true);
            if("REVERTED".equals(row.get("status"))) return;
            var latest=jdbc.queryForList("select id from prop_simplification_plan where tenant_id=? and project_id=? and status='APPLIED' order by id desc limit 1",Long.class,tenant,project);
            check(!latest.isEmpty() && latest.get(0)==id,"只能撤销最近应用的精简方案。");
            requireIdle(tenant,project);
            // References selected while sharing remain valid after restoring original ownership.
            var refs=jdbc.queryForList("""
                select r.id,r.asset_id,r.variant_id from storyboard_asset_reference r
                join asset_visual_variant v on v.id=r.variant_id and v.tenant_id=r.tenant_id and v.project_id=r.project_id
                where r.tenant_id=? and r.project_id=? and r.asset_type='PROP' and r.retired_at is null and v.asset_id<>r.asset_id
                """,tenant,project);
            for(var ref:refs) {
                long variant=copyVariant(tenant,project,number(ref,"asset_id"),number(ref,"variant_id"),null,number(row,"created_by"));
                jdbc.update("update storyboard_asset_reference set variant_id=?,updated_at=now() where id=? and tenant_id=? and project_id=?",variant,ref.get("id"),tenant,project);
            }
            jdbc.update("delete from prop_production_policy where tenant_id=? and project_id=? and script_id=?",tenant,project,row.get("script_id"));
            for(JsonNode previous:parse(row.get("previous_policies_json"))) {
                Map<String,Object> restored=new LinkedHashMap<>();
                for(String field:List.of("tenant_id","project_id","script_id","script_version_id","source_asset_id","plan_id")) restored.put(field,previous.path(field).asLong());
                restored.put("policy_json",previous.path("policy_json").asText());restored.put("updated_at",now());
                insert("prop_production_policy",restored);
            }
            jdbc.update("update prop_simplification_plan set status='REVERTED',updated_at=now() where id=?",id);
        });
        return status(tenant,project);
    }

    private void applyGroup(long tenant,long project,long user,Map<String,Object> plan,JsonNode input,JsonNode group) {
        String mode=group.path("mode").asText();if(mode.equals("INDEPENDENT")) return;
        long target=group.path("targetId").asLong();
        Map<Long,JsonNode> assets=PropSimplificationDecision.indexed(input.path("props"));
        Map<Long,Long> mappings=new LinkedHashMap<>();Map<Long,Long> defaults=new HashMap<>();
        Set<Long> groupVersions=new LinkedHashSet<>();
        if(mode.equals("SHARED")) {
            long visual=primary(tenant,project,target,user,assets.get(target));
            input.path("variants").forEach(v -> { if(PropSimplificationDecision.ids(group.path("memberIds")).contains(v.path("asset_id").asLong())) mappings.put(v.path("id").asLong(),visual); });
            for(long source:PropSimplificationDecision.ids(group.path("memberIds"))) defaults.put(source,visual);
        } else if(mode.equals("VARIANTS")) {
            primary(tenant,project,target,user,assets.get(target));
            for(JsonNode version:group.path("variants")) {
                List<Long> sourceVariants=PropSimplificationDecision.ids(version.path("sourceVariantIds"));
                long visual=copyVariant(tenant,project,target,sourceVariants.isEmpty()?null:sourceVariants.get(0),version,user);
                groupVersions.add(visual);
                sourceVariants.forEach(v -> mappings.put(v,visual));
                PropSimplificationDecision.ids(version.path("sourceAssetIds")).forEach(a -> defaults.put(a,visual));
            }
        }
        for(long source:PropSimplificationDecision.ids(group.path("memberIds"))) {
            ObjectNode policy=json.createObjectNode().put("mode",mode).put("name",group.path("name").asText())
                .put("targetId",target).put("sourceName",assets.get(source).path("name").asText())
                .put("appearance",assets.get(source).path("appearance").asText("")).put("reason",group.path("reason").asText());
            policy.put("plotFunction",assets.get(source).path("plot_function").asText(""));
            policy.set("sceneIds",group.path("sceneIds").isArray()?group.path("sceneIds"):json.createArrayNode());
            ObjectNode map=policy.putObject("variantMap");Set<Long> allowed=new LinkedHashSet<>();
            for(JsonNode v:input.path("variants")) if(v.path("asset_id").asLong()==source && mappings.containsKey(v.path("id").asLong())) {
                long mapped=mappings.get(v.path("id").asLong());map.put(v.path("id").asText(),mapped);allowed.add(mapped);
                if(v.path("is_primary").asBoolean() && !defaults.containsKey(source)) defaults.put(source,mapped);
            }
            if(defaults.containsKey(source)) {policy.put("defaultVariantId",defaults.get(source));allowed.add(defaults.get(source));}
            if(mode.equals("VARIANTS")) allowed.addAll(groupVersions);
            var ids=policy.putArray("allowedVariantIds");allowed.forEach(ids::add);
            Map<String,Object> row=new LinkedHashMap<>();row.put("tenant_id",tenant);row.put("project_id",project);
            row.put("script_id",plan.get("script_id"));row.put("script_version_id",plan.get("script_version_id"));row.put("source_asset_id",source);
            row.put("plan_id",plan.get("id"));row.put("policy_json",write(policy));row.put("updated_at",now());insert("prop_production_policy",row);
        }
    }

    private long primary(long tenant,long project,long asset,long user,JsonNode source) {
        var rows=jdbc.queryForList("select id from asset_visual_variant where tenant_id=? and project_id=? and asset_type='PROP' and asset_id=? and deleted_at is null order by is_primary desc,id limit 1",Long.class,tenant,project,asset);
        if(!rows.isEmpty()) return rows.get(0);
        ObjectNode version=json.createObjectNode().put("name","标准外观").put("prompt",source.path("prompt").asText()).put("appearance",source.path("appearance").asText());
        return copyVariant(tenant,project,asset,null,version,user);
    }

    private long copyVariant(long tenant,long project,long asset,Long source,JsonNode version,long user) {
        Map<String,Object> row=new LinkedHashMap<>();
        if(source!=null) {
            var originals=jdbc.queryForList("select * from asset_visual_variant where tenant_id=? and project_id=? and asset_type='PROP' and id=? and deleted_at is null",tenant,project,source);
            check(originals.size()==1,"来源形态已变化。");
            var original=originals.get(0);
            if(number(original,"asset_id")==asset && (version==null || Objects.equals(original.get("name"),version.path("name").asText()))) return source;
            for(String field:List.of("name","appearance","prompt","generation_status","current_image_result_id","current_image_url","content_json")) row.put(field,original.get(field));
        } else {row.put("generation_status","NOT_STARTED");row.put("name","标准外观");}
        if(version!=null) for(String field:List.of("name","appearance","prompt")) row.put(field,version.path(field).asText());
        row.put("tenant_id",tenant);row.put("project_id",project);row.put("asset_type","PROP");row.put("asset_id",asset);
        row.put("source_type","PROP_SIMPLIFICATION");row.put("created_by",user);row.put("created_at",now());row.put("updated_at",now());
        Long count=jdbc.queryForObject("select count(*) from asset_visual_variant where tenant_id=? and project_id=? and asset_type='PROP' and asset_id=? and is_primary=true and deleted_at is null",Long.class,tenant,project,asset);
        row.put("is_primary",count!=null&&count==0);return insert("asset_visual_variant",row);
    }

    JsonNode capture(long tenant,long project,long script) {
        ObjectNode input=json.createObjectNode();
        input.set("props",json.valueToTree(jdbc.queryForList("select id,name,appearance,plot_function,prompt,content_json,updated_at from prop_asset where tenant_id=? and project_id=? and script_id=? and deleted_at is null order by id",tenant,project,script)));
        input.set("scenes",json.valueToTree(jdbc.queryForList("select id,name,description,updated_at from scene_asset where tenant_id=? and project_id=? and script_id=? and deleted_at is null order by id",tenant,project,script)));
        input.set("variants",json.valueToTree(jdbc.queryForList("select v.* from asset_visual_variant v join prop_asset a on a.id=v.asset_id and a.tenant_id=v.tenant_id and a.project_id=v.project_id where v.tenant_id=? and v.project_id=? and a.script_id=? and v.asset_type='PROP' and v.deleted_at is null and a.deleted_at is null order by v.id",tenant,project,script)));
        input.set("episodes",json.valueToTree(jdbc.queryForList("select id,episode_no,content,content_fingerprint from script_episode where tenant_id=? and project_id=? and script_id=? and status='ACTIVE' and retired_at is null order by id",tenant,project,script)));
        input.set("bindings",json.valueToTree(jdbc.queryForList("select id,asset_id,variant_id,episode_id,is_preferred from asset_visual_variant_episode where tenant_id=? and project_id=? and script_id=? and asset_type='PROP' and binding_status='ACTIVE' and retired_at is null order by id",tenant,project,script)));
        input.set("policies",json.valueToTree(jdbc.queryForList("select source_asset_id,policy_json from prop_production_policy where tenant_id=? and project_id=? and script_id=? order by source_asset_id",tenant,project,script)));
        return input;
    }
    private void requireIdle(long tenant,long project) {
        normalization.requireReady(tenant,project);
        Long busy=jdbc.queryForObject("select count(*) from ai_execution_task where tenant_id=? and project_id=? and status in ('PENDING','RUNNING')",Long.class,tenant,project);
        check(busy==null||busy==0,"请等待当前项目的提取、出图或分镜等任务完成后再应用或撤销。");
    }
    private Map<String,Object> script(long tenant,long project,boolean lock) {
        if(lock) production.lockProject(tenant,project);
        var rows=jdbc.queryForList("select id,current_version_id from script where tenant_id=? and project_id=? and deleted_at is null order by id desc limit 1"+(lock?" for update":""),tenant,project);
        check(!rows.isEmpty()&&rows.get(0).get("current_version_id")!=null,"请先导入剧本。");return rows.get(0);
    }
    private Map<String,Object> plan(long tenant,long project,long id,boolean lock) {
        var rows=jdbc.queryForList("select * from prop_simplification_plan where tenant_id=? and project_id=? and id=?"+(lock?" for update":""),tenant,project,id);
        if(rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND,"精简方案不存在。");return rows.get(0);
    }
    private long insert(String table,Map<String,Object> row) {return new SimpleJdbcInsert(jdbc).withTableName(table).usingColumns(row.keySet().toArray(String[]::new)).usingGeneratedKeyColumns("id").executeAndReturnKey(row).longValue();}
    private long number(Map<String,Object> row,String field) {return ((Number)row.get(field)).longValue();}
    private java.sql.Timestamp now() {return new java.sql.Timestamp(System.currentTimeMillis());}
    private void check(boolean value,String message) {PropSimplificationDecision.require(value,message);}
    private String hash(JsonNode value) {return EpisodePromptContextFactory.hash(write(value));}
    private String write(Object value) {try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private JsonNode parse(Object value) {try{return value==null?json.createObjectNode():json.readTree(String.valueOf(value));}catch(Exception e){throw new IllegalStateException(e);}}
}
