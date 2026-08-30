package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowToolRegistryTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void exposesCompleteMetadataAndExecutableDefinitionByStableCode() {
        WorkflowToolDefinition definition = definition("read_episode_script");
        WorkflowToolRegistry registry = new WorkflowToolRegistry(List.of(definition));

        assertThat(registry.contains("read_episode_script")).isTrue();
        assertThat(registry.require("read_episode_script")).isSameAs(definition);
        assertThat(registry.catalog()).singleElement().satisfies(item -> {
            assertThat(item.code()).isEqualTo("read_episode_script");
            assertThat(item.name()).isNotBlank();
            assertThat(item.description()).isNotBlank();
            assertThat(item.inputSchema().path("type").asText()).isEqualTo("object");
            assertThat(item.outputSchema().path("type").asText()).isEqualTo("object");
            assertThat(item.riskLevel()).isEqualTo(ToolRiskLevel.READ_ONLY);
            assertThat(item.failurePolicy()).isEqualTo(ToolFailurePolicy.TERMINAL);
        });
    }

    @Test
    void rejectsDuplicateCodesAndUnknownLookup() {
        assertThatThrownBy(() -> new WorkflowToolRegistry(List.of(
            definition("duplicate"), definition("duplicate"))))
            .isInstanceOf(IllegalStateException.class);
        WorkflowToolRegistry registry = new WorkflowToolRegistry(List.of());
        assertThatThrownBy(() -> registry.require("missing"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaValidatorRejectsMissingRequiredWrongTypesAndUnexpectedScopeFields() {
        ObjectNode schema = json.createObjectNode();
        schema.put("type", "object");
        schema.putArray("required").add("content");
        schema.putObject("properties").putObject("content").put("type", "string");
        schema.put("additionalProperties", false);
        WorkflowToolSchemaValidator validator = new WorkflowToolSchemaValidator();

        assertThatThrownBy(() -> validator.validate(schema, json.createObjectNode()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(schema,
            json.createObjectNode().put("content", 1)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(schema,
            json.createObjectNode().put("content", "ok").put("projectId", 99)))
            .isInstanceOf(IllegalArgumentException.class);
        validator.validate(schema, json.createObjectNode().put("content", "ok"));
    }

    private WorkflowToolDefinition definition(String code) {
        ObjectNode schema = json.createObjectNode().put("type", "object");
        return new WorkflowToolDefinition(code, "Read episode", "Reads the current episode",
            schema, schema.deepCopy(), ToolRiskLevel.READ_ONLY, ToolFailurePolicy.TERMINAL,
            new WorkflowToolExecutor() {
                @Override
                public com.fasterxml.jackson.databind.JsonNode execute(
                    ToolExecutionContext context,
                    com.fasterxml.jackson.databind.JsonNode arguments
                ) {
                    return json.createObjectNode();
                }
            });
    }
}
