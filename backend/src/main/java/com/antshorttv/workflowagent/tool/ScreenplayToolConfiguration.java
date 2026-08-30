package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ScreenplayToolConfiguration {
    @Bean
    WorkflowToolDefinition readProjectContextTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_project_context", "读取项目上下文", "读取当前授权项目的基础配置。",
            emptyInput(json), projectOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readProjectContext(context)));
    }

    @Bean
    WorkflowToolDefinition listEpisodeScriptsTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("list_episode_scripts", "列出剧集", "列出当前项目的有效剧集。",
            emptyInput(json), episodeListOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.listEpisodeScripts(context)));
    }

    @Bean
    WorkflowToolDefinition readEpisodeScriptTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_episode_script", "读取当前剧集", "读取可信作用域内当前剧集的完整剧本。",
            emptyInput(json), episodeOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readEpisodeScript(context)));
    }

    @Bean
    WorkflowToolDefinition readAdjacentEpisodesTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_adjacent_episodes", "读取相邻剧集", "读取当前剧集的上一集和下一集。",
            emptyInput(json), adjacentOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readAdjacentEpisodes(context)));
    }

    @Bean
    WorkflowToolDefinition readScriptAnalysisTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_script_analysis", "读取剧本分析", "读取当前项目最近一次剧本分析结果。",
            emptyInput(json), analysisOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readScriptAnalysis(context)));
    }

    @Bean
    WorkflowToolDefinition readScriptAssetsTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_script_assets", "读取剧本资产", "读取当前项目的人物、场景和道具资产。",
            emptyInput(json), assetsOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readScriptAssets(context)));
    }

    @Bean
    WorkflowToolDefinition validateScreenplayFormatTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("validate_screenplay_format", "校验剧本格式", "校验格式化短剧剧本的场景头和对白格式。",
            contentInput(json), validationOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.validateScreenplayFormat(arguments.path("content").asText())));
    }

    @Bean
    WorkflowToolDefinition saveEpisodeScriptTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("save_episode_script", "保存剧集剧本", "创建新的剧集剧本版本并将其设为当前版本。",
            contentInput(json), saveOutput(json), ToolRiskLevel.WRITE,
            executor((context, arguments) -> data.saveEpisodeScript(context,
                arguments.path("content").asText())));
    }

    private WorkflowToolDefinition definition(
        String code,
        String name,
        String description,
        JsonNode input,
        JsonNode output,
        ToolRiskLevel risk,
        WorkflowToolExecutor executor
    ) {
        return new WorkflowToolDefinition(code, name, description, input, output, risk,
            ToolFailurePolicy.TERMINAL, executor);
    }

    private ObjectNode emptyInput(ObjectMapper json) {
        return json.createObjectNode().put("type", "object").put("additionalProperties", false)
            .set("properties", json.createObjectNode());
    }

    private ObjectNode contentInput(ObjectMapper json) {
        ObjectNode schema = json.createObjectNode().put("type", "object")
            .put("additionalProperties", false);
        schema.putArray("required").add("content");
        schema.putObject("properties").putObject("content").put("type", "string");
        return schema;
    }

    private ObjectNode objectSchema(ObjectMapper json) {
        return json.createObjectNode().put("type", "object")
            .put("additionalProperties", false).set("properties", json.createObjectNode());
    }

    private ObjectNode projectOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("projectId").add("name").add("code").add("status");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("projectId").put("type", "integer");
        for (String field : new String[]{"name", "code", "status"}) {
            fields.putObject(field).put("type", "string");
        }
        for (String field : new String[]{"description", "aspectRatio", "scriptType",
            "breakdownStrength", "visualStyle"}) {
            fields.set(field, nullableType(json, "string"));
        }
        return schema;
    }

    private ObjectNode episodeListOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("episodes");
        ObjectNode item = objectSchema(json);
        item.putArray("required").add("episodeId").add("episodeNo");
        ((ObjectNode) item.path("properties")).putObject("episodeId").put("type", "integer");
        ((ObjectNode) item.path("properties")).putObject("episodeNo").put("type", "integer");
        for (String field : new String[]{"title", "summary", "status"}) {
            ((ObjectNode) item.path("properties")).set(field, nullableType(json, "string"));
        }
        ((ObjectNode) schema.path("properties")).putObject("episodes").put("type", "array")
            .set("items", item);
        return schema;
    }

    private ObjectNode episodeOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("episodeId").add("episodeNo").add("content");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("episodeId").put("type", "integer");
        fields.putObject("episodeNo").put("type", "integer");
        fields.putObject("content").put("type", "string");
        for (String field : new String[]{"title", "summary", "status"}) {
            fields.set(field, nullableType(json, "string"));
        }
        return schema;
    }

    private ObjectNode adjacentOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        ObjectNode fields = (ObjectNode) schema.path("properties");
        ObjectNode episode = episodeOutput(json);
        episode.set("type", json.createArrayNode().add("object").add("null"));
        fields.set("previous", episode);
        fields.set("next", episode.deepCopy());
        return schema;
    }

    private ObjectNode analysisOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("stages");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.set("task", nullableType(json, "object"));
        fields.putObject("stages").put("type", "array")
            .set("items", json.createObjectNode().put("type", "object"));
        return schema;
    }

    private ObjectNode assetsOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("characters").add("scenes").add("props");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        for (String field : new String[]{"characters", "scenes", "props"}) {
            fields.putObject(field).put("type", "array")
                .set("items", json.createObjectNode().put("type", "object"));
        }
        return schema;
    }

    private ObjectNode validationOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("valid").add("errors");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("valid").put("type", "boolean");
        fields.putObject("errors").put("type", "array")
            .set("items", json.createObjectNode().put("type", "string"));
        return schema;
    }

    private ObjectNode saveOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("episodeId").add("versionId").add("versionNo").add("current");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("episodeId").put("type", "integer");
        fields.putObject("versionId").put("type", "integer");
        fields.putObject("versionNo").put("type", "integer");
        fields.putObject("current").put("type", "boolean");
        return schema;
    }

    private ObjectNode nullableType(ObjectMapper json, String type) {
        ObjectNode schema = json.createObjectNode();
        schema.putArray("type").add(type).add("null");
        return schema;
    }

    private WorkflowToolExecutor executor(ToolCall call) {
        return new WorkflowToolExecutor() {
            @Override
            public JsonNode execute(ToolExecutionContext context, JsonNode arguments) {
                return call.execute(context, arguments);
            }
        };
    }

    @FunctionalInterface
    private interface ToolCall {
        JsonNode execute(ToolExecutionContext context, JsonNode arguments);
    }
}
