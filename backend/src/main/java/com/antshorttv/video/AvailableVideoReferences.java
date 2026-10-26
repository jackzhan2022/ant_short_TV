package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.script.StoryboardPromptCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Builds a request-local prompt without changing persisted asset bindings. */
@Component
public class AvailableVideoReferences {
    private final StoryboardPromptCompiler compiler;
    private final VideoTaskReferenceResolver resolver;

    public AvailableVideoReferences(StoryboardPromptCompiler compiler, VideoTaskReferenceResolver resolver) {
        this.compiler = compiler;
        this.resolver = resolver;
    }

    public Selection compile(Long tenantId, Long projectId, JsonNode document,
        List<StoryboardPromptCompiler.BindingReference> bindings) {
        var original = compiler.compile(document, bindings);
        Set<String> unavailable = new HashSet<>();
        Set<Long> unavailableVariants = new HashSet<>();
        List<VideoReferenceSubsetSelector.OmittedReference> omitted = new ArrayList<>();
        for (var reference : original.references()) {
            if (!"IMAGE".equals(reference.mediaType())) continue;
            try {
                resolver.resolve(tenantId, projectId, List.of(reference));
            } catch (BusinessException exception) {
                if (exception.getErrorCode() != ErrorCode.VALIDATION_ERROR) throw exception;
                unavailable.add(key(reference.sourceType(), reference.sourceId()));
                if (reference.formalBinding()) unavailableVariants.add(reference.sourceId());
                omitted.add(new VideoReferenceSubsetSelector.OmittedReference(
                    reference.mediaType(), reference.sourceType(), reference.sourceId(), reference.variantId(),
                    reference.assetType(), reference.assetId(), reference.displayName(), reference.bindingRole(),
                    reference.sortOrder(), "IMAGE_UNAVAILABLE"));
            }
        }
        if (unavailable.isEmpty()) return new Selection(original, List.of());
        JsonNode local = document.deepCopy();
        ArrayNode nodes = (ArrayNode) local.path("nodes");
        for (int index = 0; index < nodes.size(); index++) {
            JsonNode node = nodes.get(index);
            if ("mention".equals(node.path("type").asText()) && "IMAGE".equals(node.path("mediaType").asText())
                && unavailable.contains(key(node.path("sourceType").asText(), node.path("sourceId").asLong()))) {
                nodes.set(index, nodes.objectNode().put("type", "text").put("text", node.path("displayName").asText()));
            }
        }
        var remaining = bindings.stream().filter(binding -> !unavailableVariants.contains(binding.variantId())).toList();
        return new Selection(compiler.compile(local, remaining), List.copyOf(omitted));
    }

    private String key(String type, Long id) { return type + ":" + id; }

    public record Selection(StoryboardPromptCompiler.CompiledPrompt compiled,
        List<VideoReferenceSubsetSelector.OmittedReference> omitted) {}
}
