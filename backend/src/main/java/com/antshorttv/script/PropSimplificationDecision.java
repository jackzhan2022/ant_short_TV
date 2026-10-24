package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

final class PropSimplificationDecision {
    private PropSimplificationDecision() {}
    static void validate(JsonNode input, JsonNode decision) {
        require(decision.path("groups").isArray(), "缺少道具精简分组。");
        Map<Long, JsonNode> assets = indexed(input.path("props"));
        Map<Long, JsonNode> variants = indexed(input.path("variants"));
        Map<Long, JsonNode> scenes = indexed(input.path("scenes"));
        Map<Long, JsonNode> episodes = indexed(input.path("episodes"));
        Set<Long> covered = new HashSet<>();
        for (JsonNode group : decision.path("groups")) {
            String mode = group.path("mode").asText();
            require(Set.of("INDEPENDENT", "SHARED", "VARIANTS", "SCENE").contains(mode), "未知道具处理方式。");
            require(!group.path("name").asText().isBlank() && group.path("name").asText().length() <= 100,
                "制作项名称须为 1 至 100 字。");
            require(!group.path("reason").asText().isBlank(), "每组需要说明处理原因。");
            List<Long> members = ids(group.path("memberIds"));
            require(!members.isEmpty(), "分组不能没有原道具。");
            for (long id : members) require(assets.containsKey(id) && covered.add(id), "道具不存在、跨项目或重复分组。");
            if (mode.equals("INDEPENDENT")) continue;
            Set<Long> evidenceEpisodes = new HashSet<>();
            require(group.path("evidence").isArray() && !group.path("evidence").isEmpty(), "精简建议需要剧情原文证据。");
            for (JsonNode evidence : group.path("evidence")) {
                long episode = evidence.path("episodeId").asLong();
                String quote = evidence.path("quote").asText();
                require(quote.trim().length() >= 4 && episodes.containsKey(episode)
                    && episodes.get(episode).path("content").asText().contains(quote), "精简证据必须为当前剧集逐字原文。");
                evidenceEpisodes.add(episode);
            }
            for (long member : members) {
                List<Long> bound = new ArrayList<>();
                input.path("bindings").forEach(b -> { if (b.path("asset_id").asLong() == member) bound.add(b.path("episode_id").asLong()); });
                require(bound.isEmpty() || bound.stream().anyMatch(evidenceEpisodes::contains), "证据必须覆盖组内道具的使用剧集。");
            }
            if (mode.equals("SCENE")) {
                List<Long> destinations = ids(group.path("sceneIds"));
                require(!destinations.isEmpty() && destinations.stream().allMatch(scenes::containsKey), "场景内物品必须关联当前项目有效场景。");
                continue;
            }
            require(members.contains(group.path("targetId").asLong()), "保留制作项必须是本组现有道具。");
            if (mode.equals("SHARED")) {
                for (long member : members) require(variants.values().stream()
                    .filter(v -> v.path("asset_id").asLong() == member).count() <= 1,
                    "已有多个形态的道具必须保留不同版本，不能直接共用普通外观。");
                continue;
            }
            require(group.path("variants").isArray() && !group.path("variants").isEmpty(), "需要明确保留的视觉版本。");
            Set<Long> mapped = new HashSet<>();
            Set<Long> defaults = new HashSet<>();
            Set<String> names = new HashSet<>();
            for (JsonNode version : group.path("variants")) {
                String name = version.path("name").asText();
                require(!name.isBlank() && name.length() <= 100 && names.add(name), "版本名称不能为空、重复或超过 100 字。");
                require(!version.path("prompt").asText().isBlank() && version.path("prompt").asText().length() <= 8000,
                    "必要版本须提供独立绘图提示词。");
                for (long variant : ids(version.path("sourceVariantIds"))) require(variants.containsKey(variant)
                    && members.contains(variants.get(variant).path("asset_id").asLong()) && mapped.add(variant),
                    "版本映射越界或重复。");
                for (long asset : ids(version.path("sourceAssetIds"))) require(members.contains(asset) && defaults.add(asset),
                    "同一道具不能默认指向多个不同版本。");
            }
            for (JsonNode variant : variants.values()) if (members.contains(variant.path("asset_id").asLong()))
                require(mapped.contains(variant.path("id").asLong()), "必须为所有已有形态保留制作映射。");
        }
        require(covered.equals(assets.keySet()), "精简方案必须覆盖全部原道具，未精简项保持独立。");
    }
    static Map<Long, JsonNode> indexed(JsonNode rows) {
        Map<Long, JsonNode> result = new LinkedHashMap<>();
        rows.forEach(row -> result.put(row.path("id").asLong(), row));
        return result;
    }
    static List<Long> ids(JsonNode values) {
        require(values.isArray() || values.isMissingNode(), "ID 列表格式无效。");
        List<Long> result = new ArrayList<>();
        for (JsonNode value : values) { require(value.isIntegralNumber() && value.asLong() > 0, "ID 必须为正整数。"); result.add(value.asLong()); }
        require(new HashSet<>(result).size() == result.size(), "ID 列表不能重复。");
        return result;
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
