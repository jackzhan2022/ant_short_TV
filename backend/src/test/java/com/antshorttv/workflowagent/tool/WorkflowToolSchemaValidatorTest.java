package com.antshorttv.workflowagent.tool;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WorkflowToolSchemaValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final WorkflowToolSchemaValidator validator = new WorkflowToolSchemaValidator();

    @Test
    void enforcesNumericStringAndArrayBounds() throws Exception {
        var schema = json.readTree("""
            {"type":"object","required":["version","name","items"],"additionalProperties":false,
             "properties":{
               "version":{"type":"integer","minimum":1,"maximum":1},
               "name":{"type":"string","maxLength":3},
               "items":{"type":"array","maxItems":1,"items":{"type":"string"}}
             }}
            """);

        assertThatThrownBy(() -> validator.validate(schema,
            json.readTree("{\"version\":2,\"name\":\"ok\",\"items\":[]}")))
            .hasMessageContaining("maximum");
        assertThatThrownBy(() -> validator.validate(schema,
            json.readTree("{\"version\":1,\"name\":\"long\",\"items\":[]}")))
            .hasMessageContaining("maxLength");
        assertThatThrownBy(() -> validator.validate(schema,
            json.readTree("{\"version\":1,\"name\":\"ok\",\"items\":[\"a\",\"b\"]}")))
            .hasMessageContaining("maxItems");
    }

    @Test
    void requiredNullablePropertyMustBePresentButMayBeNull() throws Exception {
        var schema = json.readTree("""
            {"type":"object","required":["stageStatus"],"additionalProperties":false,
             "properties":{"stageStatus":{"type":["string","null"]}}}
            """);

        assertDoesNotThrow(() -> validator.validate(schema,
            json.readTree("{\"stageStatus\":null}")));
        assertThatThrownBy(() -> validator.validate(schema, json.readTree("{}")))
            .hasMessageContaining("stageStatus is required");
    }
}
