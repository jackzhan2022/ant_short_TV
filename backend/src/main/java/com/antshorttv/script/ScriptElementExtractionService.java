package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;

@Service
class ScriptElementExtractionService {

    private final ProjectAiConfigService projectAiConfigService;
    private final AiInvocationService aiInvocationService;
    private final TeamPointService teamPointService;
    private final ObjectMapper objectMapper;

    ScriptElementExtractionService(
        ProjectAiConfigService projectAiConfigService,
        AiInvocationService aiInvocationService,
        TeamPointService teamPointService,
        ObjectMapper objectMapper
    ) {
        this.projectAiConfigService = projectAiConfigService;
        this.aiInvocationService = aiInvocationService;
        this.teamPointService = teamPointService;
        this.objectMapper = objectMapper;
    }

    ScriptElementExtractionResult extract(
        TenantContext context,
        Long projectId,
        ScriptEntity script,
        ScriptElementType elementType
    ) {
        return switch (elementType) {
            case CHARACTER -> new ScriptElementExtractionResult(
                elementType,
                extractCharacters(context, projectId, script),
                List.of(),
                List.of()
            );
            case SCENE -> new ScriptElementExtractionResult(
                elementType,
                List.of(),
                extractScenes(context, projectId, script),
                List.of()
            );
            case PROP -> new ScriptElementExtractionResult(
                elementType,
                List.of(),
                List.of(),
                extractProps(context, projectId, script)
            );
            case ALL -> new ScriptElementExtractionResult(
                elementType,
                extractCharacters(context, projectId, script),
                extractScenes(context, projectId, script),
                extractProps(context, projectId, script)
            );
        };
    }

    private List<ScriptElementExtractionResult.CharacterElement> extractCharacters(
        TenantContext context,
        Long projectId,
        ScriptEntity script
    ) {
        String response = callElementExtraction(context, projectId, AiBusinessScene.CHARACTER_EXTRACT, script);
        JsonNode items = extractionItems(parseExtractionResponse(response), "characters");
        return stream(items)
            .map(item -> new ScriptElementExtractionResult.CharacterElement(
                text(item, "name"),
                text(item, "roleType"),
                text(item, "gender"),
                text(item, "ageRange"),
                text(item, "identity"),
                arrayText(item, "personality"),
                text(item, "appearance"),
                text(item, "prompt")
            ))
            .filter(item -> item.name() != null)
            .toList();
    }

    private List<ScriptElementExtractionResult.SceneElement> extractScenes(
        TenantContext context,
        Long projectId,
        ScriptEntity script
    ) {
        String response = callElementExtraction(context, projectId, AiBusinessScene.SCENE_EXTRACT, script);
        JsonNode items = extractionItems(parseExtractionResponse(response), "scenes");
        return stream(items)
            .map(item -> new ScriptElementExtractionResult.SceneElement(
                text(item, "name"),
                text(item, "sceneType"),
                text(item, "atmosphere"),
                text(item, "description"),
                text(item, "visualStyle"),
                text(item, "prompt")
            ))
            .filter(item -> item.name() != null)
            .toList();
    }

    private List<ScriptElementExtractionResult.PropElement> extractProps(
        TenantContext context,
        Long projectId,
        ScriptEntity script
    ) {
        String response = callElementExtraction(context, projectId, AiBusinessScene.PROP_EXTRACT, script);
        JsonNode items = extractionItems(parseExtractionResponse(response), "props");
        return stream(items)
            .map(item -> new ScriptElementExtractionResult.PropElement(
                text(item, "name"),
                text(item, "propType"),
                text(item, "appearance"),
                text(item, "plotFunction"),
                text(item, "relatedCharacter"),
                text(item, "prompt")
            ))
            .filter(item -> item.name() != null)
            .toList();
    }

    private String callElementExtraction(TenantContext context, Long projectId, AiBusinessScene scene, ScriptEntity script) {
        teamPointService.consumeForAi(context, 1, scene.pointScene(), null, "AI 调用消耗积分");
        Long modelId = projectAiConfigService.resolveModelId(context.tenantId(), projectId, "TEXT");
        return aiInvocationService.invokeText(AiInvocationRequest.text()
            .tenantId(context.tenantId())
            .userId(context.userId())
            .projectId(projectId)
            .modelId(modelId)
            .scene(scene)
            .requestSummary(script.getTitle())
            .templateVariables(Map.of(
                "scriptTitle", script.getTitle() == null ? "" : script.getTitle(),
                "scriptContent", script.getContent() == null ? "" : script.getContent()
            ))
            .build()).content();
    }

    private JsonNode parseExtractionResponse(String response) {
        try {
            return objectMapper.readTree(stripCodeFence(response));
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "AI 提取结果格式异常，请重试。");
        }
    }

    private String stripCodeFence(String response) {
        if (response == null) {
            return "";
        }
        String value = response.trim();
        if (value.startsWith("```")) {
            int firstBreak = value.indexOf('\n');
            if (firstBreak >= 0) {
                value = value.substring(firstBreak + 1);
            }
            if (value.endsWith("```")) {
                value = value.substring(0, value.length() - 3);
            }
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private JsonNode extractionItems(JsonNode root, String field) {
        JsonNode direct = root.path(field);
        if (direct.isArray()) {
            return direct;
        }
        JsonNode nested = root.path("data").path(field);
        if (nested.isArray()) {
            return nested;
        }
        return objectMapper.createArrayNode();
    }

    private Stream<JsonNode> stream(JsonNode node) {
        return node == null || !node.isArray() ? Stream.empty() : StreamSupport.stream(node.spliterator(), false);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            String text = value.asText().trim();
            return text.isBlank() ? null : text;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<String> arrayText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            return List.of();
        }
        return Arrays.stream(objectMapper.convertValue(value, String[].class))
            .map(item -> item == null ? "" : item.trim())
            .filter(item -> !item.isBlank())
            .toList();
    }
}
