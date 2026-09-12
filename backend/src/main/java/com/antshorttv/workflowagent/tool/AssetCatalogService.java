package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.script.AssetIdentityNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Catalog pages are observations, never proof that an identity does not exist. */
@Service
public class AssetCatalogService {
    static final int BYTE_LIMIT = 32768;
    private static final Map<String,String> TABLES = Map.of(
        "CHARACTER","character_asset","SCENE","scene_asset","PROP","prop_asset");
    private static final Map<String,String> PREFIXES = Map.of("CHARACTER","c_","SCENE","s_","PROP","p_");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssetCatalogService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public ObjectNode search(ToolExecutionContext context, JsonNode args) {
        String type = args.path("assetType").asText();
        requireScope(context, type);
        String query = AssetIdentityNormalizer.normalize(args.path("query").asText(""));
        int size = Math.max(1, Math.min(50, args.path("pageSize").asInt(50)));
        long after = cursor(context, args.path("cursor").asText(""), type + ":" + query);
        List<Map<String,Object>> rows = identities(context, type);
        var matching = rows.stream().filter(row -> query.isEmpty() || matches(row, query)).toList();
        ObjectNode result=json.createObjectNode();
        result.put("total",matching.size());
        ArrayNode values=result.putArray("items");
        long last=after;
        boolean more=false;
        for (Map<String,Object> row : matching) {
            long id=((Number)row.get("id")).longValue();
            if (id<=after) continue;
            ObjectNode item=summary(type,row);
            if (values.size()>=size || bytes(values)+bytes(item)>BYTE_LIMIT-2048) {
                if (values.isEmpty()) throw invalid("资产摘要过长，请缩短资产名称或别名后重试。");
                more=true; break;
            }
            values.add(item); last=id;
        }
        result.put("hasMore",more);
        result.put("nextCursor",more ? remember(context,type+":"+query,last) : "");
        return result;
    }

    public ObjectNode details(ToolExecutionContext context, JsonNode args) {
        JsonNode keys=args.path("assetKeys");
        if (!keys.isArray() || keys.isEmpty() || keys.size()>10) throw invalid("每次读取 1 到 10 个资产 key。");
        if (keys.size()>1 && !args.path("cursor").asText("").isEmpty()) throw invalid("形态续页必须指定单个资产 key。");
        ObjectNode result=json.createObjectNode();
        ArrayNode assets=result.putArray("items");
        for (JsonNode keyNode:keys) {
            String key=keyNode.asText();
            String type=typeOf(key);
            requireScope(context,type);
            long id=parseId(key);
            List<Map<String,Object>> rows=jdbc.queryForList("select id,name,normalized_name,content_json,prompt from "
                +TABLES.get(type)+" where tenant_id=? and project_id=? and script_id=? and id=? and deleted_at is null",
                context.tenantId(),context.projectId(),context.scriptId(),id);
            if(rows.size()!=1) throw invalid("资产 key 不属于当前剧本或已删除。");
            ObjectNode asset=summary(type,rows.get(0));
            asset.set("content",parse(rows.get(0).get("content_json")));
            if(bytes(asset)>BYTE_LIMIT-2048) throw invalid("单条资产详情超过响应预算，请精简资产设定。");
            long after=cursor(context,args.path("cursor").asText(""),"variant:"+key);
            List<Map<String,Object>> variants=jdbc.queryForList("""
                select id,name,content_json,prompt,is_primary from asset_visual_variant
                where tenant_id=? and project_id=? and asset_type=? and asset_id=? and deleted_at is null
                  and id>? order by id limit 21
                """,context.tenantId(),context.projectId(),type,id,after);
            ArrayNode values=asset.putArray("variants");
            long last=after;
            boolean more=false;
            for(var row:variants) {
                ObjectNode variant=json.createObjectNode();
                variant.put("variantKey","v_"+row.get("id"));
                variant.put("name",String.valueOf(row.get("name")));
                variant.put("hasPrompt",hasText(row.get("prompt")));
                variant.put("primary",Boolean.TRUE.equals(row.get("is_primary"))
                    || row.get("is_primary") instanceof Number n && n.intValue()!=0);
                variant.set("content",parse(row.get("content_json")));
                Integer bound=jdbc.queryForObject("""
                    select count(*) from asset_visual_variant_episode where tenant_id=? and project_id=?
                    and script_id=? and episode_id=? and asset_type=? and asset_id=? and variant_id=?
                    and retired_at is null and binding_status='ACTIVE'
                    """,Integer.class,context.tenantId(),context.projectId(),context.scriptId(),
                    context.episodeId(),type,id,row.get("id"));
                variant.put("episodeBound",bound!=null && bound>0);
                if(values.size()>=20 || bytes(result)+bytes(asset)+bytes(variant)>BYTE_LIMIT-2048) {
                    if(values.isEmpty()) throw invalid("单条形态详情超过响应预算，请逐个资产读取或精简形态设定。");
                    more=true; break;
                }
                values.add(variant);last=((Number)row.get("id")).longValue();
            }
            asset.put("hasMore",more);
            asset.put("nextCursor",more?remember(context,"variant:"+key,last):"");
            assets.add(asset);
            if(bytes(result)>BYTE_LIMIT) throw invalid("详情响应过大，请减少每次读取的资产 key。");
        }
        return result;
    }

