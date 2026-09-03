package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void normalizesOnlyMechanicalOmissionsAndNeverInventsEvidence() throws Exception {
        JsonNode input = json.readTree("""
            {"schemaVersion":1,
             "characters":[{"localKey":"c1","name":"小满","evidence":"小满"}],
             "characterLooks":[{"localKey":"l1","characterLocalKey":"c1","name":"校服","evidence":"校服"}],
             "scenes":[{"localKey":"s1","name":"仓库","evidence":"仓库"}]}
            """);

        JsonNode normalized = EpisodeAssetsPayloadNormalizer.normalize(input);

        assertThat(normalized.path("characters").path(0).path("aliases").isArray()).isTrue();
        assertThat(normalized.path("scenes").path(0).path("aliases").isArray()).isTrue();
        assertThat(normalized.path("characterLooks").path(0).path("preferred").asBoolean()).isFalse();
        assertThat(normalized.path("props").isArray()).isTrue();
        assertThat(normalized.path("propVariants").isArray()).isTrue();
        assertThat(input.path("props").isMissingNode()).isTrue();

        JsonNode missingEvidence = EpisodeAssetsPayloadNormalizer.normalize(json.readTree("""
            {"schemaVersion":1,"characters":[{"localKey":"c1","name":"小满"}]}
            """));
        WorkflowToolDefinition tool = new ScreenplayToolConfiguration().saveEpisodeAssetsTool(null, json);
        assertThatThrownBy(() -> new WorkflowToolSchemaValidator()
            .validate(tool.inputSchema(), missingEvidence))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("evidence");
    }
}
