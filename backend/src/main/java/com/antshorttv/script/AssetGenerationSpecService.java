package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Reads the specification attached to the saved prompt, never today's project selection. */
@Service
public class AssetGenerationSpecService {
    private static final Map<String,String> TABLES = Map.of("CHARACTER", "character_asset", "SCENE", "scene_asset", "PROP", "prop_asset");
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AssetGenerationSpecService(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }

    public record Spec(String negativePrompt, String aspectRatio, String promptMode) {
        public boolean complete() { return "FULL".equals(promptMode); }
        public String ratio(String requested) {
            return requested == null || requested.isBlank() || "ASSET".equals(requested)
                ? aspectRatio == null || aspectRatio.isBlank() ? "16:9" : aspectRatio : requested;
        }
    }

    public Spec read(Long tenant, Long project, String type, Long id) {
        if ("VISUAL_VARIANT".equals(type)) {
            var rows=jdbc.queryForList("select asset_type,asset_id,is_primary,content_json from asset_visual_variant where id=? and tenant_id=? and project_id=? and deleted_at is null", id,tenant,project);
            if (rows.size()!=1) throw new BusinessException(ErrorCode.NOT_FOUND,"资产形态不存在。");
            var row=rows.get(0);
            boolean primary=Boolean.TRUE.equals(row.get("is_primary")) || row.get("is_primary") instanceof Number n && n.intValue()!=0;
            if (!primary) return fromContent(row.get("content_json"));
            return read(tenant,project,String.valueOf(row.get("asset_type")),((Number)row.get("asset_id")).longValue());
        }
        String table=TABLES.get(type);
        if (table==null) return new Spec(null,null,null);
        var rows=jdbc.queryForList("select content_json from "+table+" where id=? and tenant_id=? and project_id=? and deleted_at is null",id,tenant,project);
        if (rows.size()!=1) throw new BusinessException(ErrorCode.NOT_FOUND,"资产不存在。");
        return fromContent(rows.get(0).get("content_json"));
    }

    public Spec fromContent(Object content) {
        try {
            var parsed=json.readTree(content==null?"{}":String.valueOf(content));
            var node=parsed==null ? json.createObjectNode() : parsed.path("generationSpec");
            return new Spec(node.path("negativePrompt").asText(null),node.path("aspectRatio").asText(null),node.path("promptMode").asText(null));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("资产生成规格损坏。",e); }
    }
}
