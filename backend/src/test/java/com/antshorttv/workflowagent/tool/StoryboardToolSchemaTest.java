package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class StoryboardToolSchemaTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void requiresVersionFingerprintOrderedBoardsAndStructuredShots() throws Exception {
        WorkflowToolDefinition tool = new ScreenplayToolConfiguration()
            .saveEpisodeStoryboardsTool(null, json);
        JsonNode schema = tool.inputSchema();
        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(tool.failurePolicy()).isEqualTo(ToolFailurePolicy.RETURN_TO_MODEL);
        assertThat(schema.path("required").toString())
            .contains("schemaVersion", "episodeFingerprint", "storyboards");
        JsonNode board = schema.path("properties").path("storyboards").path("items");
        assertThat(board.path("required").toString())
            .contains("storyboardNo", "sourceFrom", "sourceTo", "usedAssetKeys", "shots");
        assertThat(board.path("properties").has("sourceStartMarker")).isFalse();
        assertThat(board.path("properties").has("sourceEndMarker")).isFalse();
        JsonNode shot = board.path("properties").path("shots").path("items");
        assertThat(shot.path("required").toString())
            .contains("shotNo", "durationSeconds", "positioning", "action", "soundSegmentIds");
        assertThat(shot.path("properties").has("dialogue")).isFalse();
        assertThat(shot.path("properties").has("narration")).isFalse();
        assertThat(shot.path("properties").has("innerOs")).isFalse();
        assertThat(board.path("properties").path("sourceFrom").path("description").asText())
            .contains("每个分镜对象内部");
        assertThat(shot.path("properties").path("soundSegmentIds").path("description").asText())
            .contains("DIALOGUE", "NARRATION", "INNER_OS", "ACTION", "METADATA");
        assertThat(schema.path("properties").path("schemaVersion").path("minimum").asInt())
            .isEqualTo(2);
        assertThat(schema.path("properties").path("schemaVersion").path("maximum").asInt())
            .isEqualTo(2);

        JsonNode invalid = json.readTree("""
            {"schemaVersion":2,"episodeFingerprint":"fp","storyboards":[]}
            """);
        assertThatThrownBy(() -> new WorkflowToolSchemaValidator().validate(schema, invalid))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
