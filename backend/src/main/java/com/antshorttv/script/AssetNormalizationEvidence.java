package com.antshorttv.script;

import com.antshorttv.workflowagent.tool.EpisodeAssetsPayloadNormalizer;
import com.antshorttv.workflowagent.tool.EpisodeSourceSegmenter;
import com.antshorttv.workflowagent.tool.WorkflowToolValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evidence references always resolve against the frozen episode, never model-provided text. */
final class AssetNormalizationEvidence {
    private AssetNormalizationEvidence() {}

    static JsonNode episodeForModel(JsonNode episode) {
        ObjectNode output = episode.deepCopy();
        output.remove("content");
        ArrayNode segments = output.putArray("sourceSegments");
        segments(episode.path("content").asText()).forEach((id, text) ->
            segments.addObject().put("id", id).put("text", text));
        return output;
    }

    static ArrayNode episodesForModel(JsonNode episodes) {
        ArrayNode output = JsonNodeFactory.instance.arrayNode();
        episodes.forEach(episode -> output.add(episodeForModel(episode)));
        return output;
    }

    static JsonNode resolve(JsonNode inventory, JsonNode decision) {
        JsonNode output = decision.deepCopy();
        Map<Long, String> episodes = new HashMap<>();
        inventory.path("episodes").forEach(e -> episodes.put(e.path("id").asLong(), e.path("content").asText()));
        Map<Long, Map<String, String>> indexes = new HashMap<>();
        List<String> errors = new ArrayList<>();
        for (String field : List.of("groups", "splits")) {
            JsonNode items = output.path(field);
            for (int i = 0; i < items.size(); i++) {
                JsonNode evidence = items.path(i).path("evidence");
                for (int j = 0; j < evidence.size(); j++) {
                    JsonNode item = evidence.path(j);
                    if (!item.has("quoteRef")) continue;
                    String path = "$." + field + "[" + i + "].evidence[" + j + "]";
                    JsonNode id = item.path("episodeId");
                    if (!id.isIntegralNumber() || !id.canConvertToLong() || !episodes.containsKey(id.asLong())) {
                        errors.add(path + ".quoteRef 的 episodeId 必须属于当前归一快照，不能填写集号或其他剧本的 ID。");
                        continue;
                    }
                    Map<String, String> index = indexes.computeIfAbsent(id.asLong(), key -> segments(episodes.get(key)));
                    EpisodeAssetsPayloadNormalizer.resolveEvidence((ObjectNode) item, "quote", index, path, errors);
                }
            }
        }
        if (!errors.isEmpty()) throw WorkflowToolValidationException.aggregate(errors);
        return output;
    }

    private static Map<String, String> segments(String content) {
        Map<String, String> result = new LinkedHashMap<>();
        new EpisodeSourceSegmenter().segment(content).forEach(segment -> result.put(segment.id(), segment.text()));
        return result;
    }
}
