package com.antshorttv.script;

import com.antshorttv.ai.AiBusinessScene;
import com.antshorttv.ai.AiInvocationRequest;
import com.antshorttv.ai.AiInvocationResult;
import com.antshorttv.ai.AiInvocationService;
import com.antshorttv.ai.AiTextResponse;
import com.antshorttv.ai.ProjectAiConfigService;
import com.antshorttv.common.BusinessException;
import com.antshorttv.execution.AiExecutionContext;
import com.antshorttv.points.TeamPointService;
import com.antshorttv.security.TenantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScriptAnalysisExecutionService {
    private final ScriptAnalysisTaskMapper taskMapper;
    private final ScriptAnalysisStageMapper stageMapper;
    private final ScriptAnalysisResultMapper resultMapper;
    private final ScriptMapper scriptMapper;
    private final ScriptVersionMapper versionMapper;
    private final ScriptElementDraftService scriptElementDraftService;
    private final AiInvocationService aiInvocationService;
    private final ProjectAiConfigService projectAiConfigService;
    private final TeamPointService teamPointService;
    private final ObjectMapper objectMapper;

    public ScriptAnalysisExecutionService(
        ScriptAnalysisTaskMapper taskMapper,
        ScriptAnalysisStageMapper stageMapper,
        ScriptAnalysisResultMapper resultMapper,
        ScriptMapper scriptMapper,
        ScriptVersionMapper versionMapper,
        ScriptElementDraftService scriptElementDraftService,
        AiInvocationService aiInvocationService,
        ProjectAiConfigService projectAiConfigService,
        TeamPointService teamPointService,
        ObjectMapper objectMapper
    ) {
        this.taskMapper = taskMapper;
        this.stageMapper = stageMapper;
        this.resultMapper = resultMapper;
        this.scriptMapper = scriptMapper;
        this.versionMapper = versionMapper;
        this.scriptElementDraftService = scriptElementDraftService;
        this.aiInvocationService = aiInvocationService;
        this.projectAiConfigService = projectAiConfigService;
        this.teamPointService = teamPointService;
        this.objectMapper = objectMapper;
    }

    public void executeTask(Long taskId) {
        executeTask(taskId, null);
    }

    public ScriptAnalysisExecutionOutcome executeTask(Long taskId, AiExecutionContext executionContext) {
        InvocationTracker tracker = new InvocationTracker();
        ScriptAnalysisTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || "COMPLETED".equals(task.getStatus())) {
            return new ScriptAnalysisExecutionOutcome(List.of());
        }
        ScriptVersionEntity version = versionMapper.selectById(task.getScriptVersionId());
        if (version == null || !task.getScriptVersionId().equals(version.getId())) {
            failTask(task, "SCRIPT_VERSION_NOT_FOUND", "分析绑定的剧本版本不存在。");
            return new ScriptAnalysisExecutionOutcome(List.of());
        }

        task.setStatus("RUNNING");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        for (ScriptAnalysisStageEntity stage : stageMapper.selectByTask(task.getId())) {
            if ("SUCCEEDED".equals(stage.getStatus())) {
                continue;
            }
            executeStage(task, stage, version, executionContext, tracker);
            if ("FAILED".equals(stage.getStatus())) {
                throw new IllegalStateException(task.getErrorMessage() == null ? "Script analysis failed." : task.getErrorMessage());
            }
        }
        task.setStatus("COMPLETED");
        task.setCurrentStage(null);
        task.setCurrentAction("四阶段分析已完成");
        task.setOverallProgress(100);
        task.setCompletedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
        return new ScriptAnalysisExecutionOutcome(List.copyOf(tracker.calls));
    }

    private void executeStage(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        ScriptVersionEntity version,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        if (!isCurrentVersion(task)) {
            failStaleStage(task, stage);
            return;
        }
        stage.setStatus("RUNNING");
        stage.setProgressPercent(10);
        stage.setCurrentAction(actionFor(stage.getStageCode()));
        stage.setAttemptNo((stage.getAttemptNo() == null ? 0 : stage.getAttemptNo()) + 1);
        stage.setStartedAt(LocalDateTime.now());
        stage.setUpdatedAt(LocalDateTime.now());
        stageMapper.updateById(stage);
        task.setCurrentStage(stage.getStageCode());
        task.setCurrentAction(stage.getCurrentAction());
        task.setOverallProgress(Math.max(0, (stage.getStageOrder() - 1) * 25 + 5));
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);

        String normalizedJson = null;
        String rawResponse = null;
        Long callLogId = null;
        String requestId = null;
        Long durationMs = null;
        try {
            if ("EPISODE_SPLITTING".equals(stage.getStageCode())) {
                normalizedJson = splitEpisodes(task, version, executionContext, tracker);
                rawResponse = normalizedJson;
            } else if ("EPISODE_SUMMARY".equals(stage.getStageCode())) {
                SummaryCall call = summarizeEpisodes(task, version, executionContext, tracker);
                rawResponse = call.rawResponse();
                normalizedJson = call.normalizedJson();
                callLogId = call.callLogId();
                requestId = call.requestId();
                durationMs = call.durationMs();
            } else {
                AiCall call = invoke(task, version, stage.getStageCode(), executionContext, tracker);
                rawResponse = call.rawResponse();
                normalizedJson = normalizeJson(rawResponse);
                callLogId = call.callLogId();
                requestId = call.requestId();
                durationMs = call.durationMs();
            }
            JsonNode parsed = objectMapper.readTree(normalizedJson);
            validateStageResult(stage.getStageCode(), parsed, version.getContent());
            if (!isCurrentVersion(task)) {
                failStaleStage(task, stage);
                return;
            }
            if ("CHARACTER_SCENE_RECOGNITION".equals(stage.getStageCode())) {
                scriptElementDraftService.replaceAnalysisDrafts(
                    task.getTenantId(),
                    task.getProjectId(),
                    task.getCreatedBy(),
                    normalizeExtractionResult(parsed)
                );
            }
            persistResult(
                task,
                stage,
                "SUCCEEDED",
                rawResponse,
                normalizedJson,
                callLogId,
                requestId,
                durationMs == null ? stageDuration(stage) : durationMs,
                null,
                null,
                false
            );

            stage.setStatus("SUCCEEDED");
            stage.setProgressPercent(100);
            stage.setCompletedUnits(1);
            stage.setTotalUnits(1);
            stage.setCurrentAction("已完成");
            stage.setFinishedAt(LocalDateTime.now());
            stage.setRetryable(false);
            stage.setUpdatedAt(LocalDateTime.now());
            stageMapper.updateById(stage);
            task.setOverallProgress(stage.getStageOrder() * 25);
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        } catch (Exception exception) {
            String errorCode = exception instanceof BusinessException businessException
                ? businessException.getErrorCode().name()
                : "AI_ANALYSIS_FAILED";
            String errorMessage = exception.getMessage();
            persistResult(
                task,
                stage,
                "FAILED",
                rawResponse,
                normalizedJson,
                callLogId,
                requestId,
                stageDuration(stage),
                errorCode,
                errorMessage,
                true
            );
            stage.setStatus("FAILED");
            stage.setProgressPercent(Math.max(10, stage.getProgressPercent() == null ? 10 : stage.getProgressPercent()));
            stage.setErrorCode(errorCode);
            stage.setErrorMessage(errorMessage);
            stage.setRetryable(true);
            stage.setUpdatedAt(LocalDateTime.now());
            stageMapper.updateById(stage);
            failTask(task, errorCode, errorMessage);
        }
    }

    private SummaryCall summarizeEpisodes(ScriptAnalysisTaskEntity task, ScriptVersionEntity version) {
        return summarizeEpisodes(task, version, null, new InvocationTracker());
    }

    private SummaryCall summarizeEpisodes(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        JsonNode episodesNode;
        try {
            ScriptAnalysisResultEntity splitResult = latestSucceededResult(task, "EPISODE_SPLITTING");
            if (splitResult == null || splitResult.getNormalizedJson() == null) {
                throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "缺少可用于概要提炼的分集结果。");
            }
            episodesNode = objectMapper.readTree(splitResult.getNormalizedJson()).path("episodes");
        } catch (Exception exception) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集结果不可用，无法提炼概要。");
        }
        if (!episodesNode.isArray() || episodesNode.isEmpty()) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集结果为空，无法提炼概要。");
        }

        int maxConcurrency = 2;
        var executor = Executors.newFixedThreadPool(maxConcurrency);
        try {
            Semaphore semaphore = new Semaphore(maxConcurrency);
            List<CompletableFuture<EpisodeSummaryCall>> futures = new ArrayList<>();
            for (JsonNode episode : episodesNode) {
                futures.add(CompletableFuture.supplyAsync(
                    () -> summarizeEpisode(task, version, episode, semaphore, executionContext, tracker), executor
                ));
            }
            List<EpisodeSummaryCall> calls = futures.stream().map(CompletableFuture::join).sorted(Comparator.comparingInt(EpisodeSummaryCall::episodeNo)).toList();
            ArrayNode episodes = objectMapper.createArrayNode();
            StringBuilder raw = new StringBuilder();
            for (EpisodeSummaryCall call : calls) {
                JsonNode parsed = objectMapper.readTree(call.normalizedJson());
                JsonNode sourceEpisode = parsed.path("episodes").isArray() && parsed.path("episodes").size() > 0
                    ? parsed.path("episodes").get(0)
                    : null;
                ObjectNode node = episodes.addObject();
                node.put("episodeNo", call.episodeNo());
                node.put("summary", sourceEpisode == null ? "" : sourceEpisode.path("summary").asText(""));
                node.set("highlights", sourceEpisode == null ? objectMapper.createArrayNode() : sourceEpisode.path("highlights"));
                node.put("endingHook", sourceEpisode == null ? "" : sourceEpisode.path("endingHook").asText(""));
                if (raw.length() > 0) {
                    raw.append('\n');
                }
                raw.append(call.rawResponse());
            }
            ObjectNode root = objectMapper.createObjectNode();
            root.set("episodes", episodes);
            return new SummaryCall(
                raw.toString(),
                root.toString(),
                calls.isEmpty() ? null : calls.get(calls.size() - 1).callLogId(),
                calls.isEmpty() ? null : calls.get(calls.size() - 1).requestId(),
                calls.stream().map(EpisodeSummaryCall::durationMs).filter(value -> value != null).mapToLong(Long::longValue).sum()
            );
        } catch (Exception exception) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "剧集概要提炼失败。");
        } finally {
            executor.shutdownNow();
        }
    }

    private EpisodeSummaryCall summarizeEpisode(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        JsonNode episode,
        Semaphore semaphore,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            int episodeNo = episode.path("episodeNo").asInt(0);
            if (episodeNo <= 0) {
                throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "概要提炼集号不合法。");
            }
            AiCall call = invokeEpisodeSummary(task, version, episode, executionContext, tracker);
            return new EpisodeSummaryCall(episodeNo, call.rawResponse(), normalizeJson(call.rawResponse()), call.callLogId(), call.requestId(), call.durationMs());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "概要提炼被中断。");
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private AiCall invokeEpisodeSummary(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        JsonNode episode,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        if (executionContext == null) {
            TenantContext context = new TenantContext(task.getCreatedBy(), task.getTenantId(), null, null);
            teamPointService.consumeForAi(context, 1, AiBusinessScene.SCRIPT_EPISODE_SUMMARY.pointScene(), task.getId(), "剧本分析阶段");
        }
        Long modelId = projectAiConfigService.resolveModelId(task.getTenantId(), task.getProjectId(), "TEXT");
        int episodeNo = episode.path("episodeNo").asInt(0);
        AiInvocationRequest.Builder builder = AiInvocationRequest.text()
            .tenantId(task.getTenantId())
            .userId(task.getCreatedBy())
            .projectId(task.getProjectId())
            .taskId(task.getId())
            .modelId(modelId)
            .scene(AiBusinessScene.SCRIPT_EPISODE_SUMMARY)
            .promptTemplateId(AiBusinessScene.SCRIPT_EPISODE_SUMMARY.agentCode())
            .templateVariables(Map.of(
                "episodes",
                objectMapper.createArrayNode().add(episode)
            ))
            .requestSummary("script-analysis:EPISODE_SUMMARY")
            .traceId("script-analysis-%d-summary-%d".formatted(task.getId(), episodeNo));
        applyExecutionIdentity(builder, executionContext, "EPISODE_SUMMARY:" + episodeNo);
        var invocation = aiInvocationService.invokeText(builder.build());
        tracker.record(invocation);
        return new AiCall(invocation.content(), invocation.aiCallLogId(), invocation.providerRequestId(), invocation.durationMs());
    }

    private ScriptElementExtractionResult normalizeExtractionResult(JsonNode parsed) {
        List<ScriptElementExtractionResult.CharacterElement> characters = toCharacterElements(parsed.path("characters"));
        List<ScriptElementExtractionResult.SceneElement> scenes = toSceneElements(parsed.path("scenes"));
        List<ScriptElementExtractionResult.PropElement> props = toPropElements(parsed.path("props"));
        return new ScriptElementExtractionResult(ScriptElementType.ALL, characters, scenes, props);
    }

    private List<ScriptElementExtractionResult.CharacterElement> toCharacterElements(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
            .map(node -> new ScriptElementExtractionResult.CharacterElement(
                text(node, "name"),
                text(node, "roleType"),
                text(node, "gender"),
                text(node, "ageRange"),
                text(node, "identity"),
                textList(node.path("personality")),
                text(node, "appearance"),
                text(node, "prompt")
            ))
            .toList();
    }

    private List<ScriptElementExtractionResult.SceneElement> toSceneElements(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
            .map(node -> new ScriptElementExtractionResult.SceneElement(
                text(node, "name"),
                text(node, "sceneType"),
                text(node, "atmosphere"),
                text(node, "description"),
                text(node, "visualStyle"),
                text(node, "prompt")
            ))
            .toList();
    }

    private List<ScriptElementExtractionResult.PropElement> toPropElements(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
            .map(node -> new ScriptElementExtractionResult.PropElement(
                text(node, "name"),
                text(node, "propType"),
                text(node, "appearance"),
                text(node, "plotFunction"),
                text(node, "relatedCharacter"),
                text(node, "prompt")
            ))
            .toList();
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return null;
        }
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .map(item -> item == null ? null : item.asText(null))
            .filter(item -> item != null && !item.isBlank())
            .map(String::trim)
            .toList();
    }

    private String splitEpisodes(ScriptAnalysisTaskEntity task, ScriptVersionEntity version) {
        return splitEpisodes(task, version, null, new InvocationTracker());
    }

    private String splitEpisodes(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        List<ScriptEpisodeResponse> parsed = ScriptEpisodeParser.parse(version.getContent());
        if (parsed.size() > 1 || hasEpisodeHeading(version.getContent())) {
            return episodesJson(parsed);
        }
        AiCall call = invoke(task, version, "EPISODE_SPLITTING", executionContext, tracker);
        return normalizeJson(call.rawResponse());
    }

    private boolean hasEpisodeHeading(String content) {
        return ScriptEpisodeParser.parse(content).stream().anyMatch(item ->
            item.title() != null && !item.title().equals("第1集")
        );
    }

    private String episodesJson(List<ScriptEpisodeResponse> episodes) {
        ArrayNode values = objectMapper.createArrayNode();
        episodes.forEach(episode -> {
            ObjectNode node = values.addObject();
            node.put("episodeNo", episode.episodeNo());
            node.put("title", episode.title());
            node.put("content", episode.content());
            node.put("summary", "");
            node.put("endingHook", "");
        });
        ObjectNode root = objectMapper.createObjectNode();
        root.set("episodes", values);
        return root.toString();
    }

    private AiCall invoke(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        String stageCode,
        AiExecutionContext executionContext,
        InvocationTracker tracker
    ) {
        AiBusinessScene scene = switch (stageCode) {
            case "GLOBAL_UNDERSTANDING" -> AiBusinessScene.SCRIPT_GLOBAL_UNDERSTANDING;
            case "EPISODE_SPLITTING" -> AiBusinessScene.SCRIPT_EPISODE_SPLIT;
            case "EPISODE_SUMMARY" -> AiBusinessScene.SCRIPT_EPISODE_SUMMARY;
            case "CHARACTER_SCENE_RECOGNITION" -> AiBusinessScene.SCRIPT_CHARACTER_SCENE_RECOGNITION;
            default -> throw new IllegalArgumentException("未知分析阶段：" + stageCode);
        };
        if (executionContext == null) {
            TenantContext context = new TenantContext(task.getCreatedBy(), task.getTenantId(), null, null);
            teamPointService.consumeForAi(context, 1, scene.pointScene(), task.getId(), "剧本分析阶段");
        }
        Long modelId = projectAiConfigService.resolveModelId(task.getTenantId(), task.getProjectId(), "TEXT");
        Map<String, Object> variables = stageVariables(task, version, stageCode);
        AiInvocationRequest.Builder builder = AiInvocationRequest.text()
            .tenantId(task.getTenantId())
            .userId(task.getCreatedBy())
            .projectId(task.getProjectId())
            .taskId(task.getId())
            .modelId(modelId)
            .scene(scene)
            .promptTemplateId(scene.agentCode())
            .templateVariables(variables)
            .requestSummary("script-analysis:" + stageCode)
            .traceId("script-analysis-%d-%s".formatted(task.getId(), stageCode));
        applyExecutionIdentity(builder, executionContext, stageCode);
        var invocation = aiInvocationService.invokeText(builder.build());
        tracker.record(invocation);
        return new AiCall(invocation.content(), invocation.aiCallLogId(), invocation.providerRequestId(), invocation.durationMs());
    }

    private void applyExecutionIdentity(
        AiInvocationRequest.Builder builder,
        AiExecutionContext executionContext,
        String phase
    ) {
        if (executionContext == null) {
            return;
        }
        builder.executionId(executionContext.task().id)
            .attemptId(executionContext.claim().attemptId())
            .executionVersion(executionContext.task().executionVersion)
            .phase(phase)
            .idempotencyKey("execution:%d:v%d:%s".formatted(
                executionContext.task().id,
                executionContext.task().executionVersion,
                phase
            ));
    }

    private Map<String, Object> stageVariables(
        ScriptAnalysisTaskEntity task,
        ScriptVersionEntity version,
        String stageCode
    ) {
        Map<String, Object> variables = new java.util.HashMap<>();
        variables.put("scriptContent", version.getContent());
        variables.put("globalUnderstanding", latestResult(task, "GLOBAL_UNDERSTANDING", "{}"));
        variables.put("episodes", latestResult(task, "EPISODE_SPLITTING", "{\"episodes\":[]}"));
        return variables;
    }

    private String latestResult(ScriptAnalysisTaskEntity task, String stageCode, String fallback) {
        ScriptAnalysisResultEntity result = latestSucceededResult(task, stageCode);
        if (result != null && result.getNormalizedJson() != null) {
            return result.getNormalizedJson();
        }
        return fallback;
    }

    private ScriptAnalysisResultEntity latestSucceededResult(ScriptAnalysisTaskEntity task, String stageCode) {
        for (ScriptAnalysisStageEntity stage : stageMapper.selectByTask(task.getId())) {
            if (stageCode.equals(stage.getStageCode()) && "SUCCEEDED".equals(stage.getStatus())) {
                ScriptAnalysisResultEntity result = resultMapper.selectLatestByStage(stage.getId());
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private boolean isCurrentVersion(ScriptAnalysisTaskEntity task) {
        ScriptEntity currentScript = scriptMapper.selectById(task.getScriptId());
        return currentScript != null && task.getScriptVersionId().equals(currentScript.getCurrentVersionId());
    }

    private void failStaleStage(ScriptAnalysisTaskEntity task, ScriptAnalysisStageEntity stage) {
        stage.setStatus("FAILED");
        stage.setProgressPercent(stage.getProgressPercent() == null ? 0 : stage.getProgressPercent());
        stage.setErrorCode("STALE_SCRIPT_VERSION");
        stage.setErrorMessage("分析版本已过期，已停止更新旧结果。");
        stage.setRetryable(false);
        stage.setUpdatedAt(LocalDateTime.now());
        stageMapper.updateById(stage);
        failTask(task, "STALE_SCRIPT_VERSION", "分析版本已过期，已停止更新旧结果。");
    }

    private void validateStageResult(String stageCode, JsonNode result, String scriptContent) {
        if (!result.isObject()) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分析结果必须是 JSON 对象。");
        }
        if ("EPISODE_SPLITTING".equals(stageCode)) {
            JsonNode episodes = result.get("episodes");
            if (episodes == null || !episodes.isArray() || episodes.isEmpty()) {
                throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集结果必须包含非空 episodes 数组。");
            }
            int expectedNo = 1;
            String previousContent = null;
            for (JsonNode episode : episodes) {
                int episodeNo = episode.path("episodeNo").asInt(0);
                String content = episode.path("content").asText("").trim();
                if (episodeNo != expectedNo || content.isBlank()) {
                    throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集编号必须从1连续递增，且每集正文不能为空。");
                }
                if (previousContent != null && previousContent.equals(content)) {
                    throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集正文不能重复。");
                }
                previousContent = content;
                expectedNo++;
            }
            String source = ScriptEpisodeParser.parse(scriptContent).stream()
                .map(ScriptEpisodeResponse::content)
                .collect(java.util.stream.Collectors.joining())
                .replaceAll("\\s+", "");
            String combined = "";
            for (JsonNode episode : episodes) {
                combined += episode.path("content").asText("").replaceAll("\\s+", "");
            }
            if (!source.isBlank() && !combined.contains(source.substring(0, Math.min(24, source.length())))) {
                throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "分集正文未覆盖原始剧本开头内容。");
            }
        } else if ("EPISODE_SUMMARY".equals(stageCode)
            && (!result.has("episodes") || !result.get("episodes").isArray())) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "概要结果必须包含 episodes 数组。");
        } else if ("CHARACTER_SCENE_RECOGNITION".equals(stageCode)
            && (!result.has("characters") || !result.has("scenes") || !result.has("props"))) {
            throw new BusinessException(com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID, "角色场景识别结果字段不完整。");
        }
    }

    private String normalizeJson(String rawResponse) {
        String value = rawResponse == null ? "" : rawResponse.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            value = value.substring(start, end + 1);
        }
        try {
            objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new BusinessException(
                com.antshorttv.common.ErrorCode.AI_RESPONSE_INVALID,
                "AI 分析结果不是有效 JSON。"
            );
        }
        return value;
    }

    private Long stageDuration(ScriptAnalysisStageEntity stage) {
        if (stage.getStartedAt() == null) {
            return null;
        }
        return Math.max(0, java.time.Duration.between(stage.getStartedAt(), LocalDateTime.now()).toMillis());
    }

    private void persistResult(
        ScriptAnalysisTaskEntity task,
        ScriptAnalysisStageEntity stage,
        String status,
        String rawResponse,
        String normalizedJson,
        Long callLogId,
        String requestId,
        Long durationMs,
        String errorCode,
        String errorMessage,
        boolean retryable
    ) {
        ScriptAnalysisResultEntity result = new ScriptAnalysisResultEntity();
        result.setTaskId(task.getId());
        result.setStageId(stage.getId());
        result.setResultType(stage.getStageCode());
        result.setSchemaVersion("v1");
        result.setStatus(status);
        result.setRawResponse(sanitizeRawResponse(rawResponse));
        result.setNormalizedJson(normalizedJson);
        result.setProviderRequestId(requestId);
        result.setAiCallLogId(callLogId);
        result.setExecutionId(task.getExecutionId());
        result.setDurationMs(durationMs);
        result.setErrorCode(errorCode);
        result.setErrorMessage(errorMessage);
        result.setRetryable(retryable);
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        resultMapper.insert(result);
    }

    private String sanitizeRawResponse(String rawResponse) {
        if (rawResponse == null) {
            return null;
        }
        String value = rawResponse.trim();
        return value.length() <= 20000 ? value : value.substring(0, 20000);
    }

    private String actionFor(String stageCode) {
        return switch (stageCode) {
            case "GLOBAL_UNDERSTANDING" -> "正在理解剧情主线、人物关系和核心冲突";
            case "EPISODE_SPLITTING" -> "正在根据剧情节点智能拆分剧集";
            case "EPISODE_SUMMARY" -> "正在提炼每集概要和结尾悬念";
            case "CHARACTER_SCENE_RECOGNITION" -> "正在识别角色、场景和关键道具";
            default -> "正在分析剧本";
        };
    }

    private void failTask(ScriptAnalysisTaskEntity task, String errorCode, String message) {
        task.setStatus("FAILED");
        task.setErrorCode(errorCode);
        task.setErrorMessage(message == null ? "分析失败。" : message);
        task.setCurrentAction("分析失败，可重试失败阶段");
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private record AiCall(String rawResponse, Long callLogId, String requestId, Long durationMs) {
    }

    private record SummaryCall(String rawResponse, String normalizedJson, Long callLogId, String requestId, Long durationMs) {
    }

    private record EpisodeSummaryCall(int episodeNo, String rawResponse, String normalizedJson, Long callLogId, String requestId, Long durationMs) {
    }

    private static final class InvocationTracker {
        private final List<ScriptAnalysisCallEvidence> calls = new CopyOnWriteArrayList<>();

        private void record(AiInvocationResult<AiTextResponse> invocation) {
            calls.add(new ScriptAnalysisCallEvidence(
                invocation.aiCallLogId(), invocation.resolvedModelId(), invocation.providerId(),
                invocation.providerRequestId(), invocation.transportOutcome(), invocation.businessOutcome()
            ));
        }
    }
}
