package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpisodeAssetsToolSchemaTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exposesFiveCategoriesAndForbidsDerivedPropFields() {
        WorkflowToolDefinition tool = new ScreenplayToolConfiguration()
            .saveEpisodeAssetsTool(null, json);
        JsonNode schema = tool.inputSchema();

        assertThat(schema.path("required").findValuesAsText(""))
            .isNotNull();
        assertThat(schema.path("required").toString())
            .contains("characters", "characterLooks", "scenes", "props", "propVariants");
        assertThat(schema.toString()).doesNotContain(
            "derivedFrom", "parentPropKey", "sourcePropKey", "components", "relations");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
    }

    @Test
    void identityRequiresRunLocalKeyNameAliasesAndEvidence() {
        JsonNode schema = new ScreenplayToolConfiguration().saveEpisodeAssetsTool(null, json).inputSchema();
        JsonNode character = schema.path("properties").path("characters").path("items");
        assertThat(character.path("required").toString())
            .contains("localKey", "name", "aliases", "evidence");
        assertThat(character.path("properties").has("assetKey")).isTrue();
        assertThat(character.path("additionalProperties").asBoolean()).isFalse();
    }
}
