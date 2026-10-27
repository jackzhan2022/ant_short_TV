package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit, transactional decisions on reported same-character outfit pairs. */
@Service
public class CharacterVariantResolutionService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ProjectPermissionGuard permissions;
    private final AssetNormalizationGuard normalization;
    private final TransactionTemplate transactions;

    public CharacterVariantResolutionService(JdbcTemplate jdbc,ObjectMapper json,ProjectPermissionGuard permissions,
        AssetNormalizationGuard normalization,PlatformTransactionManager transactions) {
        this.jdbc=jdbc; this.json=json; this.permissions=permissions; this.normalization=normalization;
        this.transactions=new TransactionTemplate(transactions);
    }

    public record Resolution(long leftVariantId,long rightVariantId,String decision,Long keptVariantId,String fingerprint) {}
    private record Context(long tenant,long project,long checkId,long characterId,long left,long right,
        String pairFingerprint,Map<String,Object> character,JsonNode frozen,List<Map<String,Object>> variants) {}

    public ObjectNode preview(long tenant,long project,long checkId,long characterId,long left,long right) {
        permissions.require(tenant,project,"ELEMENT:EDIT");
        Context context=load(tenant,project,checkId,characterId,left,right,false);
        unchanged(context);
        return preview(context);
    }

    public ObjectNode resolve(long tenant,long project,long checkId,long characterId,Resolution command) {
        var user=permissions.require(tenant,project,"ELEMENT:EDIT");
        check(command != null && Set.of("MERGED","DISTINCT").contains(Objects.toString(command.decision(),"")),"请选择有效的形态处理方式。");
        return transactions.execute(tx -> {
            Context context=load(tenant,project,checkId,characterId,command.leftVariantId(),command.rightVariantId(),true);
            normalization.requireReady(tenant,project);
            var existing=jdbc.queryForList("""
                select decision,kept_variant_id from character_variant_similarity_resolution
                 where tenant_id=? and project_id=? and left_variant_id=? and right_variant_id=? and pair_fingerprint=?
                """,tenant,project,context.left(),context.right(),context.pairFingerprint());
            if (!existing.isEmpty()) {
                var old=existing.get(0);
                check(command.decision().equals(old.get("decision"))
                    && (!"MERGED".equals(command.decision()) || Objects.equals(command.keptVariantId(),old.get("kept_variant_id"))),
                    "这组形态已经处理，请刷新检查结果。");
                return outcome(context,command);
            }
            unchanged(context);
            ObjectNode preview=preview(context);
            check(preview.path("fingerprint").asText().equals(command.fingerprint()),"形态图片、设定或关联范围已有变化，请重新打开确认窗口。");
            if ("MERGED".equals(command.decision())) {
                check(command.keptVariantId()!=null && (command.keptVariantId()==context.left() || command.keptVariantId()==context.right()),"保留形态必须来自这组检查结果。");
                check(preview.path("canMerge").asBoolean(),preview.path("disabledReason").asText("当前形态无法合并。"));
                long kept=command.keptVariantId(), removed=kept==context.left()?context.right():context.left();
                var source=variant(context,removed); var target=variant(context,kept);
                check(!Boolean.TRUE.equals(source.get("is_primary")),"涉及默认形态时，请保留默认形态。");
                mergeBindings(context,removed,kept);
                boolean usable="COMPLETED".equals(target.get("generation_status"))
                    && (target.get("current_image_result_id")!=null || !Objects.toString(target.get("current_image_url"),"").isBlank());
                CharacterVariantReferenceFallback.replace(jdbc,tenant,project,characterId,removed,kept,
                    Objects.toString(context.character().get("name"),"")+"·"+Objects.toString(target.get("name"),""),usable);
                mergeVoices(context,removed,kept);
                check(jdbc.update("update asset_visual_variant set deleted_at=now(),is_primary=false,updated_at=now() where id=? and tenant_id=? and project_id=? and deleted_at is null",
                    removed,tenant,project)==1,"重复形态已被其他操作更新，请刷新后重试。");
            }
            jdbc.update("""
                insert into character_variant_similarity_resolution
                  (tenant_id,project_id,check_id,character_id,left_variant_id,right_variant_id,pair_fingerprint,decision,kept_variant_id,created_by,created_at)
                values (?,?,?,?,?,?,?,?,?,?,now())
                """,tenant,project,checkId,characterId,context.left(),context.right(),context.pairFingerprint(),
                command.decision(),"MERGED".equals(command.decision())?command.keptVariantId():null,user.userId());
            return outcome(context,command);
        });
    }

    private Context load(long tenant,long project,long checkId,long characterId,long left,long right,boolean lock) {
        check(left>0 && right>0 && left!=right,"请选择两个不同的角色形态。");
        long low=Math.min(left,right),high=Math.max(left,right);
        var scripts=jdbc.queryForList("select id,current_version_id from script where tenant_id=? and project_id=? and deleted_at is null order by id desc limit 1"+(lock?" for update":""),tenant,project);
        check(!scripts.isEmpty(),"当前剧本不存在，请重新检查。");
        var script=scripts.get(0);
        var reports=jdbc.queryForList("select * from character_prompt_similarity_check where id=? and tenant_id=? and project_id=? and script_id=?",checkId,tenant,project,script.get("id"));
        check(reports.size()==1 && reports.get(0).get("result_json")!=null,"角色形态检查结果不存在，请重新检查。");
        var report=reports.get(0);
        check(Objects.equals(script.get("current_version_id"),report.get("script_version_id")),"剧本版本已有变化，请重新检查。");
        JsonNode analysis=read(report.get("result_json"));
        boolean found=false;
        for (JsonNode pair:analysis.path("variantPairs"))
            if (pair.path("characterId").asLong()==characterId && Math.min(pair.path("leftVariantId").asLong(),pair.path("rightVariantId").asLong())==low
                && Math.max(pair.path("leftVariantId").asLong(),pair.path("rightVariantId").asLong())==high) found=true;
        check(found,"这组形态不属于当前检查结果，请重新检查。");
        var owners=jdbc.queryForList("select id,name,prompt from character_asset where id=? and tenant_id=? and project_id=? and script_id=? and deleted_at is null and merge_target_id is null"+(lock?" for update":""),characterId,tenant,project,script.get("id"));
        check(owners.size()==1,"角色已删除或合并，请重新检查。");
        if (lock) {
            CharacterVariantReferenceFallback.lockEditableReferences(jdbc,tenant,project,low);
            CharacterVariantReferenceFallback.lockEditableReferences(jdbc,tenant,project,high);
        }
        var variants=jdbc.queryForList("select * from asset_visual_variant where tenant_id=? and project_id=? and asset_type='CHARACTER' and asset_id=? and deleted_at is null order by id"+(lock?" for update":""),tenant,project,characterId);
        JsonNode frozen=read(report.get("input_json"));
        check(!find(frozen,characterId,low).isMissingNode() && !find(frozen,characterId,high).isMissingNode(),"检查形态快照不完整，请重新检查。");
        return new Context(tenant,project,checkId,characterId,low,high,pairFingerprint(frozen,characterId,low,high),owners.get(0),frozen,variants);
    }

    private void unchanged(Context context) {
        for (long id: new long[]{context.left(),context.right()}) {
            Map<String,Object> variant=variant(context,id);
            JsonNode current=snapshot(variant,Objects.toString(context.character().get("prompt"),""));
            check(sameVariant(current,find(context.frozen(),context.characterId(),id)),"这组形态的设定已有变化，请重新检查。");
        }
    }

    private ObjectNode preview(Context context) {
        var left=variant(context,context.left()); var right=variant(context,context.right());
        ObjectNode result=json.createObjectNode().put("characterId",context.characterId()).put("characterName",Objects.toString(context.character().get("name"),""));
        ObjectNode first=previewVariant(context,left),second=previewVariant(context,right);
        result.set("left",first); result.set("right",second);
        TreeSet<Integer> episodes=new TreeSet<>();
        first.path("episodeNumbers").forEach(value->episodes.add(value.asInt()));
        second.path("episodeNumbers").forEach(value->episodes.add(value.asInt()));
        var union=result.putArray("mergedEpisodeNumbers"); episodes.forEach(union::add);
        Set<Long> affected=new HashSet<>(jdbc.queryForList("""
            select distinct r.storyboard_id from storyboard_asset_reference r join storyboard s on s.id=r.storyboard_id
              and s.tenant_id=r.tenant_id and s.project_id=r.project_id and s.deleted_at is null
             where r.tenant_id=? and r.project_id=? and r.asset_type='CHARACTER' and r.asset_id=?
               and r.variant_id in (?,?) and r.retired_at is null
            """,Long.class,context.tenant(),context.project(),context.characterId(),context.left(),context.right()));
        for (var row:jdbc.queryForList("select id,prompt_document_json from storyboard where tenant_id=? and project_id=? and deleted_at is null and (prompt_document_json like ? or prompt_document_json like ?)",context.tenant(),context.project(),"%"+context.left()+"%","%"+context.right()+"%")) {
            try {
                for (JsonNode node:read(row.get("prompt_document_json")).path("nodes"))
                    if ("mention".equals(node.path("type").asText()) && "IMAGE".equals(node.path("mediaType").asText())
                        && "ASSET_VISUAL_VARIANT".equals(node.path("sourceType").asText())
                        && (node.path("sourceId").asLong()==context.left() || node.path("sourceId").asLong()==context.right())
                        && (!node.hasNonNull("assetId") || node.path("assetId").asLong()==context.characterId())) affected.add(number(row,"id"));
            } catch (BusinessException invalid) { affected.add(number(row,"id")); }
        }
        result.put("affectedStoryboardCount",affected.size());
        Integer conflicts=jdbc.queryForObject("""
            select count(*) from storyboard_character_voice a join storyboard_character_voice b
              on a.tenant_id=b.tenant_id and a.project_id=b.project_id and a.storyboard_id=b.storyboard_id and a.character_id=b.character_id
              join storyboard s on s.id=a.storyboard_id and s.tenant_id=a.tenant_id and s.project_id=a.project_id and s.deleted_at is null
             where a.tenant_id=? and a.project_id=? and a.character_id=? and a.variant_id=? and b.variant_id=?
            """,Integer.class,context.tenant(),context.project(),context.characterId(),context.left(),context.right());
        result.put("voiceConflictCount",conflicts==null?0:conflicts);
        long recommended=Boolean.TRUE.equals(left.get("is_primary"))?context.left():Boolean.TRUE.equals(right.get("is_primary"))?context.right():
            !Objects.toString(left.get("current_image_url"),"").isBlank()?context.left():!Objects.toString(right.get("current_image_url"),"").isBlank()?context.right():context.left();
        boolean generating="GENERATING".equals(left.get("generation_status")) || "GENERATING".equals(right.get("generation_status"));
        result.put("recommendedKeptVariantId",recommended).put("canMerge",!generating);
        if (generating) result.put("disabledReason","形态图片正在生成，请等待完成后再合并。");
        result.put("fingerprint",CharacterPromptSimilarityService.fingerprint(result));
        return result;
    }

    private ObjectNode previewVariant(Context context,Map<String,Object> row) {
        ObjectNode result=snapshot(row,Objects.toString(context.character().get("prompt"),""));
        result.put("currentImageUrl",Objects.toString(row.get("current_image_url"),""))
            .put("generationStatus",Objects.toString(row.get("generation_status"),"NOT_STARTED"));
        if (row.get("current_image_result_id") instanceof Number id) result.put("currentImageResultId",id.longValue());
        var episodes=result.putArray("episodeNumbers");
        jdbc.queryForList("""
            select distinct e.episode_no from asset_visual_variant_episode b join script_episode e
              on e.id=b.episode_id and e.tenant_id=b.tenant_id and e.project_id=b.project_id
             where b.tenant_id=? and b.project_id=? and b.asset_type='CHARACTER' and b.asset_id=? and b.variant_id=?
               and b.retired_at is null and b.binding_status='ACTIVE' and e.status='ACTIVE' and e.retired_at is null
             order by e.episode_no
            """,Integer.class,context.tenant(),context.project(),context.characterId(),row.get("id")).forEach(episodes::add);
        return result;
    }

    private void mergeBindings(Context context,long removed,long kept) {
        var bindings=jdbc.queryForList("""
            select b.* from asset_visual_variant_episode b join script_episode e
              on e.id=b.episode_id and e.tenant_id=b.tenant_id and e.project_id=b.project_id
             where b.tenant_id=? and b.project_id=? and b.asset_type='CHARACTER' and b.asset_id=?
               and b.variant_id in (?,?) and b.retired_at is null and e.status='ACTIVE' and e.retired_at is null
             order by b.id for update
            """,context.tenant(),context.project(),context.characterId(),removed,kept);
        Map<Long,Map<String,Object>> targets=new HashMap<>();
        for (var row:bindings) if (number(row,"variant_id")==kept) targets.put(number(row,"episode_id"),row);
        for (var row:bindings) {
            if (number(row,"variant_id")!=removed) continue;
            var target=targets.get(number(row,"episode_id"));
            if (target==null) {
                jdbc.update("update asset_visual_variant_episode set variant_id=?,updated_at=now() where id=? and tenant_id=? and project_id=?",kept,row.get("id"),context.tenant(),context.project());
                targets.put(number(row,"episode_id"),row);
            } else {
                jdbc.update("update asset_visual_variant_episode set retired_at=now(),binding_status='RETIRED',is_preferred=false,updated_at=now() where id=? and tenant_id=? and project_id=?",row.get("id"),context.tenant(),context.project());
                boolean active="ACTIVE".equals(target.get("binding_status")) || "ACTIVE".equals(row.get("binding_status"));
                boolean preferred=Boolean.TRUE.equals(target.get("is_preferred")) || (Boolean.TRUE.equals(row.get("is_preferred")) && "ACTIVE".equals(row.get("binding_status")));
                jdbc.update("update asset_visual_variant_episode set is_preferred=?,binding_status=?,updated_at=now() where id=? and tenant_id=? and project_id=?",preferred,active?"ACTIVE":target.get("binding_status"),target.get("id"),context.tenant(),context.project());
            }
        }
    }

    private void mergeVoices(Context context,long removed,long kept) {
        var voices=jdbc.queryForList("""
            select * from storyboard_character_voice where tenant_id=? and project_id=? and character_id=? and variant_id=?
              and storyboard_id in (select id from storyboard where tenant_id=? and project_id=? and deleted_at is null) order by id for update
            """,context.tenant(),context.project(),context.characterId(),removed,context.tenant(),context.project());
        for (var row:voices) {
            var target=jdbc.queryForList("select id from storyboard_character_voice where tenant_id=? and project_id=? and character_id=? and storyboard_id=? and variant_id=? for update",Long.class,context.tenant(),context.project(),context.characterId(),row.get("storyboard_id"),kept);
            if (target.isEmpty()) jdbc.update("update storyboard_character_voice set variant_id=?,updated_at=now() where id=? and tenant_id=? and project_id=?",kept,row.get("id"),context.tenant(),context.project());
            else jdbc.update("delete from storyboard_character_voice where id=? and tenant_id=? and project_id=?",row.get("id"),context.tenant(),context.project());
        }
    }

    public static void decorate(JdbcTemplate jdbc,long tenant,long project,long checkId,JsonNode frozen,JsonNode current,JsonNode analysis,boolean sameVersion) {
        if (analysis.path("variantPairs").isEmpty()) return;
        var rows=jdbc.queryForList("select * from character_variant_similarity_resolution where tenant_id=? and project_id=? and (check_id=? or decision='DISTINCT') order by id",tenant,project,checkId);
        Map<String,Map<String,Object>> resolved=new HashMap<>();
        for (var row:rows) resolved.put(row.get("left_variant_id")+":"+row.get("right_variant_id")+":"+row.get("pair_fingerprint"),row);
        for (JsonNode node:analysis.path("variantPairs")) {
            ObjectNode pair=(ObjectNode)node;
            long owner=pair.path("characterId").asLong(),left=Math.min(pair.path("leftVariantId").asLong(),pair.path("rightVariantId").asLong()),right=Math.max(pair.path("leftVariantId").asLong(),pair.path("rightVariantId").asLong());
            var row=resolved.get(left+":"+right+":"+pairFingerprint(frozen,owner,left,right));
            boolean unchanged=sameVersion && !find(current,owner,left).isMissingNode() && !find(current,owner,right).isMissingNode()
                && sameVariant(find(frozen,owner,left),find(current,owner,left)) && sameVariant(find(frozen,owner,right),find(current,owner,right));
            pair.put("canResolve",unchanged && row==null).put("canInspect",unchanged);
            if (row!=null) {
                pair.put("resolution",Objects.toString(row.get("decision")));
                if (row.get("kept_variant_id") instanceof Number id) pair.put("keptVariantId",id.longValue());
            }
        }
    }

    private static boolean sameVariant(JsonNode left,JsonNode right) {
        if (left.isMissingNode() || right.isMissingNode() || left.path("id").asLong()!=right.path("id").asLong()
            || left.path("isPrimary").asBoolean()!=right.path("isPrimary").asBoolean()) return false;
        for (String field:List.of("name","appearance","prompt"))
            if (!left.path(field).asText("").equals(right.path(field).asText(""))) return false;
        return true;
    }

    private static JsonNode find(JsonNode input,long owner,long id) {
        for (JsonNode character:input) if (character.path("id").asLong()==owner)
            for (JsonNode variant:character.path("variants")) if (variant.path("id").asLong()==id) return variant;
        return MissingNode.getInstance();
    }
    private static String pairFingerprint(JsonNode input,long owner,long left,long right) {
        return CharacterPromptSimilarityService.fingerprint(JsonNodeFactory.instance.arrayNode().add(find(input,owner,left)).add(find(input,owner,right)));
    }
    private ObjectNode snapshot(Map<String,Object> row,String primaryPrompt) {
        boolean primary=Boolean.TRUE.equals(row.get("is_primary"));
        return json.createObjectNode().put("id",number(row,"id")).put("name",Objects.toString(row.get("name"),""))
            .put("appearance",Objects.toString(row.get("appearance"),""))
            .put("prompt",primary?primaryPrompt:Objects.toString(row.get("prompt"),""))
            .put("isPrimary",primary);
    }
    private static Map<String,Object> variant(Context context,long id) {
        return context.variants().stream().filter(row->number(row,"id")==id).findFirst()
            .orElseThrow(()->new BusinessException(ErrorCode.VALIDATION_ERROR,"形态已删除或合并，请重新检查。"));
    }
    private ObjectNode outcome(Context context,Resolution command) {
        ObjectNode value=json.createObjectNode().put("characterId",context.characterId()).put("leftVariantId",context.left()).put("rightVariantId",context.right()).put("decision",command.decision());
        if (command.keptVariantId()!=null && "MERGED".equals(command.decision())) value.put("keptVariantId",command.keptVariantId());
        return value;
    }
    private JsonNode read(Object value) {
        try { return json.readTree(Objects.toString(value,"null")); }
        catch (Exception exception) { throw new BusinessException(ErrorCode.VALIDATION_ERROR,"形态或分镜提示词格式异常，请修复后重试。"); }
    }
    private static long number(Map<String,Object> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static void check(boolean valid,String message) { if (!valid) throw new BusinessException(ErrorCode.VALIDATION_ERROR,message); }
}
