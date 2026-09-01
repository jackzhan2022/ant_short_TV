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

    @Bean WorkflowToolDefinition readReviewIssueHistoryTool(ReviewToolDataService data, ObjectMapper json) {
        ObjectNode input = pagination(json, 100);
        return definition("read_review_issue_history", "读取审核历史", "读取当前维度与范围相关的既往正式问题和人工处理事件。",
            input, pageOutput(json, "issues"), ToolRiskLevel.READ_ONLY,
            executor((context, args) -> data.readHistory(context, args)));
    }

    @Bean WorkflowToolDefinition saveReviewUnitResultTool(ReviewToolDataService data, ObjectMapper json) {
        ObjectNode input = object(json);
        input.putArray("required").add("versionHash").add("scopeHash").add("dimensionsHash")
            .add("contentFingerprint").add("coverage").add("candidates");
        ObjectNode fields = (ObjectNode) input.path("properties");
        hashFields(fields);
        fields.putObject("contentFingerprint").put("type", "string").put("minLength", 1).put("maxLength", 128);
        fields.set("coverage", coverage(json));
        fields.set("candidates", issueArray(json, 100));
        return definition("save_review_unit_result", "保存审核单元候选", "验证并原子覆盖当前深度审核单元候选，不生成正式问题。命中请优先提供 startOffset/endOffset（相对 read_review_content 返回正文的零基区间），服务端会提取可信原文。",
            input, saveOutput(json, "unitSaved"), ToolRiskLevel.WRITE,
            executor((context, args) -> data.saveUnitResult(context, args)));
    }

    @Bean WorkflowToolDefinition readReviewUnitResultsTool(ReviewToolDataService data, ObjectMapper json) {
        return definition("read_review_unit_results", "读取审核单元候选", "仅聚合阶段读取完整且未变化的有序单元候选。",
            pagination(json, 100), pageOutput(json, "units"), ToolRiskLevel.READ_ONLY,
            executor((context, args) -> data.readUnitResults(context, args)));
    }

    @Bean WorkflowToolDefinition saveReviewResultTool(ReviewToolDataService data, ObjectMapper json) {
        ObjectNode input = object(json);
        input.putArray("required").add("versionHash").add("scopeHash").add("dimensionsHash")
            .add("score").add("conclusion").add("coverage").add("issues");
        ObjectNode fields = (ObjectNode) input.path("properties");
        hashFields(fields);
        fields.putObject("score").put("type", "integer").put("minimum", 0).put("maximum", 100);
        fields.putObject("conclusion").put("type", "string").put("minLength", 1).put("maxLength", 12000);
        fields.set("coverage", coverage(json));
        fields.set("issues", issueArray(json, 500));
        return definition("save_review_result", "保存正式审核结果", "验证冻结哈希、证据和生命周期后原子写入正式审核表。",
            input, saveOutput(json, "formalSaved"), ToolRiskLevel.WRITE,
            executor((context, args) -> data.saveResult(context, args)));
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

    private ObjectNode pagination(ObjectMapper json, int maximum) {
        ObjectNode schema = object(json);
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("page").put("type", "integer").put("minimum", 1).put("maximum", 10000);
        fields.putObject("pageSize").put("type", "integer").put("minimum", 1).put("maximum", maximum);
        return schema;
    }

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

    private ObjectNode saveOutput(ObjectMapper json, String idField) {
        ObjectNode schema = object(json);
        schema.putArray("required").add("saved").add(idField);
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("saved").put("type", "boolean");
        fields.putObject(idField).put("type", "integer");
        return schema;
    }

    private void hashFields(ObjectNode fields) {
        for (String name : new String[]{"versionHash", "scopeHash", "dimensionsHash"})
            fields.putObject(name).put("type", "string").put("minLength", 64).put("maxLength", 64);
    }

    private ObjectNode coverage(ObjectMapper json) {
        ObjectNode schema = object(json);
        schema.putArray("required").add("complete").add("anchors");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("complete").put("type", "boolean");
        fields.putObject("anchors").put("type", "array").put("maxItems", 1000)
            .putObject("items").put("type", "string").put("maxLength", 160);
        return schema;
    }

    private ObjectNode issueArray(ObjectMapper json, int maxItems) {
        ObjectNode array = json.createObjectNode().put("type", "array").put("maxItems", maxItems);
        ObjectNode issue = object(json);
        issue.putArray("required").add("dimension").add("severity").add("title")
            .add("problem").add("evidence").add("suggestion").add("hits");
        ObjectNode fields = (ObjectNode) issue.path("properties");
        for (String name : new String[]{"dimension", "severity", "title", "problem", "suggestion"})
            fields.putObject(name).put("type", "string").put("minLength", 1).put("maxLength", 4000);
        fields.putObject("evidence").put("type", "array").put("minItems", 1).put("maxItems", 20)
            .putObject("items").put("type", "string").put("maxLength", 2000);
        ObjectNode hits = fields.putObject("hits").put("type", "array").put("minItems", 1).put("maxItems", 50);
        ObjectNode hit = object(json);
        hit.putArray("required").add("anchor").add("excerpt");
        ((ObjectNode) hit.path("properties")).putObject("anchor").put("type", "string").put("maxLength", 160);
        ((ObjectNode) hit.path("properties")).putObject("excerpt").put("type", "string").put("minLength", 1).put("maxLength", 2000);
        ((ObjectNode) hit.path("properties")).putObject("startOffset").put("type", "integer").put("minimum", 0)
            .put("description", "命中在 read_review_content 返回正文中的零基起始偏移；与 endOffset 一起提供时由服务端提取可信原文。");
        ((ObjectNode) hit.path("properties")).putObject("endOffset").put("type", "integer").put("minimum", 1)
            .put("description", "命中在 read_review_content 返回正文中的结束偏移（不含该位置）。");
        ((ObjectNode) hit.path("properties")).putObject("episode").put("type", "integer").put("minimum", 1);
        ((ObjectNode) hit.path("properties")).putObject("scene").put("type", "string").put("maxLength", 64);
        ((ObjectNode) hit.path("properties")).putObject("shot").put("type", "integer").put("minimum", 1);
        ((ObjectNode) hit.path("properties")).putObject("line").put("type", "integer").put("minimum", 1);
        ((ObjectNode) hit.path("properties")).putObject("entity").put("type", "string").put("maxLength", 255);
        ((ObjectNode) hit.path("properties")).putObject("replacementText").put("type", "string").put("maxLength", 4000);
        hits.set("items", hit);
        array.set("items", issue);
        return array;
    }
}
