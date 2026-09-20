package com.antshorttv.script;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class StoryboardPromptCompiler {

    public CompiledPrompt compile(JsonNode document) {
        StoryboardPromptDocuments.validate(document);
        StringBuilder text = new StringBuilder();
        Map<ReferenceKey, Reference> references = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        for (JsonNode node : document.path("nodes")) {
            if ("text".equals(node.path("type").asText())) {
                text.append(node.path("text").asText());
                continue;
            }
            ReferenceKey key = new ReferenceKey(
                node.path("mediaType").asText(),
                node.path("sourceType").asText(),
                node.path("sourceId").asLong(),
                nullableLong(node, "variantId")
            );
            Reference reference = references.get(key);
            if (reference == null) {
                int mediaIndex = counters.merge(key.mediaType(), 1, Integer::sum);
                reference = new Reference(
                    key.mediaType(),
                    mediaIndex,
                    label(key.mediaType(), mediaIndex),
                    providerRole(key.mediaType()),
                    key.sourceType(),
                    key.sourceId(),
                    key.variantId(),
                    nullableText(node, "assetType"),
                    nullableLong(node, "assetId"),
                    node.path("displayName").asText(),
                    references.size()
                );
                references.put(key, reference);
            }
            text.append(reference.compiledLabel());
        }
        return new CompiledPrompt(text.toString(), List.copyOf(new ArrayList<>(references.values())));
    }

    private String label(String mediaType, int index) {
        return switch (mediaType) {
            case "IMAGE" -> "图片" + index;
            case "VIDEO" -> "视频" + index;
            case "AUDIO" -> "音频" + index;
            default -> throw new IllegalArgumentException("Unsupported media type: " + mediaType);
        };
    }

    private String providerRole(String mediaType) {
        return switch (mediaType) {
            case "IMAGE" -> "reference_image";
            case "VIDEO" -> "reference_video";
            case "AUDIO" -> "reference_audio";
            default -> throw new IllegalArgumentException("Unsupported media type: " + mediaType);
        };
    }

    private Long nullableLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asLong();
    }

    private String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    public record CompiledPrompt(String text, List<Reference> references) {
    }

    public record Reference(
        String mediaType,
        int mediaIndex,
        String compiledLabel,
        String providerRole,
        String sourceType,
        Long sourceId,
        Long variantId,
        String assetType,
        Long assetId,
        String displayName,
        int sortOrder
    ) {
    }

    private record ReferenceKey(String mediaType, String sourceType, Long sourceId, Long variantId) {
    }
}