    public ObjectNode candidates(ToolExecutionContext context,String content) {
        ObjectNode result=json.createObjectNode();
        ObjectNode pages=result.putObject("pages");
        String source=AssetIdentityNormalizer.normalize(content);
        for(String type:List.of("CHARACTER","SCENE","PROP")) {
            String field=switch(type) {case "CHARACTER"->"characters";case "SCENE"->"scenes";default->"props";};
            ArrayNode values=result.putArray(field);
            if(!included(context,type)) continue;
            var rows=identities(context,type);
            var bound=jdbc.queryForList("""
                select distinct asset_id from asset_visual_variant_episode where tenant_id=? and project_id=?
                  and script_id=? and episode_id=? and asset_type=? and retired_at is null and binding_status='ACTIVE'
                """,Long.class,context.tenantId(),context.projectId(),context.scriptId(),context.episodeId(),type);
            var relevant=rows.stream().filter(row->bound.contains(((Number)row.get("id")).longValue())
                || mentioned(row,source)).sorted(java.util.Comparator.comparingInt(
                    row->bound.contains(((Number)row.get("id")).longValue())?0:1)).toList();
            for(var row:relevant) {
                var item=summary(type,row);
                if(values.size()>=50 || bytes(result)+bytes(item)>BYTE_LIMIT-2048) break;
                values.add(item);
            }
            var page=pages.putObject(field);
            page.put("total",rows.size());page.put("candidateTotal",relevant.size());
            page.put("hasMore",values.size()<rows.size());
            page.put("nextCursor",remember(context,type+":",0));
            page.put("assetType",type);
        }
        return result;
    }

    private List<Map<String,Object>> identities(ToolExecutionContext c,String type) {
        return jdbc.queryForList("select id,name,normalized_name,content_json,prompt from "+TABLES.get(type)
            +" where tenant_id=? and project_id=? and script_id=? and deleted_at is null order by id",
            c.tenantId(),c.projectId(),c.scriptId());
    }

    ArrayNode speakerNames(ToolExecutionContext context) {
        ArrayNode result=json.createArrayNode();
        for(var row:identities(context,"CHARACTER")) result.add(summary("CHARACTER",row));
        return result;
    }

    private ObjectNode summary(String type,Map<String,Object> row) {
        ObjectNode item=json.createObjectNode();
        item.put("assetKey",PREFIXES.get(type)+row.get("id"));
        item.put("name",String.valueOf(row.get("name")));
        item.put("normalizedName",String.valueOf(row.get("normalized_name")));
        item.put("hasPrompt",hasText(row.get("prompt")));
        item.set("aliases",aliasNames(row));
        item.putArray("variants");
        return item;
    }

    private ArrayNode aliasNames(Map<String,Object> row) {
        ArrayNode names=json.createArrayNode();
        for(JsonNode alias:parse(row.get("content_json")).path("aliases")) {
            String name=alias.isTextual()?alias.asText():alias.path("name").asText();
            if(!name.isBlank()) names.add(name);
        }
        return names;
    }

    private boolean matches(Map<String,Object> row,String query) {
        if(query.equals(AssetIdentityNormalizer.normalize(String.valueOf(row.get("name"))))
            || query.equals(row.get("normalized_name"))) return true;
        for(JsonNode alias:aliasNames(row)) if(query.equals(AssetIdentityNormalizer.normalize(alias.asText()))) return true;
        return false;
    }

    private boolean mentioned(Map<String,Object> row,String source) {
        String name=AssetIdentityNormalizer.normalize(String.valueOf(row.get("name")));
        if(!name.isEmpty() && source.contains(name)) return true;
        for(JsonNode alias:aliasNames(row)) {
            String value=AssetIdentityNormalizer.normalize(alias.asText());
            if(!value.isEmpty() && source.contains(value)) return true;
        }
        return false;
    }

    private String remember(ToolExecutionContext c,String query,long after) {
        String key=UUID.randomUUID().toString();
        c.runState().put("assetCursor:"+key,new Cursor(c.tenantId(),c.projectId(),c.scriptId(),query,after));
        return key;
    }
    private long cursor(ToolExecutionContext c,String key,String query) {
        if(key.isEmpty()) return 0;
        Cursor saved=c.runState().get("assetCursor:"+key,Cursor.class);
        if(saved==null || !saved.tenant().equals(c.tenantId()) || !saved.project().equals(c.projectId())
            || !saved.script().equals(c.scriptId()) || !saved.query().equals(query)) throw invalid("资产目录游标不属于当前查询。");
        return saved.after();
    }
    private void requireScope(ToolExecutionContext c,String type) {
        if(!TABLES.containsKey(type) || c.tenantId()==null || c.projectId()==null || c.scriptId()==null
            || !included(c,type) || !c.permissions().contains("SCRIPT:VIEW")) throw invalid("资产检索作用域未授权。");
    }
    private boolean included(ToolExecutionContext c,String type) {
        String scope=c.runState().get("assetScope",String.class);
        return scope==null || "ALL".equals(scope) || type.equals(scope);
    }
    private String typeOf(String key) {
        return PREFIXES.entrySet().stream().filter(e->key.startsWith(e.getValue())).map(Map.Entry::getKey)
            .findFirst().orElseThrow(()->invalid("资产 key 类型无效。"));
    }
    private long parseId(String key) {
        try {long id=Long.parseLong(key.substring(2));if(id>0)return id;} catch(RuntimeException ignored) { }
        throw invalid("资产 key 无效。");
    }
    private JsonNode parse(Object value) {
        if(value==null)return json.createObjectNode();
        try{return json.readTree(value.toString());}catch(Exception e){throw invalid("资产元数据损坏。");}
    }
    private int bytes(JsonNode value) {return value.toString().getBytes(StandardCharsets.UTF_8).length;}
    private boolean hasText(Object value) {return value!=null && !value.toString().isBlank();}
    private BusinessException invalid(String message) {return new BusinessException(ErrorCode.VALIDATION_ERROR,message);}
    private record Cursor(Long tenant,Long project,Long script,String query,long after) {}
}
