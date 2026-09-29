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
        return compile(document, List.of());
    }

    public CompiledPrompt compile(JsonNode document, List<BindingReference> formalBindings) {
        StoryboardPromptDocuments.validate(document);
        StringBuilder text = new StringBuilder();
        Map<ReferenceKey, Reference> references = new LinkedHashMap<>();
        Map<String, Integer> counters = new LinkedHashMap<>();
        List<String> formalLabels = new ArrayList<>();
        for (BindingReference binding : formalBindings == null ? List.<BindingReference>of() : formalBindings) {
            ReferenceKey key = new ReferenceKey(
                "IMAGE", "ASSET_VISUAL_VARIANT", binding.variantId(), binding.variantId());
            if (references.containsKey(key)) continue;
            int mediaIndex = counters.merge("IMAGE", 1, Integer::sum);
            Reference reference = new Reference(
                "IMAGE", mediaIndex, label("IMAGE", mediaIndex), providerRole("IMAGE"),
                "ASSET_VISUAL_VARIANT", binding.variantId(), binding.variantId(),
                binding.assetType(), binding.assetId(), binding.displayName(), references.size(),
                binding.referenceRole(), true);
            references.put(key, reference);
            formalLabels.add(reference.compiledLabel());
        }
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
            if (reference == null && "FORMAL".equals(node.path("bindingSource").asText())) {
                text.append(node.path("displayName").asText());
                continue;
            }
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
                    references.size(),
                    null,
                    false
                );
                references.put(key, reference);
            }
            text.append(reference.compiledLabel());
        }
        String prefix = formalLabels.isEmpty() ? ""
            : "素材参考：" + String.join("、", formalLabels) + "。\n";
        return new CompiledPrompt(prefix + text, List.copyOf(new ArrayList<>(references.values())));
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
        int sortOrder,
        String bindingRole,
        boolean formalBinding
    ) {
        public Reference(
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
            this(mediaType, mediaIndex, compiledLabel, providerRole, sourceType, sourceId,
                variantId, assetType, assetId, displayName, sortOrder, null, false);
        }
    }

    public record BindingReference(
        String assetType,
        Long assetId,
        Long variantId,
        String displayName,
        String referenceRole,
        int sortOrder
    ) {
    }

    private record ReferenceKey(String mediaType, String sourceType, Long sourceId, Long variantId) {
    }
}
