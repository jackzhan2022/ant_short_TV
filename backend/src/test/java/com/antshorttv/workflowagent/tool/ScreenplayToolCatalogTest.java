package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

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
            "list_episode_scripts",
            "read_adjacent_episodes",
            "read_episode_script",
            "read_project_context",
            "read_script_analysis",
            "read_script_assets",
            "save_episode_script",
            "validate_screenplay_format"
        ));
        assertThat(registry.catalog()).allSatisfy(tool -> {
            assertThat(tool.inputSchema().path("type").asText()).isEqualTo("object");
            assertThat(tool.outputSchema().path("type").asText()).isEqualTo("object");
        });
        assertThat(registry.require("save_episode_script").riskLevel()).isEqualTo(ToolRiskLevel.WRITE);
    }
}
