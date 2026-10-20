package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Validates identity decisions against the complete, server-owned inventory. */
record AssetNormalizationDecision(List<Group> groups, List<Split> splits) {
    record Group(String type, long retainId, List<Long> members) {}
    record Split(String type, long sourceId, Long targetId, String newName, List<Long> bindings) {}

    static AssetNormalizationDecision validate(JsonNode inventory, JsonNode decision) {
        require(decision.path("groups").isArray() && decision.path("splits").isArray(), "缺少归一分组或拆分数组。");
        Map<String, JsonNode> assets = new HashMap<>();
        inventory.path("assets").forEach(a -> assets.put(key(a.path("assetType").asText(), a.path("id").asLong()), a));
        Map<Long, JsonNode> bindings = new HashMap<>();
        inventory.path("bindings").forEach(b -> bindings.put(b.path("id").asLong(), b));
        List<JsonNode> identityBindings=new ArrayList<>(bindings.values());
        inventory.path("historicalBindings").forEach(identityBindings::add);
        Map<Long, String> episodes = new HashMap<>();
        inventory.path("episodes").forEach(e -> episodes.put(e.path("id").asLong(), e.path("content").asText()));
        Set<String> covered = new HashSet<>();
        List<Group> groups = new ArrayList<>();
        for (JsonNode g : decision.path("groups")) {
            String type = g.path("assetType").asText();
            List<Long> ids = ids(g.path("memberIds"));
            long retain = g.path("retainId").asLong();
            require(ids.contains(retain), "保留资产必须属于本组。");
            Set<Long> evidence = evidence(g.path("evidence"), episodes);
            for (long id : ids) {
                String k = key(type, id);
                require(assets.containsKey(k), "资产不存在、越界或类型不符：" + k);
                require(covered.add(k), "资产重复出现在分组中：" + k);
                if (ids.size() > 1) require(identityBindings.stream().anyMatch(b ->
                    type.equals(b.path("asset_type").asText()) && b.path("asset_id").asLong() == id
                    && evidence.contains(b.path("episode_id").asLong())), "合并资产缺少对应剧集原文证据：" + k);
            }
            groups.add(new Group(type, retain, ids));
        }
        require(covered.equals(assets.keySet()), "归一分组必须完整覆盖所有资产，包含保持独立的资产。");
        Set<Long> assigned = new HashSet<>();
        List<Split> splits = new ArrayList<>();
        for (JsonNode s : decision.path("splits")) {
            String type = s.path("assetType").asText();
            long source = s.path("sourceId").asLong();
            require(groups.stream().anyMatch(g -> g.type().equals(type) && g.retainId() == source && g.members().size() == 1),
                "拆分源资产必须保持为独立分组，不能同时整体合并。");
            Long target = s.hasNonNull("targetId") ? s.path("targetId").asLong() : null;
            String name = s.path("newName").asText("").trim();
            if (target != null) require(target != source && groups.stream().anyMatch(g -> g.type().equals(type) && g.retainId() == target),
                "拆分目标资产必须是同类型保留资产。");
            else require(!name.isBlank() && name.length() <= 100, "新资产名称不能为空且不能超过100字。");
            List<Long> selected = ids(s.path("bindingIds"));
            Set<Long> evidence = evidence(s.path("evidence"), episodes);
            for (long id : selected) {
                JsonNode b = bindings.get(id);
                require(b != null && type.equals(b.path("asset_type").asText()) && source == b.path("asset_id").asLong(),
                    "拆分绑定不属于源资产。");
                require(assigned.add(id), "同一绑定不能重复分配。");
                require(evidence.contains(b.path("episode_id").asLong()), "拆分绑定缺少对应剧集原文证据。");
            }
            splits.add(new Split(type, source, target, name, selected));
        }
        return new AssetNormalizationDecision(List.copyOf(groups), List.copyOf(splits));
    }

    private static List<Long> ids(JsonNode node) {
        require(node.isArray() && !node.isEmpty(), "资产或绑定 ID 数组不能为空。");
        List<Long> ids = new ArrayList<>();
        for (JsonNode value : node) {
            require(value.isIntegralNumber() && value.canConvertToLong() && value.asLong() > 0, "资产或绑定 ID 无效。");
            ids.add(value.asLong());
        }
        return List.copyOf(ids);
    }
    private static Set<Long> evidence(JsonNode node, Map<Long, String> episodes) {
        require(node.isArray(), "缺少原文证据数组。");
        Set<Long> ids = new HashSet<>();
        for (JsonNode e : node) {
            long id = e.path("episodeId").asLong();
            String quote = e.path("quote").asText("");
            require(quote.trim().length() >= 4 && episodes.containsKey(id) && episodes.get(id).contains(quote), "归一证据不是有效剧集逐字原文。");
            ids.add(id);
        }
        return ids;
    }
    static String key(String type, long id) { return type + ":" + id; }
    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorCode.WORKFLOW_AGENT_TOOL_INVALID, message);
    }
}
