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
    WorkflowToolDefinition readProjectFullScriptTool(ScreenplayToolDataService data, ObjectMapper json) {
        return definition("read_project_full_script", "读取整部剧本",
            "按剧集顺序读取当前项目全部有效剧集的完整剧本，并保留每集边界。",
            emptyInput(json), fullScriptOutput(json), ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readProjectFullScript(context)));
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
    WorkflowToolDefinition readCurrentScriptTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("content").add("contentHash").add("updatedAt");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("content").put("type", "string");
        fields.putObject("contentHash").put("type", "string");
        fields.set("updatedAt", nullableType(json, "string"));
        return definition("read_current_script", "读取当前剧本", "读取可信作用域中的当前剧本正文。",
            emptyInput(json), output, ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readCurrentScript(context)));
    }

    @Bean
    WorkflowToolDefinition readScriptStructureTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("contentHash").add("snapshotKey")
            .add("totalChunks").add("chunks").add("anchors");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("contentHash").put("type", "string");
        fields.putObject("snapshotKey").put("type", "string");
        fields.putObject("totalChunks").put("type", "integer").put("minimum", 0);
        fields.putObject("chunks").put("type", "array")
            .putObject("items").put("type", "object");
        fields.putObject("anchors").put("type", "array")
            .putObject("items").put("type", "object");
        return definition("read_script_structure", "读取剧本结构",
            "按可信剧本作用域建立可恢复的结构化分块，不返回完整正文。",
            emptyInput(json), output, ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readScriptStructure(context)));
    }

    @Bean
    WorkflowToolDefinition analyzeScriptChunksTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("total").add("completed").add("failed")
            .add("candidates").add("anchors").add("aiCallLogIds");
        ObjectNode fields = (ObjectNode) output.path("properties");
        for (String name : new String[]{"total", "completed", "failed"}) {
            fields.putObject(name).put("type", "integer").put("minimum", 0);
        }
        for (String name : new String[]{"candidates", "anchors", "aiCallLogIds"}) {
            fields.putObject(name).put("type", "array");
        }
        fields.putObject("recommendedEpisodes").put("type", "array");
        return definition("analyze_script_chunks", "分析剧本分块",
            "分析已建立的可信剧本分块并返回紧凑边界候选。",
            emptyInput(json), output, ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.analyzeScriptChunks(context)));
    }

    @Bean
    WorkflowToolDefinition readCurrentEpisodeTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("episodeKey").add("episodeNo").add("content")
            .add("contentFingerprint").add("sourceSegments").add("assetCatalog");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("episodeKey").put("type", "string").put("maxLength", 100);
        fields.putObject("episodeNo").put("type", "integer").put("minimum", 1);
        fields.set("title", nullableType(json, "string"));
        fields.putObject("content").put("type", "string").put("maxLength", 200000);
        fields.putObject("contentFingerprint").put("type", "string").put("maxLength", 128);
        ObjectNode segments = fields.putObject("sourceSegments").put("type", "array")
            .put("maxItems", 10_000);
        ObjectNode segment = objectSchema(json);
        segment.putArray("required").add("id").add("type").add("text").add("requiredCoverage");
        ObjectNode segmentFields = (ObjectNode) segment.path("properties");
        segmentFields.putObject("id").put("type", "string").put("pattern", "^S\\d{4,}$");
        segmentFields.putObject("type").put("type", "string").putArray("enum")
            .add("METADATA").add("SCENE").add("ACTION").add("DIALOGUE")
            .add("NARRATION").add("INNER_OS");
        segmentFields.putObject("text").put("type", "string").put("maxLength", 200000);
        segmentFields.putObject("requiredCoverage").put("type", "boolean");
        segments.set("items", segment);
        ObjectNode catalog = objectSchema(json);
        catalog.putArray("required").add("characters").add("scenes").add("props");
        ObjectNode catalogFields = (ObjectNode) catalog.path("properties");
        for (String name : new String[]{"characters", "scenes", "props"}) {
            ObjectNode array = catalogFields.putObject(name).put("type", "array").put("maxItems", 200);
            ObjectNode item = objectSchema(json);
            item.putArray("required").add("assetKey").add("name").add("normalizedName")
                .add("aliases").add("variants");
            ObjectNode itemFields = (ObjectNode) item.path("properties");
            itemFields.putObject("assetKey").put("type", "string").put("maxLength", 64);
            itemFields.putObject("name").put("type", "string").put("maxLength", 100);
            itemFields.putObject("normalizedName").put("type", "string").put("maxLength", 100);
            itemFields.putObject("aliases").put("type", "array").put("maxItems", 50)
                .putObject("items").put("type", "string").put("maxLength", 100);
            itemFields.putObject("variants").put("type", "array").put("maxItems", 50)
                .putObject("items").put("type", "object");
            array.set("items", item);
        }
        fields.set("assetCatalog", catalog);
        return definition("read_current_episode", "读取当前正式剧集",
            "读取可信作用域中的当前有效剧集、内容指纹和紧凑资产目录。",
            emptyInput(json), output, ToolRiskLevel.READ_ONLY,
            executor((context, arguments) -> data.readCurrentEpisode(context)));
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

    @Bean
    WorkflowToolDefinition saveGlobalUnderstandingTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("saved").add("globalUnderstandingId")
            .add("scriptId").add("contentHash").add("stageStatus");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("saved").put("type", "boolean");
        fields.putObject("globalUnderstandingId").put("type", "integer");
        fields.putObject("scriptId").put("type", "integer");
        fields.putObject("contentHash").put("type", "string");
        fields.set("stageStatus", nullableType(json, "string"));
        return definition("save_global_understanding", "保存剧情全局理解",
            "保存当前剧本的正式剧情全局理解文档。", globalUnderstandingInput(json), output,
            ToolRiskLevel.WRITE, executor((context, arguments) -> data.saveGlobalUnderstanding(
                context, arguments.path("schemaVersion").asInt(), arguments.path("content"))));
    }

    @Bean
    WorkflowToolDefinition saveEpisodeSplittingTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("saved").add("scriptId").add("contentHash")
            .add("episodeCount").add("episodes").add("resultId").add("stageStatus");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("saved").put("type", "boolean");
        fields.putObject("scriptId").put("type", "integer");
        fields.putObject("contentHash").put("type", "string");
        fields.putObject("episodeCount").put("type", "integer").put("minimum", 1);
        fields.putObject("episodes").put("type", "array").putObject("items").put("type", "object");
        fields.set("resultId", nullableType(json, "integer"));
        fields.set("stageStatus", nullableType(json, "string"));
        return definition("save_episode_splitting", "保存剧集智能拆分",
            "验证当前剧本边界并原子覆盖正式剧集集合。", episodeSplittingInput(json), output,
            ToolRiskLevel.WRITE, executor((context, arguments) -> data.saveEpisodeSplitting(
                context, arguments.path("schemaVersion").asInt(), arguments.path("episodes"))));
    }

    private ObjectNode episodeSplittingInput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("schemaVersion").add("episodes");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("schemaVersion").put("type", "integer").put("minimum", 1).put("maximum", 1);
        ObjectNode episodes = fields.putObject("episodes").put("type", "array")
            .put("minItems", 1).put("maxItems", 500);
        ObjectNode item = objectSchema(json);
        item.putArray("required").add("title").add("startMarker").add("endMarker");
        ObjectNode itemFields = (ObjectNode) item.path("properties");
        itemFields.putObject("title").put("type", "string").put("minLength", 1).put("maxLength", 200);
        itemFields.putObject("startMarker").put("type", "string").put("minLength", 1)
            .put("maxLength", 2000);
        itemFields.putObject("endMarker").put("type", "string").put("minLength", 1)
            .put("maxLength", 2000);
        episodes.set("items", item);
        return schema;
    }

    @Bean
    WorkflowToolDefinition saveEpisodeSummaryTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("saved").add("summaryId").add("episodeKey")
            .add("contentFingerprint");
        ObjectNode outputFields = (ObjectNode) output.path("properties");
        outputFields.putObject("saved").put("type", "boolean");
        outputFields.putObject("summaryId").put("type", "integer");
        outputFields.putObject("episodeKey").put("type", "string");
        outputFields.putObject("contentFingerprint").put("type", "string");
        return definition("save_episode_summary", "保存剧集概要", "覆盖保存当前剧集的正式可编辑概要。",
            episodeSummaryInput(json), output, ToolRiskLevel.WRITE,
            executor((context, arguments) -> data.saveEpisodeSummary(
                context, arguments.path("schemaVersion").asInt(), arguments.path("summary").asText(),
                arguments.path("highlights"), arguments.path("endingHook"))));
    }

    @Bean
    WorkflowToolDefinition saveEpisodeAssetsTool(ScreenplayToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("saved").add("analysisId").add("episodeKey")
            .add("contentFingerprint").add("counts");
        ObjectNode outputFields = (ObjectNode) output.path("properties");
        outputFields.putObject("saved").put("type", "boolean");
        outputFields.putObject("analysisId").put("type", "integer");
        outputFields.putObject("episodeKey").put("type", "string");
        outputFields.putObject("contentFingerprint").put("type", "string");
        outputFields.putObject("counts").put("type", "object");
        return definition("save_episode_assets", "保存本集角色场景道具",
            "原子匹配或创建正式资产、视觉形态及当前剧集绑定。",
            episodeAssetsInput(json), output, ToolRiskLevel.WRITE, ToolFailurePolicy.RETURN_TO_MODEL,
            executor((context, arguments) -> data.saveEpisodeAssets(context, arguments)));
    }

    @Bean
    WorkflowToolDefinition saveEpisodeStoryboardsTool(StoryboardToolDataService data, ObjectMapper json) {
        ObjectNode output = objectSchema(json);
        output.putArray("required").add("saved").add("episodeId")
            .add("storyboardCount").add("storyboardIds");
        ObjectNode fields = (ObjectNode) output.path("properties");
        fields.putObject("saved").put("type", "boolean");
        fields.putObject("episodeId").put("type", "integer");
        fields.putObject("storyboardCount").put("type", "integer").put("minimum", 1);
        fields.putObject("storyboardIds").put("type", "array").putObject("items").put("type", "integer");
        return definition("save_episode_storyboards", "保存本集正式分镜",
            "完整校验并原子覆盖当前有效剧集的正式多镜头分镜。",
            episodeStoryboardsInput(json), output, ToolRiskLevel.WRITE,
            ToolFailurePolicy.RETURN_TO_MODEL,
            executor((context, arguments) -> data.saveEpisodeStoryboards(context, arguments)));
    }

    private ObjectNode episodeStoryboardsInput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("schemaVersion").add("episodeFingerprint").add("storyboards");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("schemaVersion").put("type", "integer").put("minimum", 2).put("maximum", 2);
        fields.putObject("episodeFingerprint").put("type", "string").put("minLength", 1).put("maxLength", 128);
        ObjectNode boards = fields.putObject("storyboards").put("type", "array")
            .put("minItems", 1).put("maxItems", 200);
        ObjectNode board = objectSchema(json);
        board.putArray("required").add("storyboardNo").add("sourceFrom")
            .add("sourceTo").add("usedAssetKeys").add("shots");
        ObjectNode boardFields = (ObjectNode) board.path("properties");
        boardFields.putObject("storyboardNo").put("type", "integer").put("minimum", 1);
        boardFields.putObject("sourceFrom").put("type", "string").put("pattern", "^S\\d{4,}$").put("maxLength", 16);
        boardFields.putObject("sourceTo").put("type", "string").put("pattern", "^S\\d{4,}$").put("maxLength", 16);
        boardFields.set("time", nullableType(json, "string").put("maxLength", 100));
        boardFields.set("lighting", nullableType(json, "string").put("maxLength", 1000));
        boardFields.set("usedAssetKeys", materialGroups(json));
        boardFields.set("unmatchedMaterials", materialGroups(json));
        ObjectNode shots = boardFields.putObject("shots").put("type", "array")
            .put("minItems", 2).put("maxItems", 20);
        ObjectNode shot = objectSchema(json);
        shot.putArray("required").add("shotNo").add("durationSeconds").add("positioning").add("action")
            .add("soundSegmentIds");
        ObjectNode shotFields = (ObjectNode) shot.path("properties");
        shotFields.putObject("shotNo").put("type", "integer").put("minimum", 1);
        shotFields.putObject("durationSeconds").put("type", "number").put("minimum", 1.5).put("maximum", 4);
        shotFields.putObject("positioning").put("type", "string").put("minLength", 1).put("maxLength", 1000);
        shotFields.putObject("action").put("type", "string").put("minLength", 1).put("maxLength", 1000);
        shotFields.putObject("soundSegmentIds").put("type", "array").put("maxItems", 100)
            .put("uniqueItems", true).putObject("items").put("type", "string")
            .put("pattern", "^S\\d{4,}$").put("maxLength", 16);
        shots.set("items", shot);
        boards.set("items", board);
        return schema;
    }

    private ObjectNode materialGroups(ObjectMapper json) {
        ObjectNode groups = objectSchema(json);
        groups.putArray("required").add("characters").add("scenes").add("props");
        ObjectNode fields = (ObjectNode) groups.path("properties");
        for (String field : new String[]{"characters", "scenes", "props"}) {
            fields.putObject(field).put("type", "array").put("maxItems", 100)
                .putObject("items").put("type", "string").put("minLength", 1).put("maxLength", 100);
        }
        return groups;
    }

    private ObjectNode episodeAssetsInput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("schemaVersion").add("characters")
            .add("characterLooks").add("scenes").add("props").add("propVariants");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("schemaVersion").put("type", "integer").put("minimum", 1).put("maximum", 1);
        fields.set("characters", identityArray(json, 100));
        fields.set("scenes", sceneArray(json));
        fields.set("props", propArray(json));
        fields.set("characterLooks", variantArray(json, "characterLocalKey", 300));
        fields.set("propVariants", variantArray(json, "propLocalKey", 300));
        return schema;
    }

    private ObjectNode identityArray(ObjectMapper json, int maxItems) {
        ObjectNode array = json.createObjectNode().put("type", "array").put("maxItems", maxItems);
        ObjectNode item = objectSchema(json);
        item.putArray("required").add("localKey").add("name").add("aliases").add("evidence");
        ObjectNode fields = (ObjectNode) item.path("properties");
        fields.putObject("localKey").put("type", "string").put("minLength", 1).put("maxLength", 64);
        fields.set("assetKey", nullableType(json, "string").put("maxLength", 64));
        fields.putObject("name").put("type", "string").put("minLength", 1).put("maxLength", 100);
        fields.set("aliases", aliasArray(json));
        fields.putObject("evidence").put("type", "string").put("minLength", 1).put("maxLength", 1000);
        array.set("items", item);
        return array;
    }

    private ObjectNode sceneArray(ObjectMapper json) {
        ObjectNode array = identityArray(json, 200);
        ObjectNode fields = (ObjectNode) array.path("items").path("properties");
        fields.set("description", nullableType(json, "string").put("maxLength", 4000));
        fields.set("timeAtmosphere", nullableType(json, "string").put("maxLength", 500));
        fields.set("usageEvidence", nullableType(json, "string").put("maxLength", 1000));
        return array;
    }

    private ObjectNode propArray(ObjectMapper json) {
        ObjectNode array = identityArray(json, 300);
        ObjectNode fields = (ObjectNode) array.path("items").path("properties");
        fields.set("ownerCharacterLocalKey", nullableType(json, "string").put("maxLength", 64));
        fields.set("description", nullableType(json, "string").put("maxLength", 4000));
        return array;
    }

    private ObjectNode aliasArray(ObjectMapper json) {
        ObjectNode array = json.createObjectNode().put("type", "array").put("maxItems", 30);
        ObjectNode item = objectSchema(json);
        item.putArray("required").add("name").add("evidence");
        ((ObjectNode) item.path("properties")).putObject("name")
            .put("type", "string").put("minLength", 1).put("maxLength", 100);
        ((ObjectNode) item.path("properties")).putObject("evidence")
            .put("type", "string").put("minLength", 1).put("maxLength", 1000);
        array.set("items", item);
        return array;
    }

    private ObjectNode variantArray(ObjectMapper json, String ownerField, int maxItems) {
        ObjectNode array = json.createObjectNode().put("type", "array").put("maxItems", maxItems);
        ObjectNode item = objectSchema(json);
        item.putArray("required").add("localKey").add(ownerField).add("name")
            .add("evidence").add("preferred");
        ObjectNode fields = (ObjectNode) item.path("properties");
        fields.putObject("localKey").put("type", "string").put("minLength", 1).put("maxLength", 64);
        fields.putObject(ownerField).put("type", "string").put("minLength", 1).put("maxLength", 64);
        fields.set("variantKey", nullableType(json, "string").put("maxLength", 64));
        fields.putObject("name").put("type", "string").put("minLength", 1).put("maxLength", 100);
        fields.set("description", nullableType(json, "string").put("maxLength", 4000));
        fields.putObject("evidence").put("type", "string").put("minLength", 1).put("maxLength", 1000);
        fields.putObject("preferred").put("type", "boolean");
        array.set("items", item);
        return array;
    }

    private ObjectNode episodeSummaryInput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("schemaVersion").add("summary").add("highlights").add("endingHook");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("schemaVersion").put("type", "integer").put("minimum", 1).put("maximum", 1);
        fields.putObject("summary").put("type", "string").put("minLength", 1).put("maxLength", 20000);
        fields.putObject("highlights").put("type", "array").put("minItems", 2).put("maxItems", 5)
            .putObject("items").put("type", "string").put("minLength", 1).put("maxLength", 1000);
        ObjectNode endingHook = nullableType(json, "string");
        endingHook.put("maxLength", 2000);
        fields.set("endingHook", endingHook);
        return schema;
    }

    private ObjectNode globalUnderstandingInput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("schemaVersion").add("content");
        ObjectNode fields = (ObjectNode) schema.path("properties");
        fields.putObject("schemaVersion").put("type", "integer").put("minimum", 1).put("maximum", 1);
        ObjectNode content = objectSchema(json);
        String[] required = {"logline", "synopsis", "genres", "themes", "worldSetting",
            "coreConflict", "relationships", "turningPoints", "ending", "endingHook",
            "narrativeStyle", "targetAudience"};
        for (String field : required) {
            content.withArray("required").add(field);
        }
        ObjectNode contentFields = (ObjectNode) content.path("properties");
        for (String field : new String[]{"logline", "synopsis", "worldSetting", "coreConflict",
            "ending", "endingHook", "narrativeStyle", "targetAudience"}) {
            contentFields.putObject(field).put("type", "string").put("maxLength", 20000);
        }
        ObjectNode stringArray = json.createObjectNode().put("type", "array").put("maxItems", 100);
        stringArray.putObject("items").put("type", "string").put("maxLength", 200);
        contentFields.set("genres", stringArray.deepCopy());
        contentFields.set("themes", stringArray.deepCopy());
        contentFields.set("relationships", objectArray(json,
            new String[]{"characterA", "characterB", "relationship", "description"}, 500));
        contentFields.set("turningPoints", turningPointArray(json));
        fields.set("content", content);
        return schema;
    }

    private ObjectNode objectArray(ObjectMapper json, String[] names, int maxItems) {
        ObjectNode array = json.createObjectNode().put("type", "array").put("maxItems", maxItems);
        ObjectNode item = objectSchema(json);
        ObjectNode fields = (ObjectNode) item.path("properties");
        for (String name : names) {
            item.withArray("required").add(name);
            fields.putObject(name).put("type", "string").put("maxLength", 2000);
        }
        array.set("items", item);
        return array;
    }

    private ObjectNode turningPointArray(ObjectMapper json) {
        ObjectNode array = objectArray(json,
            new String[]{"title", "description", "impact"}, 500);
        ObjectNode item = (ObjectNode) array.path("items");
        item.withArray("required").add("sequence");
        ((ObjectNode) item.path("properties")).putObject("sequence").put("type", "integer")
            .put("minimum", 1);
        return array;
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

    private WorkflowToolDefinition definition(
        String code,
        String name,
        String description,
        JsonNode input,
        JsonNode output,
        ToolRiskLevel risk,
        ToolFailurePolicy failurePolicy,
        WorkflowToolExecutor executor
    ) {
        return new WorkflowToolDefinition(code, name, description, input, output, risk,
            failurePolicy, executor);
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

    private ObjectNode fullScriptOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        schema.putArray("required").add("episodes");
        ((ObjectNode) schema.path("properties")).putObject("episodes").put("type", "array")
            .set("items", episodeOutput(json));
        return schema;
    }

    private ObjectNode adjacentOutput(ObjectMapper json) {
        ObjectNode schema = objectSchema(json);
        ObjectNode fields = (ObjectNode) schema.path("properties");
        ObjectNode episode = objectSchema(json);
        episode.putArray("required").add("episodeId").add("episodeNo");
        ObjectNode episodeFields = (ObjectNode) episode.path("properties");
        episodeFields.putObject("episodeId").put("type", "integer");
        episodeFields.putObject("episodeNo").put("type", "integer");
        for (String field : new String[]{"title", "summary", "status", "endingSummary", "openingSummary"}) {
            episodeFields.set(field, nullableType(json, "string"));
        }
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
        fields.set("globalUnderstanding", nullableType(json, "object"));
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
