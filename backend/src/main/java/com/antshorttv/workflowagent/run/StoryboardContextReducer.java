package com.antshorttv.workflowagent.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class StoryboardContextReducer {
    static final int SOFT_CHARACTER_BUDGET = 30_000;

    private static final Logger LOG = LoggerFactory.getLogger(StoryboardContextReducer.class);
    private static final Set<String> GLOBAL_FIELDS = Set.of(
        "ending", "genres", "themes", "logline", "synopsis", "endingHook",
        "coreConflict", "worldSetting", "relationships", "turningPoints",
        "narrativeStyle", "targetAudience"
    );
    private static final Set<String> ASSET_FIELDS = Set.of(
        "assetKey", "name", "normalizedName", "aliases"
    );
    private static final Set<String> VARIANT_FIELDS = Set.of(
        "variantKey", "name", "primary", "episodeBound"
    );

    private final ObjectMapper json;

    public StoryboardContextReducer(ObjectMapper json) {
        this.json = json;
    }

    public Reduction reduce(ObjectNode prepared) {
        int originalCharacters = serializedLength(prepared);
        ObjectNode reduced = json.createObjectNode();
        ObjectNode current = requiredCurrentEpisode(prepared.path("read_current_episode"));
        reduced.set("read_current_episode", current);

        ObjectNode compactAssets = compactAssets(prepared, current);
        ObjectNode compactAnalysis = compactAnalysis(
            prepared.path("read_script_analysis"), current.path("episodeNo").asInt());
        JsonNode compactProject = sanitize(prepared.path("read_project_context"));
        JsonNode compactAdjacent = sanitize(prepared.path("read_adjacent_episodes"));

        boolean optionalDropped = false;
        if (serializedLength(reduced) <= SOFT_CHARACTER_BUDGET) {
            optionalDropped |= !addWithinBudget(reduced, "read_script_analysis", compactAnalysis);
            optionalDropped |= !addWithinBudget(reduced, "read_script_assets", compactAssets);
            optionalDropped |= !addWithinBudget(reduced, "read_project_context", compactProject);
            optionalDropped |= !addWithinBudget(reduced, "read_adjacent_episodes", compactAdjacent);
        } else {
            optionalDropped = true;
        }

        int adjacentCount = objectValueCount(reduced.path("read_adjacent_episodes"));
        int characterCount = arraySize(reduced.path("read_script_assets").path("characters"));
        int sceneCount = arraySize(reduced.path("read_script_assets").path("scenes"));
        int propCount = arraySize(reduced.path("read_script_assets").path("props"));
        return new Reduction(
            reduced,
            originalCharacters,
            serializedLength(reduced),
            adjacentCount,
            characterCount,
            sceneCount,
            propCount,
            optionalDropped
        );
    }

    private ObjectNode requiredCurrentEpisode(JsonNode source) {
        ObjectNode current = json.createObjectNode();
        if (!source.isObject()) {
            return current;
        }
        copy(source, current, "episodeKey");
        copy(source, current, "episodeId");
        copy(source, current, "episodeNo");
        copy(source, current, "title");
        copy(source, current, "summary");
        copy(source, current, "content");
        copy(source, current, "contentFingerprint");
        copy(source, current, "sourceSegments");
        return current;
    }

    private ObjectNode compactAnalysis(JsonNode source, int episodeNo) {
        ObjectNode analysis = json.createObjectNode();
        JsonNode global = source.path("globalUnderstanding");
        if (global.isObject()) {
            ObjectNode compactGlobal = json.createObjectNode();
            GLOBAL_FIELDS.forEach(field -> copy(global, compactGlobal, field));
            if (!compactGlobal.isEmpty()) {
                analysis.set("globalUnderstanding", compactGlobal);
            }
        }
        JsonNode stages = source.path("stages");
        if (stages.isArray()) {
            for (JsonNode stage : stages) {
                JsonNode normalized = parseNormalized(stage.get("normalized_json"));
                JsonNode episode = matchingEpisode(normalized, episodeNo);
                if (episode != null) {
                    JsonNode compactEpisode = sanitize(episode);
                    if (compactEpisode.isObject() && !compactEpisode.isEmpty()) {
                        analysis.set("currentEpisode", compactEpisode);
                        break;
                    }
                }
            }
        }
        return analysis;
    }

    private JsonNode parseNormalized(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        if (!value.isTextual()) {
            return value;
        }
        try {
            return json.readTree(value.asText());
        } catch (JsonProcessingException exception) {
            LOG.warn("Skipping malformed optional storyboard analysis payload");
            return null;
        }
    }

    private JsonNode matchingEpisode(JsonNode normalized, int episodeNo) {
        if (normalized == null) {
            return null;
        }
        JsonNode episodes = normalized.path("episodes");
        if (!episodes.isArray()) {
            return null;
        }
        for (JsonNode episode : episodes) {
            if (episode.path("episodeNo").asInt(Integer.MIN_VALUE) == episodeNo) {
                return episode;
            }
        }
        return null;
    }

    private ObjectNode compactAssets(ObjectNode prepared, ObjectNode current) {
        JsonNode source = prepared.path("read_script_assets");
        if (!source.isObject()) {
            source = prepared.path("read_current_episode").path("assetCatalog");
        }
        String evidence = searchableEpisodeText(current);
        ObjectNode compact = json.createObjectNode();
        compact.set("characters", compactAssetCategory(source.path("characters"), evidence));
        compact.set("scenes", compactAssetCategory(source.path("scenes"), evidence));
        compact.set("props", compactAssetCategory(source.path("props"), evidence));
        return compact;
    }

    private ArrayNode compactAssetCategory(JsonNode source, String evidence) {
        ArrayNode compact = json.createArrayNode();
        if (!source.isArray()) {
            return compact;
        }
        Set<Integer> matchingIndexes = new LinkedHashSet<>();
        for (int index = 0; index < source.size(); index++) {
            if (assetMatches(source.get(index), evidence)) {
                matchingIndexes.add(index);
            }
        }
        boolean useFallback = matchingIndexes.isEmpty();
        for (int index = 0; index < source.size(); index++) {
            if (useFallback || matchingIndexes.contains(index)) {
                compact.add(compactAsset(source.get(index)));
            }
        }
        return compact;
    }

    private boolean assetMatches(JsonNode asset, String evidence) {
        if (matchesText(asset.path("name"), evidence)
            || matchesText(asset.path("normalizedName"), evidence)) {
            return true;
        }
        for (JsonNode alias : asset.path("aliases")) {
            if (matchesText(alias.isTextual() ? alias : alias.path("name"), evidence)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesText(JsonNode value, String evidence) {
        String candidate = value.asText("").strip().toLowerCase(Locale.ROOT);
        return !candidate.isEmpty() && evidence.contains(candidate);
    }

    private ObjectNode compactAsset(JsonNode source) {
        ObjectNode asset = json.createObjectNode();
        ASSET_FIELDS.forEach(field -> copy(source, asset, field));
        ArrayNode variants = asset.putArray("variants");
        for (JsonNode sourceVariant : source.path("variants")) {
            ObjectNode variant = variants.addObject();
            VARIANT_FIELDS.forEach(field -> copy(sourceVariant, variant, field));
        }
        return asset;
    }

    private String searchableEpisodeText(ObjectNode current) {
        StringBuilder evidence = new StringBuilder(current.path("content").asText(""));
        for (JsonNode segment : current.path("sourceSegments")) {
            evidence.append('\n').append(segment.path("text").asText(""));
        }
        return evidence.toString().toLowerCase(Locale.ROOT);
    }

    private JsonNode sanitize(JsonNode source) {
        if (source == null || source.isNull() || source.isMissingNode()) {
            return json.nullNode();
        }
        if (source.isArray()) {
            ArrayNode array = json.createArrayNode();
            source.forEach(value -> array.add(sanitize(value)));
            return array;
        }
        if (!source.isObject()) {
            return source.deepCopy();
        }
        ObjectNode object = json.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (!isFullTextField(field.getKey())) {
                object.set(field.getKey(), sanitize(field.getValue()));
            }
        }
        return object;
    }

    private boolean isFullTextField(String field) {
        String normalized = field.replace("_", "").toLowerCase(Locale.ROOT);
        return normalized.equals("content")
            || normalized.equals("script")
            || normalized.equals("screenplay")
            || normalized.equals("rawresponse");
    }

    private boolean addWithinBudget(ObjectNode target, String field, JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return true;
        }
        target.set(field, value.deepCopy());
        if (serializedLength(target) <= SOFT_CHARACTER_BUDGET) {
            return true;
        }
        target.remove(field);
        return false;
    }

    private void copy(JsonNode source, ObjectNode target, String field) {
        JsonNode value = source.get(field);
        if (value != null) {
            target.set(field, value.deepCopy());
        }
    }

    private int serializedLength(JsonNode value) {
        try {
            return json.writeValueAsString(value).length();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to measure storyboard context", exception);
        }
    }

    private int objectValueCount(JsonNode value) {
        if (!value.isObject()) {
            return 0;
        }
        int count = 0;
        Iterator<JsonNode> values = value.elements();
        while (values.hasNext()) {
            if (!values.next().isNull()) {
                count++;
            }
        }
        return count;
    }

    private int arraySize(JsonNode value) {
        return value.isArray() ? value.size() : 0;
    }

    public record Reduction(
        ObjectNode context,
        int originalCharacters,
        int reducedCharacters,
        int adjacentEpisodeCount,
        int characterCount,
        int sceneCount,
        int propCount,
        boolean optionalSectionsDropped
    ) {}
}
