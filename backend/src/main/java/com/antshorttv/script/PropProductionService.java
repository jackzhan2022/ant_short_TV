package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Production reuse never changes the identity or ownership of the source prop. */
@Service
public class PropProductionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public PropProductionService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    /** Short admission lock shared by policy application and production writes; never held during AI inference. */
    public void lockProject(long tenant,long project) {
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
            && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly())
            jdbc.queryForList("select id from script where tenant_id=? and project_id=? and deleted_at is null order by id for update",tenant,project);
    }

    public JsonNode policy(long tenant, long project, long asset) {
        var rows = jdbc.queryForList("""
            select p.policy_json,a.name,a.appearance,a.plot_function from prop_production_policy p
            join script s on s.id=p.script_id and s.tenant_id=p.tenant_id and s.project_id=p.project_id
              and s.current_version_id=p.script_version_id and s.deleted_at is null
            join prop_asset a on a.id=p.source_asset_id and a.tenant_id=p.tenant_id and a.project_id=p.project_id and a.deleted_at is null
            where p.tenant_id=? and p.project_id=? and p.source_asset_id=?
            """, tenant, project, asset);
        if(rows.isEmpty()) return json.createObjectNode();
        JsonNode policy=parse(rows.get(0).get("policy_json"));
        if(!unchanged(policy,rows.get(0))) return json.createObjectNode();
        long target=policy.path("targetId").asLong(asset);
        if(!"SCENE".equals(policy.path("mode").asText()) && target!=asset && policy(tenant,project,target).isEmpty())
            return json.createObjectNode();
        return policy;
    }

    public record Target(long assetId, Long variantId, boolean scene, boolean unresolved) {}

    public Target target(long tenant, long project, long source, Long variant, Long episode) {
        JsonNode policy = policy(tenant, project, source);
        if (policy.isEmpty()) return new Target(source, variant, false, false);
        if ("SCENE".equals(policy.path("mode").asText())) return new Target(source, null, true, false);
        long target = policy.path("targetId").asLong(source);
        Long sourceVariant = variant;
        if (sourceVariant == null && episode != null) {
            var preferred = jdbc.queryForList("""
                select variant_id from asset_visual_variant_episode where tenant_id=? and project_id=?
                  and asset_type='PROP' and asset_id=? and episode_id=? and is_preferred=true
                  and binding_status='ACTIVE' and retired_at is null
                """, Long.class, tenant, project, source, episode);
            if (!preferred.isEmpty()) sourceVariant = preferred.get(0);
        }
        JsonNode mapped = sourceVariant == null ? policy.path("defaultVariantId")
            : policy.path("variantMap").path(String.valueOf(sourceVariant));
        // Explicit target versions are accepted only when authorized by this source's policy.
        if (sourceVariant != null && mapped.isMissingNode() && policy.path("allowedVariantIds").isArray())
            for (JsonNode allowed : policy.path("allowedVariantIds")) if (allowed.asLong() == sourceVariant) mapped = allowed;
        Long resolved = mapped.isIntegralNumber() && mapped.asLong() > 0 ? mapped.asLong() : null;
        boolean ambiguous = resolved == null && (sourceVariant != null || "VARIANTS".equals(policy.path("mode").asText()));
        return new Target(target, resolved, false, ambiguous);
    }

    public List<Long> productionIds(long tenant, long project, List<Long> sources) {
        Set<Long> result = new LinkedHashSet<>();
        for (long source : sources) {
            Target target = target(tenant, project, source, null, null);
            if (!target.scene()) result.add(target.assetId());
        }
        return List.copyOf(result);
    }

    public JsonNode catalog(long tenant, long project) {
        ObjectNode result = json.createObjectNode();
        var rows = jdbc.queryForList("""
            select p.source_asset_id,p.policy_json,a.name,a.appearance,a.plot_function from prop_production_policy p
            join script s on s.id=p.script_id and s.tenant_id=p.tenant_id and s.project_id=p.project_id
              and s.current_version_id=p.script_version_id and s.deleted_at is null
            join prop_asset a on a.id=p.source_asset_id and a.tenant_id=p.tenant_id and a.project_id=p.project_id and a.deleted_at is null
            where p.tenant_id=? and p.project_id=? order by p.source_asset_id
            """, tenant, project);
        var policies = result.putArray("policies");
        Map<Long,Map<String,Object>> bySource=new HashMap<>();
        for(var row:rows) bySource.put(((Number)row.get("source_asset_id")).longValue(),row);
        for (var row : rows) {
            JsonNode policy=parse(row.get("policy_json"));
            var master=bySource.get(policy.path("targetId").asLong());
            boolean masterValid="SCENE".equals(policy.path("mode").asText()) || master!=null && unchanged(parse(master.get("policy_json")),master);
            if(unchanged(policy,row) && masterValid) policies.add(((ObjectNode)policy).deepCopy().put("sourceId", ((Number) row.get("source_asset_id")).longValue()));
        }
        var applied = jdbc.queryForList("select input_json,script_version_id,script_id,applied_variant_ids_json from prop_simplification_plan where tenant_id=? and project_id=? and status='APPLIED' order by id desc limit 1",tenant,project);
        boolean changed=false;
        if(!applied.isEmpty()) {
            var original=PropSimplificationDecision.indexed(parse(applied.get(0).get("input_json")).path("props"));
            var current=jdbc.queryForList("select id,name,appearance,plot_function from prop_asset where tenant_id=? and project_id=? and deleted_at is null order by id",tenant,project);
            changed=current.size()!=original.size();
            for(var asset:current) {
                JsonNode frozen=original.get(((Number)asset.get("id")).longValue());
                if(frozen==null) {changed=true;continue;}
                for(String field:List.of("name","appearance","plot_function"))
                    if(!frozen.path(field).asText("").equals(asset.get(field)==null?"":String.valueOf(asset.get(field)))) changed=true;
            }
            Long version=jdbc.queryForObject("select current_version_id from script where tenant_id=? and project_id=? and id=? and deleted_at is null",Long.class,tenant,project,applied.get(0).get("script_id"));
            if(!Objects.equals(version,((Number)applied.get(0).get("script_version_id")).longValue())) changed=true;
            Set<Long> expected=new HashSet<>();parse(applied.get(0).get("applied_variant_ids_json")).forEach(v -> expected.add(v.asLong()));
            var live=jdbc.queryForList("select v.id from asset_visual_variant v join prop_asset a on a.id=v.asset_id and a.tenant_id=v.tenant_id and a.project_id=v.project_id and a.deleted_at is null where v.tenant_id=? and v.project_id=? and v.asset_type='PROP' and v.deleted_at is null",Long.class,tenant,project);
            if(!expected.equals(new HashSet<>(live))) changed=true;
        }
        result.put("needsReview",changed);
        return result;
    }

    StoryboardAssetReferenceResponse reference(long tenant,long project,StoryboardAssetReferenceResponse source) {
        if(!"PROP".equals(source.assetType())||source.assetId()==null||policy(tenant,project,source.assetId()).isEmpty()) return source;
        Target target=target(tenant,project,source.assetId(),source.variantId(),null);
        Long variant=target.scene()||target.unresolved()?null:target.variantId();
        String name=null,url=null,status=target.scene()?"SCENE_INCLUDED":"ASSET_PENDING";
        if(variant!=null) {
            var rows=jdbc.queryForList("select name,current_image_url,current_image_result_id,generation_status from asset_visual_variant where tenant_id=? and project_id=? and asset_id=? and id=? and deleted_at is null",tenant,project,target.assetId(),variant);
            if(!rows.isEmpty()) {
                var row=rows.get(0);name=String.valueOf(row.get("name"));url=row.get("current_image_url")==null?null:String.valueOf(row.get("current_image_url"));
                if("COMPLETED".equals(row.get("generation_status")) && (row.get("current_image_result_id")!=null||url!=null&&!url.isBlank())) status="RESOLVED";
            }
        }
        return new StoryboardAssetReferenceResponse(source.id(),source.assetType(),source.assetId(),source.assetName(),variant,name,url,
            source.referenceRole(),source.sortOrder(),status,source.sourceType(),source.sourceName(),source.lockedByUser());
    }

    public String sceneContext(long tenant, long project, Long scene) {
        StringBuilder result = new StringBuilder();
        for (JsonNode policy : catalog(tenant, project).path("policies")) {
            if (!"SCENE".equals(policy.path("mode").asText())) continue;
            boolean matches = scene == null;
            for (JsonNode id : policy.path("sceneIds")) if (scene != null && id.asLong() == scene) matches = true;
            if (matches) result.append("\n场景内物品：").append(policy.path("sourceName").asText())
                .append("；场景 ID：").append(policy.path("sceneIds")).append("；外观：")
                .append(policy.path("appearance").asText()).append("。需要在场景及相关镜头中呈现，现有场景图需人工确认或更新。\n");
        }
        return result.toString();
    }
    private JsonNode parse(Object value) {
        try { return json.readTree(String.valueOf(value)); } catch (Exception error) { throw new IllegalStateException("道具制作关系读取失败。", error); }
    }
    private boolean unchanged(JsonNode policy,Map<String,Object> row) {
        return policy.path("sourceName").asText("").equals(text(row.get("name")))
            && policy.path("appearance").asText("").equals(text(row.get("appearance")))
            && policy.path("plotFunction").asText("").equals(text(row.get("plot_function")));
    }
    private String text(Object value) {return value==null?"":String.valueOf(value);}
}
