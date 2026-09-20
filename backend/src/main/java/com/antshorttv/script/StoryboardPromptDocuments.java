package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

final class StoryboardPromptDocuments {
    private static final Set<String> MEDIA_TYPES = Set.of("IMAGE", "VIDEO", "AUDIO");

    private StoryboardPromptDocuments() {
    }

    static void validate(JsonNode document) {
        if (document == null || document.isNull()) {
            return;
        }
        if (!document.isObject() || document.path("version").asInt(-1) != 2) {
            throw invalid("分镜提示词仅支持 version 2。");
        }
        JsonNode nodes = document.path("nodes");
        if (!nodes.isArray() || nodes.isEmpty()) {
            throw invalid("分镜提示词节点不能为空。");
        }
        for (JsonNode node : nodes) {
            String type = node.path("type").asText("");
            if ("text".equals(type)) {
                if (!node.path("text").isTextual()) {
                    throw invalid("分镜提示词文本节点不正确。");
                }
                continue;
            }
            if (!"mention".equals(type)
                || !MEDIA_TYPES.contains(node.path("mediaType").asText(""))
                || node.path("sourceType").asText("").isBlank()
                || node.path("sourceId").asLong(0) <= 0
                || node.path("displayName").asText("").isBlank()) {
                throw invalid("分镜提示词素材引用不正确。");
            }
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
