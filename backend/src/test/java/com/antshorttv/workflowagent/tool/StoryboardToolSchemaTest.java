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
            .contains("storyboardNo", "sourceStartMarker", "sourceEndMarker", "usedAssetKeys", "shots");
        JsonNode shot = board.path("properties").path("shots").path("items");
        assertThat(shot.path("required").toString())
            .contains("shotNo", "durationSeconds", "positioning", "action");
        assertThat(shot.path("properties").toString()).contains("dialogue", "narration", "innerOs");

        JsonNode invalid = json.readTree("""
            {"schemaVersion":1,"episodeFingerprint":"fp","storyboards":[]}
            """);
        assertThatThrownBy(() -> new WorkflowToolSchemaValidator().validate(schema, invalid))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
