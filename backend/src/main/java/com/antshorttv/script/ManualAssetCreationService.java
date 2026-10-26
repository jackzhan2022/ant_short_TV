package com.antshorttv.script;

import static com.antshorttv.script.ManualAssetConfiguration.invalid;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Statement;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualAssetCreationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProjectPermissionGuard permissions;
    private final ManualAssetConfiguration configuration;
    private final AssetExtractionCoordination coordination;
    private final AssetNormalizationGuard normalization;
    private final AssetVisualVariantService variants;
    public ManualAssetCreationService(JdbcTemplate jdbc,ObjectMapper json,ProjectPermissionGuard permissions,
        ManualAssetConfiguration configuration,AssetExtractionCoordination coordination,AssetNormalizationGuard normalization,
        AssetVisualVariantService variants) {
        this.jdbc=jdbc;this.json=json;this.permissions=permissions;this.configuration=configuration;
        this.coordination=coordination;this.normalization=normalization;this.variants=variants;
    }

    public Options options(Long tenant,Long project) {
        permissions.require(tenant,project,"ELEMENT:VIEW");
        boolean editable=true;
        try { permissions.require(tenant,project,"ELEMENT:EDIT"); }
        catch(BusinessException e) { if(e.getErrorCode()!=ErrorCode.FORBIDDEN) throw e; editable=false; }
        Long script=currentScript(tenant,project,false);
        boolean blocked=Boolean.TRUE.equals(normalization.status(tenant,project).get("blocked"));
        String reason=!editable?"你暂无资产编辑权限":script==null?"请先创建或导入剧本":blocked?"资产提取或归一尚未完成":"";
        return new Options(script,reason.isEmpty(),reason,configuration.definitions());
    }

    @Transactional
    public Created create(Long tenant,Long project,Command command) {
        var actor=permissions.require(tenant,project,"ELEMENT:EDIT");
        if(command==null || command.scriptId()==null || command.requestId()==null ||
            !command.requestId().matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
            throw invalid("新增请求不完整，请重新打开表单。");
        String type=command.assetType();
        var built=configuration.build(type,command.configuration());
        String table=ScriptElementType.from(type).tableName();
        String name=identityName(command.name());
        List<String> aliases=validatedAliases(name,command.aliases());
        if(!"PROP".equals(type) && command.ownerCharacterId()!=null) throw invalid("只有道具可配置持有角色。");
        Long script=currentScript(tenant,project,false);
        if(script==null || !script.equals(command.scriptId())) throw invalid("当前剧本已变化或不存在，请刷新页面后重新添加。");
        // Same owner lock as extraction/normalization; held until asset, variant and receipt commit together.
        coordination.lockForManualMutation(tenant,project,script);
        if(!script.equals(currentScript(tenant,project,true))) throw invalid("当前剧本已变化，请刷新后重新添加。");
        String fingerprint=EpisodePromptContextFactory.hash(write(List.of(type,name,aliases,built.values(),
            command.ownerCharacterId()==null?0:command.ownerCharacterId())));
        var receipts=jdbc.queryForList("select * from script_manual_asset_request where tenant_id=? and project_id=? and request_id=? for update",
            tenant,project,command.requestId());
        if(!receipts.isEmpty()) {
            var receipt=receipts.get(0);
            if(!fingerprint.equals(receipt.get("fingerprint")) || ((Number)receipt.get("script_id")).longValue()!=script)
                throw invalid("该保存请求的内容已变化，请重新打开新增表单。");
            return new Created(type,((Number)receipt.get("asset_id")).longValue(),((Number)receipt.get("variant_id")).longValue());
        }
        normalization.requireReady(tenant,project);
        Set<String> identities=new HashSet<>(); identities.add(AssetIdentityNormalizer.normalize(name));
        aliases.forEach(alias->identities.add(AssetIdentityNormalizer.normalize(alias)));
        requireUnique(tenant,project,script,table,identities);
        String owner=ownerName(tenant,project,script,command.ownerCharacterId());
        var metadata=json.createObjectNode();
        var aliasNodes=metadata.putArray("aliases");aliases.forEach(alias->aliasNodes.addObject().put("name",alias));
        metadata.put("description",built.description());
        var manual=metadata.putObject("manualConfiguration");manual.put("version",1);manual.set("values",json.valueToTree(built.values()));
        if(command.ownerCharacterId()!=null) metadata.put("ownerCharacterId",command.ownerCharacterId());
        String prompt=("PROP".equals(type)?name+"。":"")+built.prompt();
        if(prompt.length()>10000) throw invalid("提示词过长，请精简设定。");
        String kindColumn=switch(type) { case "CHARACTER"->"role_type"; case "SCENE"->"scene_type"; default->"prop_type"; };
        String kind=switch(type) { case "CHARACTER"->"SUPPORTING"; case "SCENE"->"LOCATION"; default->"KEY_PROP"; };
        var key=new GeneratedKeyHolder();
        jdbc.update(connection->{
            var statement=connection.prepareStatement("insert into "+table+" (tenant_id,project_id,script_id,name,normalized_name,"
                +kindColumn+",status,source,content_json,prompt,created_by,created_at,updated_at) values (?,?,?,?,?,?,'CONFIRMED','USER',?,?,?,now(),now())",Statement.RETURN_GENERATED_KEYS);
            Object[] args={tenant,project,script,name,AssetIdentityNormalizer.normalize(name),kind,metadata.toString(),prompt,actor.userId()};
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);return statement;
        },key);
        Number generatedId=key.getKeys()!=null && key.getKeys().get("id") instanceof Number value ? value : key.getKey();
        long id=Objects.requireNonNull(generatedId,"资产 id 未生成。").longValue();
        if("CHARACTER".equals(type)) jdbc.update("update character_asset set gender=?,age_range=?,appearance=? where id=?",
            built.values().get("gender"),built.values().get("age"),summary(built.description()),id);
        else if("SCENE".equals(type)) jdbc.update("update scene_asset set description=? where id=?",built.description(),id);
        else jdbc.update("update prop_asset set appearance=?,related_character=? where id=?",summary(built.description()),owner,id);
        var variant=variants.create(tenant,project,type,id,actor.userId(),new AssetVisualVariantService.VariantCommand(
            "主形象",built.description(),prompt,"MANUAL","NOT_STARTED",null,null,true));
        jdbc.update("insert into script_manual_asset_request(tenant_id,project_id,script_id,request_id,fingerprint,asset_type,asset_id,variant_id,created_at) values (?,?,?,?,?,?,?,?,now())",
            tenant,project,script,command.requestId(),fingerprint,type,id,variant.id());
        return new Created(type,id,variant.id());
    }
    private Long currentScript(Long tenant,Long project,boolean lock) {
        return jdbc.queryForList("select id from script where tenant_id=? and project_id=? and deleted_at is null order by updated_at desc,id desc limit 1"+(lock?" for update":""),
            Long.class,tenant,project).stream().findFirst().orElse(null);
    }
    private String ownerName(Long tenant,Long project,Long script,Long owner) {
        if(owner==null) return null;
        var names=jdbc.queryForList("select name from character_asset where tenant_id=? and project_id=? and (script_id=? or script_id is null) and id=? and deleted_at is null for update",
            String.class,tenant,project,script,owner);
        if(names.isEmpty()) throw invalid("持有角色已删除或不属于当前剧本，请重新选择。");
        return names.get(0);
    }
    private void requireUnique(Long tenant,Long project,Long script,String table,Set<String> identities) {
        var rows=jdbc.queryForList("select name,normalized_name,content_json from "+table+" where tenant_id=? and project_id=? and (script_id=? or script_id is null) and deleted_at is null for update",tenant,project,script);
        for(var row:rows) {
            Set<String> existing=new HashSet<>();
            existing.add(AssetIdentityNormalizer.normalize((String)row.get("name")));
            existing.add(AssetIdentityNormalizer.normalize((String)row.get("normalized_name")));
            if(row.get("content_json")!=null) {
                try { for(var alias:json.readTree((String)row.get("content_json")).path("aliases"))
                    existing.add(AssetIdentityNormalizer.normalize(alias.isTextual()?alias.asText():alias.path("name").asText()));
                } catch(java.io.IOException e) { throw new IllegalStateException("现有资产元数据无法读取。",e); }
            }
            if(!Collections.disjoint(existing,identities)) throw invalid("名称或别名与已有资产“"+row.get("name")+"”重复，请使用现有资产或修改名称。");
        }
    }
    private String identityName(String value) {
        String name=value==null?"":value.trim();
        if(name.isEmpty() || name.length()>100 || AssetIdentityNormalizer.normalize(name).isEmpty()) throw invalid("名称及别名需包含文字或数字，且不超过 100 字符。");
        return name;
    }
    private List<String> validatedAliases(String name,List<String> input) {
        if(input==null) return List.of(); if(input.size()>30) throw invalid("最多添加 30 个别名。");
        Set<String> seen=new HashSet<>();seen.add(AssetIdentityNormalizer.normalize(name));List<String> result=new ArrayList<>();
        for(String raw:input) { String alias=identityName(raw); if(!seen.add(AssetIdentityNormalizer.normalize(alias))) throw invalid("本资产名称和别名不能重复。");result.add(alias); }
        return result;
    }
    private String summary(String value) { return value.length()<=500?value:value.substring(0,499)+"…"; }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch(java.io.IOException e) { throw new IllegalStateException(e); } }
    public record Command(String requestId,Long scriptId,String assetType,String name,List<String> aliases,Map<String,String> configuration,Long ownerCharacterId) {}
    public record Created(String assetType,long assetId,long variantId) {}
    public record Options(Long scriptId,boolean canCreate,String disabledReason,Map<String,ManualAssetConfiguration.Definition> definitions) {}
}
