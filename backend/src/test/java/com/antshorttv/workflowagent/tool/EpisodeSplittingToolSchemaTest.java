package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EpisodeSplittingToolSchemaTest {
    @Autowired private WorkflowToolRegistry tools;

    @Test
    void splitSaveAcceptsOnlyOrderedTitlesAndSourceMarkers() {
        WorkflowToolDefinition tool = tools.require("save_episode_splitting");
        var input = tool.inputSchema();
        var item = input.path("properties").path("episodes").path("items");

        assertThat(tool.riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(input.path("additionalProperties").asBoolean()).isFalse();
        assertThat(input.path("required")).extracting(node -> node.asText())
            .containsExactly("schemaVersion", "episodes");
        assertThat(input.path("properties").path("episodes").path("minItems").asInt()).isEqualTo(1);
        assertThat(input.path("properties").path("episodes").path("maxItems").asInt()).isBetween(2, 500);
        assertThat(item.path("additionalProperties").asBoolean()).isFalse();
        assertThat(item.path("required")).extracting(node -> node.asText())
            .containsExactly("title", "startMarker", "endMarker");
        assertThat(item.path("properties").fieldNames()).toIterable()
            .containsExactlyInAnyOrder("title", "startMarker", "endMarker")
            .doesNotContain("content", "episodeId", "scriptId", "contentFingerprint");
        assertThat(item.path("properties").path("title").path("maxLength").asInt()).isPositive();
        assertThat(item.path("properties").path("startMarker").path("minLength").asInt()).isEqualTo(1);
        assertThat(item.path("properties").path("endMarker").path("maxLength").asInt()).isPositive();
    }
}
