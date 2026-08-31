package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ScreenplayToolCatalogTest {
    @Autowired
    private WorkflowToolRegistry registry;

    @Test
    void registersOnlyCompleteInitialScreenplayTools() {
        assertThat(registry.catalog()).extracting(WorkflowToolMetadata::code).containsExactlyElementsOf(List.of(
            "analyze_script_chunks",
            "list_episode_scripts",
            "read_adjacent_episodes",
            "read_current_episode",
            "read_current_script",
            "read_episode_script",
            "read_project_context",
            "read_project_full_script",
            "read_script_analysis",
            "read_script_assets",
            "read_script_structure",
            "save_episode_assets",
            "save_episode_script",
            "save_episode_splitting",
            "save_episode_summary",
            "save_global_understanding",
            "validate_screenplay_format"
        ));
        assertThat(registry.catalog()).allSatisfy(tool -> {
            assertThat(tool.inputSchema().path("type").asText()).isEqualTo("object");
            assertThat(tool.outputSchema().path("type").asText()).isEqualTo("object");
        });
        assertThat(registry.require("save_episode_script").riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(registry.require("save_global_understanding").riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
        assertThat(registry.require("save_global_understanding").inputSchema().path("required"))
            .extracting(node -> node.asText()).containsExactly("schemaVersion", "content");
        WorkflowToolDefinition currentEpisode = registry.require("read_current_episode");
        assertThat(currentEpisode.riskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(currentEpisode.inputSchema().path("properties")).isEmpty();
        assertThat(currentEpisode.inputSchema().path("additionalProperties").asBoolean()).isFalse();
        assertThat(currentEpisode.outputSchema().path("required"))
            .extracting(node -> node.asText())
            .contains("episodeKey", "episodeNo", "content", "contentFingerprint", "assetCatalog");
        assertThat(currentEpisode.outputSchema().path("properties").path("content").path("maxLength").asInt())
            .isGreaterThan(0);
        WorkflowToolDefinition fullScript = registry.require("read_project_full_script");
        assertThat(fullScript.riskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
        assertThat(fullScript.inputSchema().path("properties")).isEmpty();
        assertThat(fullScript.outputSchema().path("properties").path("episodes")
            .path("items").path("required"))
            .extracting(node -> node.asText()).contains("episodeId", "episodeNo", "content");
        WorkflowToolDefinition structure = registry.require("read_script_structure");
        assertThat(structure.inputSchema().path("properties")).isEmpty();
        assertThat(structure.outputSchema().path("required"))
            .extracting(JsonNode::asText)
            .contains("contentHash", "snapshotKey", "totalChunks", "chunks", "anchors");
        WorkflowToolDefinition chunkAnalysis = registry.require("analyze_script_chunks");
        assertThat(chunkAnalysis.inputSchema().path("properties")).isEmpty();
        assertThat(chunkAnalysis.outputSchema().path("properties").path("recommendedEpisodes").path("type").asText())
            .isEqualTo("array");
    }
}
