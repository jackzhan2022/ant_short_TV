package com.antshorttv.video;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class VideoAnalysisNormalizer {
    private final ObjectMapper objectMapper;
    private final MarkdownScreenplayValidator screenplayValidator = new MarkdownScreenplayValidator();

    public VideoAnalysisNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VideoAnalysis normalize(String rawResponse) {
        return normalize(rawResponse, inferEpisodeNo(rawResponse));
    }

    public VideoAnalysis normalize(String rawResponse, int episodeNo) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new VideoAnalysisParseException("视频解析响应为空。");
        }
        String candidate = rawResponse.trim();
        if (candidate.startsWith("```") || candidate.endsWith("```")) {
            throw new VideoAnalysisParseException("视频解析响应必须只包含完整 JSON 对象，不能使用 Markdown 代码块。");
        }
        try {
            JsonNode root = objectMapper.readTree(candidate);
            if (!root.isObject()) {
                throw new VideoAnalysisParseException("视频解析响应必须是 JSON 对象。");
            }
            JsonNode script = root.get("script");
            if (script == null || !script.isTextual() || script.asText().isBlank()) {
                throw new VideoAnalysisParseException("视频解析响应缺少非空 script 字段。");
            }
            screenplayValidator.validate(script.asText(), episodeNo);
            return new VideoAnalysis(
                script.asText().replace("\r\n", "\n").strip(),
                objectMapper.writeValueAsString(root)
            );
        } catch (VideoAnalysisParseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VideoAnalysisParseException("视频解析响应不是合法 JSON。", exception);
        }
    }

    private int inferEpisodeNo(String rawResponse) {
        if (rawResponse == null) return 1;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("# 第(\\d+)集：")
            .matcher(rawResponse);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

}
