package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class EpisodeSummaryToolSchemaTest {
    @Autowired private WorkflowToolRegistry tools;

    @Test
    void summarySaveHasBoundedEditableContentAndNoBusinessIdentityFields() {
        var schema = tools.require("save_episode_summary").inputSchema();
        var fields = schema.path("properties");

        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("required")).extracting(node -> node.asText())
            .containsExactly("schemaVersion", "summary", "highlights", "endingHook");
        assertThat(fields.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("schemaVersion", "summary", "highlights", "endingHook")
            .doesNotContain("episodeId", "scriptId", "contentFingerprint", "agentRunId");
        assertThat(fields.path("summary").path("minLength").asInt()).isEqualTo(1);
        assertThat(fields.path("summary").path("maxLength").asInt()).isPositive();
        assertThat(fields.path("highlights").path("minItems").asInt()).isEqualTo(2);
        assertThat(fields.path("highlights").path("maxItems").asInt()).isEqualTo(5);
        assertThat(fields.path("highlights").path("items").path("minLength").asInt()).isEqualTo(1);
        assertThat(fields.path("endingHook").path("type")).extracting(node -> node.asText())
            .containsExactly("string", "null");
    }
}
