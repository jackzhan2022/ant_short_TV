package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VideoAnalysisNormalizer {
    private static final List<String> REQUIRED_ARRAY_FIELDS = List.of(
        "characters",
        "scenes",
        "props",
        "timeline",
        "dialogue",
        "actions",
        "emotions"
    );

    private final ObjectMapper objectMapper;

    public VideoAnalysisNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VideoAnalysis normalize(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new VideoAnalysisParseException("视频解析响应为空。");
        }
        try {
            JsonNode root = objectMapper.readTree(stripMarkdownFence(rawResponse));
            if (!root.isObject()) {
                throw new VideoAnalysisParseException("视频解析响应必须是 JSON 对象。");
            }
            for (String field : REQUIRED_ARRAY_FIELDS) {
                JsonNode value = root.get(field);
                if (value == null || !value.isArray()) {
                    throw new VideoAnalysisParseException("视频解析响应缺少必需数组字段：" + field);
                }
            }
            return new VideoAnalysis(
                root.get("characters"),
                root.get("scenes"),
                root.get("props"),
                root.get("timeline"),
                root.get("dialogue"),
                root.get("actions"),
                root.get("emotions"),
                objectMapper.writeValueAsString(root)
            );
        } catch (VideoAnalysisParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VideoAnalysisParseException("视频解析响应不是合法 JSON。", exception);
        }
    }

    private String stripMarkdownFence(String value) {
        String text = value.trim();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineEnd = text.indexOf('\n');
        int lastFence = text.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return text.substring(firstLineEnd + 1, lastFence).trim();
        }
        return text;
    }
}
