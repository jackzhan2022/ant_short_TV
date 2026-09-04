package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StoryboardToolDataService {
    public static final String FIXED_MEDIA_CONSTRAINT =
        "视频中不得出现任何字幕、文字叠加、纯画面，不要bgm，不要配乐。";
    public static final String FIXED_CONSISTENCY_CONSTRAINT =
        "保持<人物身份、数量、服装、道具归属、空间方向和声音关系>稳定。";
    private static final Pattern SPOKEN_LINE = Pattern.compile("^[^\\r\\n:：]{1,80}[：:].+$");
    private static final Pattern TOO_MANY_ACTIONS = Pattern.compile(
        ".*(随后|然后|接着|继而|并且|同时又|after that|then).*", Pattern.CASE_INSENSITIVE);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public StoryboardToolDataService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public JsonNode saveEpisodeStoryboards(ToolExecutionContext context, JsonNode payload) {
        requireTrustedScope(context);
        String source = context.runState().require("currentEpisodeContent", String.class);
        String trustedFingerprint = context.runState().require("currentEpisodeFingerprint", String.class);
        String suppliedFingerprint = requiredText(payload, "episodeFingerprint");
        if (!trustedFingerprint.equals(suppliedFingerprint)) {
            throw invalid("分镜来源指纹与本次读取的当前剧集不一致。");
        }
        if (payload.path("schemaVersion").asInt() != 1) {
            throw invalid("不支持的分镜 Schema 版本。");
        }
        JsonNode submitted = payload.path("storyboards");
        if (!submitted.isArray() || submitted.isEmpty()) {
            throw invalid("必须提交当前集完整且非空的分镜集合。");
        }

        Episode episode = lockEpisode(context);
        if (!trustedFingerprint.equals(episode.fingerprint())) {
            throw invalid("当前剧集内容已变化，请重新生成分镜。");
        }
        String visualStyle = projectVisualStyle(context);
        List<ValidatedStoryboard> validated = validateAll(context, source, submitted, visualStyle);

        jdbc.update("""
            update storyboard set deleted_at = now(), updated_at = now()
             where tenant_id = ? and project_id = ? and episode_id = ? and deleted_at is null
            """, context.tenantId(), context.projectId(), context.episodeId());
        ArrayNode ids = json.createArrayNode();
        for (ValidatedStoryboard board : validated) {
            ids.add(insert(context, episode, board));
        }
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("episodeId", context.episodeId());
        result.put("storyboardCount", validated.size());
        result.set("storyboardIds", ids);
        return result;
    }

    public boolean hasCompleteRunSet(Long tenantId, Long projectId, Long episodeId, Long runId) {
        Integer total = jdbc.queryForObject("""
            select count(*) from storyboard
             where tenant_id = ? and project_id = ? and episode_id = ? and deleted_at is null
            """, Integer.class, tenantId, projectId, episodeId);
        Integer owned = jdbc.queryForObject("""
            select count(*) from storyboard
             where tenant_id = ? and project_id = ? and episode_id = ? and deleted_at is null
               and generated_by_run_id = ? and shot_plan_json is not null and prompt_document_json is not null
            """, Integer.class, tenantId, projectId, episodeId, runId);
        return total != null && total > 0 && total.equals(owned);
    }

    private List<ValidatedStoryboard> validateAll(
        ToolExecutionContext context,
        String source,
        JsonNode submitted,
        String visualStyle
    ) {
        List<ValidatedStoryboard> values = new ArrayList<>();
        int expectedOffset = 0;
        Set<String> submittedSounds = new HashSet<>();
        for (int index = 0; index < submitted.size(); index++) {
            JsonNode board = submitted.get(index);
            int storyboardNo = board.path("storyboardNo").asInt();
            if (storyboardNo != index + 1) throw invalid("分镜编号必须从 1 连续递增。");
            String startMarker = requiredText(board, "sourceStartMarker");
            String endMarker = requiredText(board, "sourceEndMarker");
            int start = uniqueIndex(source, startMarker, "开始");
            int endStart = uniqueIndex(source, endMarker, "结束");
            int end = endStart + endMarker.length();
            if (endStart < start || !source.substring(expectedOffset, start).isBlank()) {
                throw invalid("分镜原文边界存在遗漏、重叠或顺序错误。");
            }
            expectedOffset = end;

            JsonNode shots = board.path("shots");
            if (!shots.isArray() || shots.size() < 2) {
                throw invalid("每个正式分镜必须包含至少两个内部镜头。");
            }
            BigDecimal total = BigDecimal.ZERO;
            for (int shotIndex = 0; shotIndex < shots.size(); shotIndex++) {
                JsonNode shot = shots.get(shotIndex);
                if (shot.path("shotNo").asInt() != shotIndex + 1) {
                    throw invalid("每个分镜内的镜头编号必须从 1 连续递增。");
                }
                BigDecimal duration = shot.path("durationSeconds").decimalValue();
                if (duration.compareTo(new BigDecimal("1.5")) < 0
                    || duration.compareTo(new BigDecimal("4")) > 0) {
                    throw invalid("内部镜头时长必须为 1.5 至 4 秒。");
                }
                total = total.add(duration);
                String action = requiredText(shot, "action");
                requiredText(shot, "positioning");
                if (action.length() > 1000 || TOO_MANY_ACTIONS.matcher(action).matches()) {
                    throw invalid("单个镜头只能承载一个主要动作或一个明确情绪变化。");
                }
                collectSound(shot, "dialogue", submittedSounds);
                collectSound(shot, "narration", submittedSounds);
                collectSound(shot, "innerOs", submittedSounds);
            }
            if (total.compareTo(BigDecimal.TEN) < 0 || total.compareTo(new BigDecimal("15")) > 0) {
                throw invalid("每个正式分镜总时长必须为 10 至 15 秒。");
            }
            MaterialSet materials = resolveMaterials(context, board);
            RenderedPrompt prompt = render(visualStyle, board, total, materials);
            values.add(new ValidatedStoryboard(storyboardNo, total, board.deepCopy(), prompt,
                materials.pending() ? "ASSET_PENDING" : "BOUND", materials));
        }
        if (!source.substring(expectedOffset).isBlank()) {
            throw invalid("分镜边界未完整覆盖当前剧集正文。");
        }
        Set<String> sourceSounds = extractSounds(source);
        if (!sourceSounds.equals(submittedSounds)) {
            throw invalid("对白、旁白或内心 OS 必须逐字保留且各出现一次。");
        }
        return values;
    }

    private MaterialSet resolveMaterials(ToolExecutionContext context, JsonNode board) {
        List<Material> materials = new ArrayList<>();
        boolean pending = false;
        JsonNode used = board.path("usedAssetKeys");
        for (AssetKind kind : AssetKind.values()) {
            Set<String> seen = new HashSet<>();
            JsonNode keys = used.path(kind.jsonField);
            if (keys.isArray()) {
                for (JsonNode keyNode : keys) {
                    String key = keyNode.asText();
                    if (!seen.add(key)) throw invalid("同一分镜不得重复引用素材 key。");
                    materials.add(resolveKey(context, kind, key));
                }
            }
            JsonNode unmatched = board.path("unmatchedMaterials").path(kind.jsonField);
            if (unmatched.isArray()) {
                for (JsonNode name : unmatched) {
                    String value = name.asText().trim();
                    if (value.isEmpty()) continue;
                    Material resolved = resolveExactName(context, kind, value);
                    materials.add(resolved);
                    pending |= resolved.assetId == null;
                }
            }
        }
        return new MaterialSet(List.copyOf(materials), pending || materials.stream().anyMatch(m -> m.variantId == null));
    }

    private Material resolveExactName(ToolExecutionContext context, AssetKind kind, String name) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, name, content_json from " + kind.table
                + " where tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null order by id",
            context.tenantId(), context.projectId(), context.scriptId());
        List<Long> matches = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (name.equals(String.valueOf(row.get("name"))) || aliases(row.get("content_json")).contains(name)) {
                matches.add(((Number) row.get("id")).longValue());
            }
        }
        if (matches.size() > 1) throw invalid("素材名称或别名存在歧义：" + name);
        return matches.isEmpty()
            ? Material.unmatched(kind, name)
            : resolveKey(context, kind, kind.prefix + matches.get(0));
    }

    private Set<String> aliases(Object contentJson) {
        if (contentJson == null) return Set.of();
        try {
            Set<String> values = new HashSet<>();
            for (JsonNode alias : json.readTree(String.valueOf(contentJson)).path("aliases")) {
                String value = alias.isTextual() ? alias.asText() : alias.path("name").asText();
                if (!value.isBlank()) values.add(value);
            }
            return values;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资产别名数据损坏。", exception);
        }
    }

    private Material resolveKey(ToolExecutionContext context, AssetKind kind, String key) {
        if (key == null || !key.matches(Pattern.quote(kind.prefix) + "\\d+")) {
            throw invalid("素材 key 类型错误或无效：" + key);
        }
        long assetId = Long.parseLong(key.substring(kind.prefix.length()));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, name, content_json from " + kind.table
                + " where id = ? and tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null",
            assetId, context.tenantId(), context.projectId(), context.scriptId());
        if (rows.size() != 1) throw invalid("素材 key 不属于当前剧本：" + key);
        Map<String, Object> row = rows.get(0);
        Long variantId = firstLong("""
            select variant.id
              from asset_visual_variant_episode binding
              join asset_visual_variant variant on variant.id = binding.variant_id
             where binding.tenant_id = ? and binding.project_id = ? and binding.script_id = ?
               and binding.episode_id = ? and binding.asset_type = ? and binding.asset_id = ?
               and binding.retired_at is null and variant.deleted_at is null
             order by binding.is_preferred desc, binding.id desc limit 1
            """, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(), kind.assetType, assetId);
        if (variantId == null) {
            variantId = firstLong("""
                select id from asset_visual_variant
                 where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
                   and deleted_at is null and is_primary = true
                 order by id desc limit 1
                """, context.tenantId(), context.projectId(), kind.assetType, assetId);
        }
        String variantName = variantId == null ? null : jdbc.queryForObject(
            "select name from asset_visual_variant where id = ?", String.class, variantId);
        return new Material(kind, key, assetId, variantId, String.valueOf(row.get("name")), variantName);
    }

    private RenderedPrompt render(String visualStyle, JsonNode board, BigDecimal total, MaterialSet materialSet) {
        StringBuilder text = new StringBuilder();
        text.append("画风: ").append(visualStyle).append('\n');
        text.append(FIXED_MEDIA_CONSTRAINT).append("\n\n### 素材引用\n\n");
        appendReferences(text, materialSet.materials, AssetKind.CHARACTER, "【人物】\n");
        appendReferences(text, materialSet.materials, AssetKind.SCENE, "【场景】\n");
        appendReferences(text, materialSet.materials, AssetKind.PROP, "【道具】\n");
        text.append("\n### 画面描写\n\n分镜场景设定在： ");
        materialSet.materials.stream().filter(m -> m.kind == AssetKind.SCENE)
            .forEach(m -> text.append(m.name).append(' '));
        text.append("\n\n时间： ").append(optionalText(board, "time"));
        text.append("\n灯光： ").append(optionalText(board, "lighting"));
        text.append("\n分镜具体动作描述：\n");
        for (JsonNode shot : board.path("shots")) {
            text.append("镜头").append(shot.path("shotNo").asInt()).append(' ')
                .append(formatDuration(shot.path("durationSeconds").decimalValue())).append("s\n")
                .append("[站位] ").append(requiredText(shot, "positioning")).append("\n")
                .append("[动作] ").append(requiredText(shot, "action"));
            for (String field : List.of("dialogue", "narration", "innerOs")) {
                String sound = optionalText(shot, field);
                if (!sound.isBlank()) text.append(' ').append(sound);
            }
            text.append("\n\n");
        }
        text.append("### 约束词\n【保持一致】\n").append(FIXED_CONSISTENCY_CONSTRAINT);
        ObjectNode document = json.createObjectNode();
        document.put("version", 1);
        ArrayNode nodes = document.putArray("nodes");
        addMentionNodes(nodes, text.toString(), materialSet.materials);
        return new RenderedPrompt(text.toString(), document);
    }

    private void appendReferences(StringBuilder text, List<Material> materials, AssetKind kind, String heading) {
        List<Material> filtered = materials.stream().filter(m -> m.kind == kind).toList();
        if (filtered.isEmpty()) return;
        text.append(heading);
        for (Material material : filtered) {
            String target = material.variantName == null || material.variantName.isBlank()
                ? material.name : material.variantName;
            text.append('<').append(material.name).append('>');
            switch (kind) {
                case CHARACTER -> text.append("对应").append(material.name).append("，只采用外貌、发型和服装。\n");
                case SCENE -> text.append("参考").append(material.name).append("，只采用空间布局、建筑和光线，不采用图中人物。\n");
                case PROP -> text.append("对应").append(target).append("，只采用结构、材质和颜色。\n");
            }
        }
    }

    private void addMentionNodes(ArrayNode nodes, String text, List<Material> materials) {
        List<Material> bound = materials.stream().filter(m -> m.variantId != null)
            .sorted(Comparator.comparingInt((Material m) -> m.name.length()).reversed()).toList();
        int cursor = 0;
        while (cursor < text.length()) {
            int next = text.length();
            Material match = null;
            for (Material candidate : bound) {
                int at = text.indexOf(candidate.name, cursor);
                if (at >= 0 && at < next) {
                    next = at;
                    match = candidate;
                }
            }
            if (match == null) {
                addText(nodes, text.substring(cursor));
                break;
            }
            addText(nodes, text.substring(cursor, next));
            ObjectNode mention = nodes.addObject();
            mention.put("type", "mention");
            mention.put("assetType", match.kind.assetType);
            mention.put("assetId", match.assetId);
            mention.put("variantId", match.variantId);
            mention.put("displayName", match.name);
            cursor = next + match.name.length();
        }
    }

    private void addText(ArrayNode nodes, String value) {
        if (!value.isEmpty()) nodes.addObject().put("type", "text").put("text", value);
    }

    private long insert(ToolExecutionContext context, Episode episode, ValidatedStoryboard board) {
        JsonNode firstShot = board.plan.path("shots").get(0);
        String characters = joinedNames(board.materials.materials, AssetKind.CHARACTER);
        String scenes = joinedNames(board.materials.materials, AssetKind.SCENE);
        String props = joinedNames(board.materials.materials, AssetKind.PROP);
        String actions = joinField(board.plan.path("shots"), "action");
        String dialogue = joinSounds(board.plan.path("shots"));
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                insert into storyboard
                  (tenant_id, project_id, script_id, episode_id, episode_no, shot_no, storyboard_no,
                   shot_type, visual_description, characters, actions, dialogue, scene, props,
                   duration_seconds, video_prompt, shot_plan_json, prompt_document_json,
                   source_fingerprint, generated_by_run_id, material_binding_status, status,
                   created_by, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, now(), now())
                """, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            statement.setLong(i++, context.tenantId());
            statement.setLong(i++, context.projectId());
            statement.setLong(i++, context.scriptId());
            statement.setLong(i++, context.episodeId());
            statement.setInt(i++, episode.episodeNo);
            statement.setInt(i++, board.storyboardNo);
            statement.setInt(i++, board.storyboardNo);
            statement.setString(i++, actionPrefix(firstShot.path("action").asText()));
            statement.setString(i++, actions);
            statement.setString(i++, blankToNull(characters));
            statement.setString(i++, actions);
            statement.setString(i++, blankToNull(dialogue));
            statement.setString(i++, blankToNull(scenes));
            statement.setString(i++, blankToNull(props));
            statement.setInt(i++, board.total.setScale(0, RoundingMode.HALF_UP).intValueExact());
            statement.setString(i++, board.prompt.plainText);
            statement.setString(i++, write(board.plan));
            statement.setString(i++, write(board.prompt.document));
            statement.setString(i++, episode.fingerprint);
            statement.setLong(i++, context.agentRunId());
            statement.setString(i++, board.materialStatus);
            statement.setLong(i, context.userId());
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("未生成分镜 ID。");
        return key.longValue();
    }

    private Episode lockEpisode(ToolExecutionContext context) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, episode_no, content_fingerprint from script_episode
             where id = ? and tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null for update
            """, context.episodeId(), context.tenantId(), context.projectId(), context.scriptId());
        if (rows.size() != 1) throw invalid("当前正式剧集不存在或已退役。");
        return new Episode(((Number) rows.get(0).get("episode_no")).intValue(),
            String.valueOf(rows.get(0).get("content_fingerprint")));
    }

    private String projectVisualStyle(ToolExecutionContext context) {
        List<String> values = jdbc.queryForList("""
            select visual_style from project where id = ? and tenant_id = ? and deleted_at is null
            """, String.class, context.projectId(), context.tenantId());
        if (values.isEmpty() || values.get(0) == null || values.get(0).isBlank()) {
            throw invalid("项目未设置画风，无法生成正式分镜提示词。");
        }
        return values.get(0).trim();
    }

    private void requireTrustedScope(ToolExecutionContext context) {
        if (context == null || context.tenantId() == null || context.userId() == null
            || context.projectId() == null || context.scriptId() == null || context.episodeId() == null
            || context.agentRunId() == null || context.executionId() == null || context.attemptId() == null
            || context.executionVersion() == null) {
            throw invalid("保存分镜缺少可信执行作用域。");
        }
        Long episodeId = context.runState().require("currentEpisodeId", Long.class);
        Long scriptId = context.runState().require("currentEpisodeScriptId", Long.class);
        if (!episodeId.equals(context.episodeId()) || !scriptId.equals(context.scriptId())) {
            throw invalid("分镜保存作用域与本次读取的剧集不一致。");
        }
    }

    private int uniqueIndex(String source, String marker, String label) {
        int first = source.indexOf(marker);
        if (first < 0 || first != source.lastIndexOf(marker)) {
            throw invalid("分镜原文" + label + "标记缺失或不唯一。");
        }
        return first;
    }

    private Set<String> extractSounds(String source) {
        Set<String> values = new HashSet<>();
        for (String line : source.split("\\R")) {
            String value = line.trim();
            if (SPOKEN_LINE.matcher(value).matches() && !values.add(value)) {
                throw invalid("当前剧集存在完全相同且无法唯一归属的声音原文。");
            }
        }
        return values;
    }

    private void collectSound(JsonNode shot, String field, Set<String> sounds) {
        String value = optionalText(shot, field);
        if (!value.isBlank() && !sounds.add(value)) throw invalid("声音原文不得重复归属多个镜头。");
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value.isBlank()) throw invalid("缺少必填字段：" + field);
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().trim() : "";
    }

    private String joinedNames(List<Material> values, AssetKind kind) {
        return String.join("、", values.stream().filter(value -> value.kind == kind).map(value -> value.name).toList());
    }

    private String joinField(JsonNode values, String field) {
        List<String> texts = new ArrayList<>();
        values.forEach(value -> texts.add(value.path(field).asText()));
        return String.join("\n", texts);
    }

    private String joinSounds(JsonNode shots) {
        List<String> texts = new ArrayList<>();
        shots.forEach(shot -> List.of("dialogue", "narration", "innerOs").forEach(field -> {
            String value = optionalText(shot, field);
            if (!value.isBlank()) texts.add(value);
        }));
        return String.join("\n", texts);
    }

    private String actionPrefix(String action) {
        int separator = action.indexOf('|');
        return separator > 0 ? action.substring(0, separator) : null;
    }

    private String formatDuration(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private Long firstLong(String sql, Object... args) {
        List<Long> values = jdbc.queryForList(sql, Long.class, args);
        return values.isEmpty() ? null : values.get(0);
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化正式分镜。", exception);
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID, message);
    }

    private enum AssetKind {
        CHARACTER("characters", "c_", "CHARACTER", "character_asset"),
        SCENE("scenes", "s_", "SCENE", "scene_asset"),
        PROP("props", "p_", "PROP", "prop_asset");

        private final String jsonField;
        private final String prefix;
        private final String assetType;
        private final String table;

        AssetKind(String jsonField, String prefix, String assetType, String table) {
            this.jsonField = jsonField;
            this.prefix = prefix;
            this.assetType = assetType;
            this.table = table;
        }
    }

    private record Episode(int episodeNo, String fingerprint) {}
    private record Material(AssetKind kind, String key, Long assetId, Long variantId, String name, String variantName) {
        static Material unmatched(AssetKind kind, String name) {
            return new Material(kind, null, null, null, name, null);
        }
    }
    private record MaterialSet(List<Material> materials, boolean pending) {}
    private record RenderedPrompt(String plainText, ObjectNode document) {}
    private record ValidatedStoryboard(
        int storyboardNo,
        BigDecimal total,
        JsonNode plan,
        RenderedPrompt prompt,
        String materialStatus,
        MaterialSet materials
    ) {}
}
