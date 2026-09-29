package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StoryboardToolSchemaTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void acceptsV3SourceAnchorsWithoutRequiringModelAuthoredSoundOwnership() throws Exception {
        WorkflowToolDefinition tool = new ScreenplayToolConfiguration()
            .saveEpisodeStoryboardsTool(null, json);
        JsonNode schema = tool.inputSchema();
        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(tool.failurePolicy()).isEqualTo(ToolFailurePolicy.TERMINAL);
        assertThat(schema.path("required").toString())
            .contains("schemaVersion", "episodeFingerprint", "storyboards");
        JsonNode board = schema.path("properties").path("storyboards").path("items");
        assertThat(board.path("required").toString())
            .contains("storyboardNo", "sourceTo", "usedAssetKeys", "shots")
            .doesNotContain("sourceFrom");
        assertThat(board.path("properties").has("sourceStartMarker")).isFalse();
        assertThat(board.path("properties").has("sourceEndMarker")).isFalse();
        JsonNode shot = board.path("properties").path("shots").path("items");
        assertThat(shot.path("required").toString())
            .contains("positioning", "action")
            .doesNotContain("shotNo", "durationSeconds", "soundSegmentIds", "sourceAnchor");
        assertThat(shot.path("properties").path("durationSeconds").has("minimum")).isFalse();
        assertThat(shot.path("properties").path("durationSeconds").has("maximum")).isFalse();
        assertThat(shot.path("properties").path("sourceAnchor").path("pattern").asText())
            .isEqualTo("^S\\d{4,}$");
        assertThat(shot.path("properties").has("dialogue")).isFalse();
        assertThat(shot.path("properties").has("narration")).isFalse();
        assertThat(shot.path("properties").has("innerOs")).isFalse();
        assertThat(board.path("properties").has("sourceFrom")).isFalse();
        assertThat(shot.path("properties").has("soundSegmentIds")).isFalse();
        JsonNode characterReference = board.path("properties").path("usedAssetKeys")
            .path("properties").path("characters").path("items");
        assertThat(characterReference.path("required").toString())
            .contains("assetKey", "role");
        assertThat(characterReference.path("properties").fieldNames())
            .toIterable().contains("assetKey", "variantKey", "role", "sourceName");
        assertThat(schema.path("properties").path("schemaVersion").path("minimum").asInt())
            .isEqualTo(3);
        assertThat(schema.path("properties").path("schemaVersion").path("maximum").asInt())
            .isEqualTo(3);

        JsonNode invalid = json.readTree("""
            {"schemaVersion":2,"episodeFingerprint":"fp","storyboards":[{
              "storyboardNo":1,"sourceTo":"S0001",
              "usedAssetKeys":{"characters":[],"scenes":[],"props":[]},
              "shots":[
                {"shotNo":1,"durationSeconds":2,"positioning":"wide","action":"move"},
                {"shotNo":2,"durationSeconds":2,"positioning":"close","action":"react"}
              ]
            }]}
            """);
        assertThatThrownBy(() -> new WorkflowToolSchemaValidator().validate(schema, invalid))
            .isInstanceOf(IllegalArgumentException.class);

        JsonNode validV3 = json.readTree("""
            {
              "schemaVersion":3,
              "episodeFingerprint":"fp",
              "storyboards":[{
                "storyboardNo":9,
                "sourceTo":"S0002",
                "usedAssetKeys":{
                  "characters":[{"assetKey":"c_12","variantKey":"v_33","role":"VISIBLE","sourceName":"Serena"}],
                  "scenes":[{"assetKey":"s_21","role":"MAIN"}],
                  "props":[]
                },
                "shots":[
                  {"shotNo":7,"durationSeconds":2,"positioning":"wide","action":"move","sourceAnchor":"S0001"},
                  {"shotNo":8,"durationSeconds":2,"positioning":"close","action":"react"}
                ]
              }]
            }
            """);
        new WorkflowToolSchemaValidator().validate(schema, validV3);

        ((com.fasterxml.jackson.databind.node.ObjectNode) validV3.path("storyboards").path(0)
            .path("shots").path(0)).remove(java.util.List.of("shotNo", "durationSeconds"));
        new WorkflowToolSchemaValidator().validate(schema, validV3);

        ((com.fasterxml.jackson.databind.node.ObjectNode) validV3.path("storyboards").path(0).path("shots").path(0))
            .put("unknownField", true);
        assertThatThrownBy(() -> new WorkflowToolSchemaValidator().validate(schema, validV3))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
