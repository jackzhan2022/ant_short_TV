package com.antshorttv.workflowagent.tool;

import com.antshorttv.rbac.MenuAuthorization;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.AssetIdentityNormalizer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Catalog pages are observations, never proof that an identity does not exist. */
@Service
public class AssetCatalogService {
    static final int BYTE_LIMIT = 32768;
    private static final Map<String,String> TABLES = Map.of(
        "CHARACTER","character_asset","SCENE","scene_asset","PROP","prop_asset");
    private static final Map<String,String> PREFIXES = Map.of("CHARACTER","c_","SCENE","s_","PROP","p_");
    private static final Pattern CHARACTER_IDENTITY_FIELD = Pattern.compile(
        "(?m)^[\\t ]*-[\\t ]*\\*\\*(脸型|眉眼|鼻部|嘴唇|肤质|发型|身材|性别|年龄)\\*\\*[\\t ]*[:：][\\t ]*([^\\r\\n]+)");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProjectPermissionGuard permissionGuard;
    private final com.antshorttv.script.PropProductionService propProduction;

    public AssetCatalogService(JdbcTemplate jdbc, ObjectMapper json, ProjectPermissionGuard permissionGuard) {
        this(jdbc,json,permissionGuard,null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AssetCatalogService(JdbcTemplate jdbc,ObjectMapper json,ProjectPermissionGuard permissionGuard,
        com.antshorttv.script.PropProductionService propProduction) {
        this.jdbc=jdbc; this.json=json; this.permissionGuard=permissionGuard;
        this.propProduction=propProduction;
    }

    public ObjectNode search(ToolExecutionContext context, JsonNode args) {
        if (args.has("queries")) return searchBatch(context, args);
        String type = args.path("assetType").asText();
        requireScope(context, type);
        String query = AssetIdentityNormalizer.normalize(args.path("query").asText(""));
        int size = Math.max(1, Math.min(50, args.path("pageSize").asInt(50)));
        long after = cursor(context, args.path("cursor").asText(""), type + ":" + query);
        return searchPage(context, type, query, size, after, identities(context, type));
    }

    private ObjectNode searchBatch(ToolExecutionContext context, JsonNode args) {
        JsonNode queries = args.path("queries");
        if (!queries.isArray() || queries.isEmpty() || queries.size() > 50) {
            throw invalid("批量检索每次必须提供 1 到 50 个查询。");
        }
        for (String field : List.of("assetType", "query", "cursor", "pageSize")) {
            if (args.has(field)) throw invalid("批量 queries 与单项查询参数不能混用。");
        }
        var types = new LinkedHashSet<String>();
        for (JsonNode query : queries) {
            if (!query.isObject() || !query.path("assetType").isTextual()
                || !query.path("query").isTextual() || query.path("query").asText().length() > 200
                || AssetIdentityNormalizer.normalize(query.path("query").asText()).isBlank()) {
                throw invalid("批量检索每项必须提供 assetType 和 1 到 200 字的非空 query。");
            }
            types.add(query.path("assetType").asText());
        }
        // Authorize the entire batch before reading any identities, including mixed scopes.
        for (String type : types) requireScope(context, type);
        Map<String, List<Map<String, Object>>> rowsByType = new LinkedHashMap<>();
        for (String type : types) rowsByType.put(type, identities(context, type));
        ObjectNode result = json.createObjectNode();
        ArrayNode results = result.putArray("results");
        for (JsonNode request : queries) {
            String type = request.path("assetType").asText();
            String query = AssetIdentityNormalizer.normalize(request.path("query").asText());
            ObjectNode page = searchPage(context, type, query, 50, 0, rowsByType.get(type));
            page.put("assetType", type);
            page.put("query", request.path("query").asText());
            results.add(page);
            if (bytes(result) > BYTE_LIMIT) {
                throw invalid("批量资产检索响应超过 32 KiB，请减少 queries 数量后重试；不得将未返回结果视为未命中。");
            }
        }
        return result;
    }

    private ObjectNode searchPage(ToolExecutionContext context, String type, String query,
                                  int size, long after, List<Map<String, Object>> rows) {
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
        JsonNode keys = args.path("assetKeys");
        if (!keys.isArray() || keys.isEmpty() || keys.size() > 10) throw invalid("每次读取 1 到 10 个资产 key。");
        if (keys.size() > 1 && !args.path("cursor").asText("").isEmpty()) throw invalid("形态续页必须指定单个资产 key。");
        ObjectNode result = json.createObjectNode();
        result.put("detailLevel", "FULL");
        result.put("degraded", false);
        ArrayNode assets = result.putArray("items");
        for (JsonNode keyNode : keys) {
            String key = keyNode.asText();
            String type = typeOf(key);
            requireScope(context, type);
            long id = parseId(key);
            List<Map<String, Object>> rows = jdbc.queryForList("select id,name,normalized_name,content_json,prompt from "
                + TABLES.get(type) + " where tenant_id=? and project_id=? and script_id=? and id=? and deleted_at is null",
                context.tenantId(), context.projectId(), context.scriptId(), id);
            if (rows.size() != 1) throw invalid("资产 key 不属于当前剧本或已删除。");
            ObjectNode asset = summary(type, rows.get(0));
            JsonNode content = parse(rows.get(0).get("content_json"));
            if ("CHARACTER".equals(type) && content.isObject()) {
                // Visual design lives in the canonical prompt, not extraction metadata.
                ObjectNode identity = characterIdentity(rows.get(0).get("prompt"));
                if (!identity.isEmpty()) ((ObjectNode) content).set("visualIdentity", identity);
            }
            asset.set("content", content);
            long after = cursor(context, args.path("cursor").asText(""), "variant:" + key);
            List<Map<String, Object>> variants = jdbc.queryForList("""
                select id,name,content_json,prompt,is_primary from asset_visual_variant
                where tenant_id=? and project_id=? and asset_type=? and asset_id=? and deleted_at is null
                  and id>? order by id limit 21
                """, context.tenantId(), context.projectId(), type, id, after);
            ArrayNode values = asset.putArray("variants");
            long last = after;
            for (var row : variants.subList(0, Math.min(20, variants.size()))) {
                ObjectNode variant = json.createObjectNode();
                variant.put("variantKey", "v_" + row.get("id"));
                variant.put("name", String.valueOf(row.get("name")));
                variant.put("hasPrompt", hasText(row.get("prompt")));
                variant.put("primary", Boolean.TRUE.equals(row.get("is_primary"))
                    || row.get("is_primary") instanceof Number n && n.intValue() != 0);
                variant.set("content", parse(row.get("content_json")));
                Integer bound = jdbc.queryForObject("""
                    select count(*) from asset_visual_variant_episode where tenant_id=? and project_id=?
                    and script_id=? and episode_id=? and asset_type=? and asset_id=? and variant_id=?
                    and retired_at is null and binding_status='ACTIVE'
                    """, Integer.class, context.tenantId(), context.projectId(), context.scriptId(),
                    context.episodeId(), type, id, row.get("id"));
                variant.put("episodeBound", bound != null && bound > 0);
                values.add(variant);
                last = ((Number) row.get("id")).longValue();
            }
            boolean more = variants.size() > values.size();
            asset.put("hasMore", more);
            asset.put("nextCursor", more ? remember(context, "variant:" + key, last) : "");
            assets.add(asset);
        }
        // Only response size selects the fallback. Scope, cursor, metadata and database errors propagate.
        return bytes(result) > BYTE_LIMIT - 2048 ? detailSummaries(context, assets) : result;
    }

    private ObjectNode detailSummaries(ToolExecutionContext context, ArrayNode fullAssets) {
        ObjectNode result = json.createObjectNode();
        result.put("detailLevel", "SUMMARY");
        result.put("degraded", true);
        result.put("degradationReason", "RESPONSE_SIZE_LIMIT");
        result.put("notice", "完整详情超过响应预算，已自动返回摘要；长提示词和证据未返回。"
            + "按 hasMore/nextCursor 续页；hasPrompt=true 的已有提示词不得因省略而重新生成。");
        ArrayNode assets = result.putArray("items");
        // Reserve space for every requested identity rather than letting the first asset consume it all.
        int perAssetBudget = (BYTE_LIMIT - 2048) / fullAssets.size();
        for (JsonNode fullAsset : fullAssets) {
            ObjectNode asset = json.createObjectNode();
            for (String field : List.of("assetKey", "name", "normalizedName", "hasPrompt")) {
                asset.set(field, fullAsset.path(field).deepCopy());
            }
            ObjectNode content = detailSummaryContent(fullAsset.path("content"));
            asset.set("content", content);
            asset.put("contentTruncated", !content.equals(fullAsset.path("content")));
            ArrayNode variants = asset.putArray("variants");
            long last = 0;
            for (JsonNode fullVariant : fullAsset.path("variants")) {
                ObjectNode variant = fullVariant.deepCopy();
                ObjectNode visual = detailSummaryContent(fullVariant.path("content"));
                variant.set("content", visual);
                variant.put("contentTruncated", !visual.equals(fullVariant.path("content")));
                if (bytes(asset) + bytes(variant) + 120 > perAssetBudget) {
                    if (variants.isEmpty()) throw invalid("单条形态摘要超过响应预算，请减少每次读取的资产 key。");
                    break;
                }
                variants.add(variant);
                last = parseId(fullVariant.path("variantKey").asText());
            }
            boolean more = fullAsset.path("hasMore").asBoolean()
                || variants.size() < fullAsset.path("variants").size();
            asset.put("hasMore", more);
            asset.put("nextCursor", more ? remember(context, "variant:" + asset.path("assetKey").asText(), last) : "");
            assets.add(asset);
        }
        if (bytes(result) > BYTE_LIMIT) throw invalid("详情摘要响应过大，请减少每次读取的资产 key。");
        return result;
    }

    private ObjectNode detailSummaryContent(JsonNode content) {
        ObjectNode summary = json.createObjectNode();
        String description = content.path("description").asText("");
        if (description.isBlank()) description = content.path("appearance").asText("");
        if (!description.isBlank()) {
            int length = description.codePointCount(0, description.length());
            summary.put("description", description.substring(0, description.offsetByCodePoints(0, Math.min(120, length))));
        }
        ObjectNode identity = json.createObjectNode();
        for (String field : List.of("脸型", "性别", "年龄")) {
            String value = content.path("visualIdentity").path(field).asText("");
            if (!value.isBlank()) {
                int length = value.codePointCount(0, value.length());
                identity.put(field, value.substring(0, value.offsetByCodePoints(0, Math.min(180, length))));
            }
        }
        if (!identity.isEmpty()) summary.set("visualIdentity", identity);
        return summary;
    }

    private ObjectNode characterIdentity(Object prompt) {
        ObjectNode identity = json.createObjectNode();
        if (prompt == null) return identity;
        var fields = CHARACTER_IDENTITY_FIELD.matcher(prompt.toString());
        while (fields.find()) {
            String value = fields.group(2).trim();
            if (!value.isBlank()) identity.put(fields.group(1), value);
        }
        return identity;
    }

    public ObjectNode candidates(ToolExecutionContext context,String content) {
        EpisodeCatalog selected = episodeCatalog(context, content);
        ObjectNode result=json.createObjectNode();
        ObjectNode pages=result.putObject("pages");
        for(String type:List.of("CHARACTER","SCENE","PROP")) {
            String field=switch(type) {case "CHARACTER"->"characters";case "SCENE"->"scenes";default->"props";};
            ArrayNode values=result.putArray(field);
            if(!included(context,type)) continue;
            ArrayNode relevant = (ArrayNode) selected.assets().path(field);
            for(JsonNode item:relevant) {
                if(values.size()>=50 || bytes(result)+bytes(item)>BYTE_LIMIT-2048) break;
                values.add(item.deepCopy());
            }
            var page=pages.putObject(field);
            page.put("total",selected.totals().get(type));page.put("candidateTotal",relevant.size());
            page.put("hasMore",values.size()<selected.totals().get(type));
            page.put("nextCursor",remember(context,type+":",0));
            page.put("assetType",type);
        }
        return result;
    }

    /** Complete episode subset; unlike the initial candidate page it is not a script-wide directory. */
    public ObjectNode episodeAssets(ToolExecutionContext context, String content) {
        return episodeCatalog(context, content).assets().deepCopy();
    }

    private EpisodeCatalog episodeCatalog(ToolExecutionContext context, String content) {
        String scope = context.runState().get("assetScope", String.class);
        EpisodeCatalog cached = context.runState().get("episodeAssetCatalog", EpisodeCatalog.class);
        if (cached != null && java.util.Objects.equals(cached.tenantId(), context.tenantId())
            && java.util.Objects.equals(cached.projectId(), context.projectId())
            && java.util.Objects.equals(cached.scriptId(), context.scriptId())
            && java.util.Objects.equals(cached.episodeId(), context.episodeId())
            && java.util.Objects.equals(cached.scope(), scope) && cached.content().equals(content)) return cached;

        ObjectNode assets = json.createObjectNode();
        Map<String, Integer> totals = new LinkedHashMap<>();
        String source = AssetIdentityNormalizer.normalize(content);
        for (String type : List.of("CHARACTER", "SCENE", "PROP")) {
            String field = switch (type) {case "CHARACTER" -> "characters"; case "SCENE" -> "scenes"; default -> "props";};
            ArrayNode values = assets.putArray(field);
            if (!included(context, type)) continue;
            // Match names/explicit aliases before loading any visual variants. Prompt text is not needed.
            var rows = jdbc.queryForList("select id,name,normalized_name,content_json,"
                + "case when prompt is not null and trim(prompt) <> '' then true else false end has_prompt from "
                + TABLES.get(type) + " where tenant_id=? and project_id=? and script_id=? and deleted_at is null order by id",
                context.tenantId(), context.projectId(), context.scriptId());
            totals.put(type, rows.size());
            var bound = new LinkedHashSet<>(jdbc.queryForList("""
                select distinct asset_id from asset_visual_variant_episode where tenant_id=? and project_id=?
                  and script_id=? and episode_id=? and asset_type=? and retired_at is null and binding_status='ACTIVE'
                """, Long.class, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(), type));
            var relevant = rows.stream().filter(row -> bound.contains(((Number) row.get("id")).longValue())
                || mentioned(row, source)).sorted(java.util.Comparator.comparingInt(
                    row -> bound.contains(((Number) row.get("id")).longValue()) ? 0 : 1)).toList();
            Map<Long, ArrayNode> variants = candidateVariants(context, type,
                relevant.stream().map(row -> ((Number) row.get("id")).longValue()).toList());
            for (var row : relevant) {
                long assetId = ((Number) row.get("id")).longValue();
                ObjectNode item = summary(type, row);
                item.put("episodeBound", bound.contains(assetId));
                item.set("variants", variants.getOrDefault(assetId, json.createArrayNode()));
                values.add(item);
            }
        }
        applyPropProduction(context, assets);
        EpisodeCatalog selected = new EpisodeCatalog(context.tenantId(), context.projectId(), context.scriptId(),
            context.episodeId(), scope, content, assets, totals);
        context.runState().put("episodeAssetCatalog", selected);
        return selected;
    }

    private record EpisodeCatalog(Long tenantId, Long projectId, Long scriptId, Long episodeId,
                                  String scope, String content, ObjectNode assets, Map<String, Integer> totals) {}

    private void applyPropProduction(ToolExecutionContext context,ObjectNode assets) {
        if(propProduction==null) return;
        var production=propProduction;
        JsonNode policies=production.catalog(context.tenantId(),context.projectId()).path("policies");
        if(policies.isEmpty()) return;
        Map<Long,JsonNode> bySource=new LinkedHashMap<>();
        policies.forEach(p -> bySource.put(p.path("sourceId").asLong(),p));
        for(JsonNode prop:assets.path("props")) {
            long id=parseId(prop.path("assetKey").asText());JsonNode policy=bySource.get(id);
            if(policy==null || !(prop instanceof ObjectNode item)) continue;
            item.set("production",policy);
            ArrayNode variants=item.putArray("variants");
            if("SCENE".equals(policy.path("mode").asText())) continue;
            for(JsonNode allowed:policy.path("allowedVariantIds")) {
                var rows=jdbc.queryForList("select id,name,generation_status,current_image_result_id,current_image_url from asset_visual_variant where tenant_id=? and project_id=? and asset_type='PROP' and asset_id=? and id=? and deleted_at is null",
                    context.tenantId(),context.projectId(),policy.path("targetId").asLong(),allowed.asLong());
                for(var row:rows) variants.addObject().put("variantKey","v_"+row.get("id"))
                    .put("name",String.valueOf(row.get("name"))).put("primary",allowed.asLong()==policy.path("defaultVariantId").asLong())
                    .put("hasPrompt",true).put("episodeBound",false).put("episodePreferred",false)
                    .put("generationStatus",String.valueOf(row.get("generation_status")))
                    .put("imageReady","COMPLETED".equals(row.get("generation_status")) && (row.get("current_image_result_id")!=null||row.get("current_image_url")!=null));
            }
        }
        assets.put("sceneProductionContext",production.sceneContext(context.tenantId(),context.projectId(),null));
    }

    private Map<Long, ArrayNode> candidateVariants(
        ToolExecutionContext context, String type, List<Long> assetIds
    ) {
        if (assetIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(assetIds.size(), "?"));
        List<Object> arguments = new java.util.ArrayList<>(List.of(
            context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(),
            context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(),
            context.tenantId(), context.projectId(), type));
        arguments.addAll(assetIds);
        List<Map<String,Object>> rows = jdbc.queryForList("""
            select variant.id,variant.asset_id,variant.name,variant.is_primary,
                   case when variant.prompt is not null and trim(variant.prompt) <> ''
                     then true else false end has_prompt,
                   variant.generation_status,variant.current_image_result_id,variant.current_image_url,
                   case when exists (select 1 from asset_visual_variant_episode binding
                     where binding.tenant_id=? and binding.project_id=? and binding.script_id=?
                       and binding.episode_id=? and binding.variant_id=variant.id
                       and binding.binding_status='ACTIVE' and binding.retired_at is null)
                     then 1 else 0 end episode_bound,
                   case when exists (select 1 from asset_visual_variant_episode binding
                     where binding.tenant_id=? and binding.project_id=? and binding.script_id=?
                       and binding.episode_id=? and binding.variant_id=variant.id
                       and binding.is_preferred=true and binding.binding_status='ACTIVE'
                       and binding.retired_at is null) then 1 else 0 end episode_preferred
              from asset_visual_variant variant
             where variant.tenant_id=? and variant.project_id=? and variant.asset_type=?
               and variant.asset_id in (%s) and variant.deleted_at is null
             order by variant.asset_id,variant.is_primary desc,variant.id
            """.formatted(placeholders), arguments.toArray());
        Map<Long, ArrayNode> result = new java.util.LinkedHashMap<>();
        for (Map<String,Object> row : rows) {
            Long assetId = ((Number) row.get("asset_id")).longValue();
            ObjectNode variant = json.createObjectNode();
            variant.put("variantKey", "v_" + row.get("id"));
            variant.put("name", String.valueOf(row.get("name")));
            variant.put("primary", truthy(row.get("is_primary")));
            variant.put("episodeBound", truthy(row.get("episode_bound")));
            variant.put("hasPrompt", row.containsKey("has_prompt") ? truthy(row.get("has_prompt")) : hasText(row.get("prompt")));
            variant.put("episodePreferred", truthy(row.get("episode_preferred")));
            variant.put("generationStatus", String.valueOf(row.get("generation_status")));
            String imageUrl = row.get("current_image_url") == null
                ? null : row.get("current_image_url").toString();
            variant.put("imageReady", "COMPLETED".equals(row.get("generation_status"))
                && (row.get("current_image_result_id") != null
                    || imageUrl != null && !imageUrl.isBlank()));
            result.computeIfAbsent(assetId, ignored -> json.createArrayNode()).add(variant);
        }
        return result;
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue() != 0;
    }

    private List<Map<String,Object>> identities(ToolExecutionContext c,String type) {
        return jdbc.queryForList("select id,name,normalized_name,content_json,prompt from "+TABLES.get(type)
            +" where tenant_id=? and project_id=? and script_id=? and deleted_at is null order by id",
            c.tenantId(),c.projectId(),c.scriptId());
    }

    private ObjectNode summary(String type,Map<String,Object> row) {
        ObjectNode item=json.createObjectNode();
        item.put("assetKey",PREFIXES.get(type)+row.get("id"));
        item.put("name",String.valueOf(row.get("name")));
        item.put("normalizedName",String.valueOf(row.get("normalized_name")));
        item.put("hasPrompt",row.containsKey("has_prompt") ? truthy(row.get("has_prompt")) : hasText(row.get("prompt")));
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
        String normalized = AssetIdentityNormalizer.normalize((String) row.get("normalized_name"));
        if (!normalized.isEmpty() && source.contains(normalized)) return true;
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
        if(c==null || !TABLES.containsKey(type) || c.tenantId()==null || c.userId()==null
            || c.projectId()==null || c.scriptId()==null || !included(c,type)) throw invalid("资产检索作用域未授权。");
        boolean trustedBackgroundRun=c.agentRunId()!=null && c.taskId()!=null && c.executionId()!=null
            && c.attemptId()!=null && c.executionVersion()!=null;
        if(!trustedBackgroundRun || !MenuAuthorization.allows(c.permissions(), "SCRIPT:VIEW")) {
            permissionGuard.require(c.tenantId(),c.projectId(),"SCRIPT:VIEW");
        }
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
