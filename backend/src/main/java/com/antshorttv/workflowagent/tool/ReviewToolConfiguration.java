package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReviewToolConfiguration {
    @Bean WorkflowToolDefinition readReviewContextTool(ReviewToolDataService data, ObjectMapper json) {
        return definition("read_review_context", "读取审核上下文", "读取冻结审核配置、哈希、范围与覆盖元数据。",
            empty(json), contextOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, args) -> data.readContext(context)));
    }

    @Bean WorkflowToolDefinition readReviewContentTool(ReviewToolDataService data, ObjectMapper json) {
        ObjectNode input = object(json);
        ObjectNode fields = (ObjectNode) input.path("properties");
        fields.putObject("offset").put("type", "integer").put("minimum", 0);
        fields.putObject("limit").put("type", "integer").put("minimum", 1).put("maximum", 50000);
        return definition("read_review_content", "读取审核正文", "分页读取冻结范围或当前单元的可信正文与位置锚点。",
            input, pageOutput(json, "segments"), ToolRiskLevel.READ_ONLY,
            executor((context, args) -> data.readContent(context, args)));
    }

    private WorkflowToolDefinition definition(String code, String name, String description,
        JsonNode input, JsonNode output, ToolRiskLevel risk, WorkflowToolExecutor executor) {
        return new WorkflowToolDefinition(code, name, description, input, output, risk,
            ToolFailurePolicy.RETURN_TO_MODEL, executor);
    }

    private WorkflowToolExecutor executor(ToolCall call) {
        return new WorkflowToolExecutor() {
            @Override public JsonNode execute(ToolExecutionContext context, JsonNode arguments) {
                return call.execute(context, arguments);
            }
        };
    }

    @FunctionalInterface
    private interface ToolCall {
        JsonNode execute(ToolExecutionContext context, JsonNode arguments);
    }

    private ObjectNode object(ObjectMapper json) {
        return json.createObjectNode().put("type", "object").put("additionalProperties", false)
            .set("properties", json.createObjectNode());
    }

    private ObjectNode empty(ObjectMapper json) { return object(json); }

    private ObjectNode contextOutput(ObjectMapper json) {
        ObjectNode schema = object(json);
        schema.putArray("required").add("mode").add("phase").add("round").add("dimensions")
            .add("scope").add("versionHash").add("scopeHash").add("dimensionsHash").add("coverage");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        for (String field : new String[]{"mode", "phase", "versionHash", "scopeHash", "dimensionsHash"})
            fields.putObject(field).put("type", "string");
        fields.putObject("snapshotKey").put("type", "string");
        fields.putObject("round").put("type", "integer").put("minimum", 1);
        fields.putObject("lineCount").put("type", "integer").put("minimum", 0);
        fields.putObject("segmentCount").put("type", "integer").put("minimum", 0);
        fields.putObject("snapshotId").put("type", "integer").put("minimum", 1);
        fields.putObject("unitId").put("type", "integer").put("minimum", 1);
        fields.putObject("dimensions").put("type", "array").put("maxItems", 13).putObject("items").put("type", "string");
        fields.putObject("scope").put("type", "object");
        ObjectNode coverage = object(json);
        coverage.putArray("required").add("completeRequired").add("unitBound").add("segmentCount");
        ObjectNode coverageFields = (ObjectNode) coverage.path("properties");
        coverageFields.putObject("completeRequired").put("type", "boolean");
        coverageFields.putObject("unitBound").put("type", "boolean");
        coverageFields.putObject("segmentCount").put("type", "integer").put("minimum", 0);
        fields.set("coverage", coverage);
        return schema;
    }

    private ObjectNode pageOutput(ObjectMapper json, String collection) {
        ObjectNode schema = object(json);
        schema.putArray("required").add(collection).add("hasMore");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject(collection).put("type", "array").put("maxItems", 100).putObject("items").put("type", "object");
        fields.putObject("hasMore").put("type", "boolean");
        return schema;
    }

}
