package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VideoAnalysisNormalizer {
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
            JsonNode script = root.get("script");
            if (script == null || !script.isTextual() || script.asText().isBlank()) {
                throw new VideoAnalysisParseException("视频解析响应缺少非空 script 字段。");
            }
            return new VideoAnalysis(
                script.asText(),
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
