package com.antshorttv.workflowagent.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class StoryboardContextReducerTest {
    private final ObjectMapper json = new ObjectMapper();
    private final StoryboardContextReducer reducer = new StoryboardContextReducer(json);

    @Test
    void removesFullScriptAndRawResponsesWhileKeepingSelectedEpisodeFacts() throws Exception {
        ObjectNode prepared = prepared("Alice enters the observatory.");
        JsonNode current = prepared.path("read_current_episode");

        StoryboardContextReducer.Reduction result = reducer.reduce(prepared);

        assertThat(result.context().path("read_current_episode").path("content"))
            .isEqualTo(current.path("content"));
        assertThat(result.context().path("read_current_episode").path("sourceSegments"))
            .isEqualTo(current.path("sourceSegments"));
        assertThat(result.context().toString())
            .doesNotContain("FULL_PROJECT_SCRIPT", "raw_response", "RAW_MODEL_RESPONSE");
        assertThat(result.context().path("read_script_analysis").path("currentEpisode")
            .path("episodeNo").asInt()).isEqualTo(2);
        assertThat(result.context().path("read_script_analysis").path("currentEpisode")
            .path("summary").asText()).isEqualTo("current summary");
        assertThat(result.context().path("read_script_analysis").path("currentEpisode")
            .has("content")).isFalse();
        assertThat(result.originalCharacters()).isGreaterThan(result.reducedCharacters());
    }

    @Test
    void retainsMatchingAssetsWithoutVerboseVariantContent() throws Exception {
        StoryboardContextReducer.Reduction result = reducer.reduce(
            prepared("Alice enters the observatory."));

        JsonNode characters = result.context().path("read_script_assets").path("characters");
        assertThat(characters).hasSize(1);
        assertThat(characters.get(0).path("assetKey").asText()).isEqualTo("c_1");
        assertThat(characters.get(0).path("variants").get(0).path("variantKey").asText())
            .isEqualTo("v_11");
        assertThat(characters.toString()).doesNotContain("VERBOSE_APPEARANCE_PROMPT");
        assertThat(result.characterCount()).isEqualTo(1);
    }

    @Test
    void fallsBackToIdentityOnlyAssetsWhenNoNameMatches() throws Exception {
        StoryboardContextReducer.Reduction result = reducer.reduce(
            prepared("An unidentified figure enters an unknown place."));

        JsonNode characters = result.context().path("read_script_assets").path("characters");
        assertThat(characters).hasSize(2);
        assertThat(characters.toString()).contains("c_1", "c_2");
        assertThat(characters.toString()).doesNotContain("VERBOSE_APPEARANCE_PROMPT");
    }

    @Test
    void malformedOptionalAnalysisDegradesWithoutChangingRequiredEpisode() throws Exception {
        ObjectNode prepared = prepared("Alice enters the observatory.");
        prepared.withObject("read_script_analysis").withArray("stages").get(1)
            .deepCopy();
        ((ObjectNode) prepared.path("read_script_analysis").path("stages").get(1))
            .put("normalized_json", "{not-json");
        JsonNode current = prepared.path("read_current_episode").deepCopy();

        StoryboardContextReducer.Reduction result = reducer.reduce(prepared);

        assertThat(result.context().path("read_current_episode").path("content"))
            .isEqualTo(current.path("content"));
        assertThat(result.context().path("read_current_episode").path("sourceSegments"))
            .isEqualTo(current.path("sourceSegments"));
        assertThat(result.context().path("read_script_analysis").has("currentEpisode")).isFalse();
    }

    @Test
    void softBudgetDropsOptionalDataBeforeRequiredEpisode() throws Exception {
        ObjectNode prepared = prepared("Alice enters the observatory.");
        prepared.withObject("read_project_context").put("notes", "P".repeat(40_000));

        StoryboardContextReducer.Reduction result = reducer.reduce(prepared);

        assertThat(result.reducedCharacters())
            .isLessThanOrEqualTo(StoryboardContextReducer.SOFT_CHARACTER_BUDGET);
        assertThat(result.context().path("read_current_episode").path("content").asText())
            .isEqualTo("Alice enters the observatory.");
        assertThat(result.optionalSectionsDropped()).isTrue();
    }

    @Test
    void requiredEpisodeMayExceedSoftBudgetWithoutTruncation() throws Exception {
        String requiredContent = "CURRENT".repeat(5_000);
        ObjectNode prepared = prepared(requiredContent);

        StoryboardContextReducer.Reduction result = reducer.reduce(prepared);

        assertThat(result.context().path("read_current_episode").path("content").asText())
            .isEqualTo(requiredContent);
        assertThat(result.reducedCharacters())
            .isGreaterThan(StoryboardContextReducer.SOFT_CHARACTER_BUDGET);
        assertThat(result.context().has("read_adjacent_episodes")).isFalse();
        assertThat(result.optionalSectionsDropped()).isTrue();
    }

    private ObjectNode prepared(String currentContent) throws Exception {
        ObjectNode prepared = json.createObjectNode();
        ObjectNode current = prepared.putObject("read_current_episode");
        current.put("episodeKey", "episode-2");
        current.put("episodeNo", 2);
        current.put("title", "Episode 2");
        current.put("content", currentContent);
        current.put("contentFingerprint", "fingerprint-2");
        ArrayNode segments = current.putArray("sourceSegments");
        segments.addObject().put("id", "S0001").put("type", "ACTION")
            .put("text", currentContent).put("requiredCoverage", true);
        current.set("assetCatalog", assets().deepCopy());

        prepared.set("read_adjacent_episodes", json.readTree("""
            {"previous":{"episodeNo":1,"title":"Episode 1","summary":"before",
             "openingExcerpt":"OPEN","endingExcerpt":"PREVIOUS_END","contentTruncated":true},
             "next":{"episodeNo":3,"title":"Episode 3","summary":"after",
             "openingExcerpt":"NEXT_START","endingExcerpt":"END","contentTruncated":true}}
            """));

        ObjectNode analysis = prepared.putObject("read_script_analysis");
        analysis.set("globalUnderstanding", json.readTree("""
            {"logline":"A mystery","themes":["truth"],"worldSetting":"Observatory",
             "coreConflict":"Alice versus time","narrativeStyle":"fast",
             "irrelevantVerboseField":"G"}
            """));
        analysis.putObject("task").put("status", "SUCCEEDED");
        ArrayNode stages = analysis.putArray("stages");
        ObjectNode split = stages.addObject();
        split.put("stage_code", "EPISODE_SPLITTING");
        split.put("normalized_json", json.writeValueAsString(json.readTree("""
            {"episodes":[{"episodeNo":1,"content":"FULL_PROJECT_SCRIPT"}]}
            """)));
        split.put("raw_response", "RAW_MODEL_RESPONSE");
        ObjectNode summary = stages.addObject();
        summary.put("stage_code", "EPISODE_SUMMARY");
        summary.put("normalized_json", json.writeValueAsString(json.readTree("""
            {"episodes":[
              {"episodeNo":1,"summary":"previous summary","content":"FULL_PROJECT_SCRIPT"},
              {"episodeNo":2,"summary":"current summary","highlights":["turn"],
               "content":"FULL_PROJECT_SCRIPT"}]}
            """)));

        prepared.set("read_project_context", json.readTree("""
            {"projectId":26,"name":"Demo","visualStyle":"cinematic"}
            """));
        prepared.set("read_script_assets", assets());
        return prepared;
    }

    private ObjectNode assets() throws Exception {
        ObjectNode assets = json.createObjectNode();
        ArrayNode characters = assets.putArray("characters");
        characters.add(asset("c_1", "Alice", "v_11"));
        characters.add(asset("c_2", "Bob", "v_12"));
        ArrayNode scenes = assets.putArray("scenes");
        scenes.add(asset("s_1", "Observatory", "v_21"));
        assets.putArray("props").add(asset("p_1", "Pocket Watch", "v_31"));
        return assets;
    }

    private ObjectNode asset(String assetKey, String name, String variantKey) throws Exception {
        ObjectNode asset = json.createObjectNode();
        asset.put("assetKey", assetKey);
        asset.put("name", name);
        asset.put("normalizedName", name.toLowerCase());
        asset.putArray("aliases");
        asset.putArray("variants").addObject()
            .put("variantKey", variantKey)
            .put("name", "Primary")
            .put("primary", true)
            .put("episodeBound", true)
            .set("content", json.readTree("""
                {"prompt":"VERBOSE_APPEARANCE_PROMPT"}
                """));
        return asset;
    }
}
