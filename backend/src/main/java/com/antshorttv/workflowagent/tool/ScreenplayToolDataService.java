package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.script.ScriptGlobalUnderstandingDocument;
import com.antshorttv.script.ScriptGlobalUnderstandingRepository;
import com.antshorttv.script.GlobalUnderstandingProgress;
import com.antshorttv.script.EpisodeSplitBoundaryResolver;
import com.antshorttv.script.ScriptEpisodeResponse;
import com.antshorttv.script.ScriptEpisodeService;
import com.antshorttv.script.ScriptEpisodeSummaryDocument;
import com.antshorttv.script.ScriptEpisodeSummaryRepository;
import com.antshorttv.script.EpisodeAssetPersistenceService;
import com.antshorttv.script.ScriptSplitChunkPlanner;
import com.antshorttv.script.ScriptSplitSnapshotStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScreenplayToolDataService {
    private static final Pattern SCENE_HEADING = Pattern.compile(
        "(?m)^## S\\d{2,} \\| (?:内景|外景) · .+ \\| .+$");
    private static final Pattern DIALOGUE = Pattern.compile("(?m)^\\S+：(?:（[^）]*）)?.+$");

    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissionGuard;
    private final ObjectMapper json;
    private final EpisodeScriptCurrentSelector currentSelector;
    private final ScriptGlobalUnderstandingRepository globalUnderstanding;
    private final GlobalUnderstandingProgressWriter progressWriter;
    private final ScriptEpisodeService episodeService;
    private final ScriptEpisodeSummaryRepository episodeSummaries;
    private final EpisodeAssetPersistenceService episodeAssets;
    private final ScriptSplitChunkPlanner splitChunkPlanner;
    private final ScriptSplitSnapshotStore splitSnapshotStore;
    private final EpisodeSplitBoundaryResolver splitBoundaryResolver = new EpisodeSplitBoundaryResolver();

    public ScreenplayToolDataService(
        JdbcTemplate jdbc,
        ProjectPermissionGuard permissionGuard,
        ObjectMapper json,
        EpisodeScriptCurrentSelector currentSelector,
        ScriptGlobalUnderstandingRepository globalUnderstanding,
        GlobalUnderstandingProgressWriter progressWriter,
        ScriptEpisodeService episodeService,
        ScriptEpisodeSummaryRepository episodeSummaries,
        EpisodeAssetPersistenceService episodeAssets,
        ScriptSplitChunkPlanner splitChunkPlanner,
        ScriptSplitSnapshotStore splitSnapshotStore
    ) {
        this.jdbc = jdbc;
        this.permissionGuard = permissionGuard;
        this.json = json;
        this.currentSelector = currentSelector;
        this.globalUnderstanding = globalUnderstanding;
        this.progressWriter = progressWriter;
        this.episodeService = episodeService;
        this.episodeSummaries = episodeSummaries;
        this.episodeAssets = episodeAssets;
        this.splitChunkPlanner = splitChunkPlanner;
        this.splitSnapshotStore = splitSnapshotStore;
    }

    public JsonNode readProjectContext(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, name, code, description, status, aspect_ratio, script_type,
                   breakdown_strength, visual_style
              from project
             where tenant_id = ? and id = ? and deleted_at is null
            """, context.tenantId(), context.projectId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在。");
        }
        Map<String, Object> row = rows.get(0);
        ObjectNode result = json.createObjectNode();
        result.put("projectId", number(row.get("id")));
        put(result, "name", row.get("name"));
        put(result, "code", row.get("code"));
        put(result, "description", row.get("description"));
        put(result, "status", row.get("status"));
        put(result, "aspectRatio", row.get("aspect_ratio"));
        put(result, "scriptType", row.get("script_type"));
        put(result, "breakdownStrength", row.get("breakdown_strength"));
        put(result, "visualStyle", row.get("visual_style"));
        return result;
    }

    public JsonNode listEpisodeScripts(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        ObjectNode result = json.createObjectNode();
        ArrayNode episodes = result.putArray("episodes");
        jdbc.queryForList("""
            select episode.id, episode.episode_no, episode.title, episode.summary, episode.status
              from script_episode episode
              join script on script.id = episode.script_id
             where episode.tenant_id = ? and episode.project_id = ?
               and episode.retired_at is null and script.deleted_at is null
             order by episode.episode_no, episode.id
            """, context.tenantId(), context.projectId()).forEach(row -> episodes.add(episodeSummary(row)));
        return result;
    }

    public JsonNode readEpisodeScript(ToolExecutionContext context) {
        requireEpisode(context);
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, episode_no, title, summary, content, status
              from script_episode
             where tenant_id = ? and project_id = ? and id = ? and retired_at is null
            """, context.tenantId(), context.projectId(), context.episodeId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧集不存在。");
        }
        return episodeDetail(rows.get(0));
    }

    public JsonNode readProjectFullScript(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        ObjectNode result = json.createObjectNode();
        ArrayNode episodes = result.putArray("episodes");
        jdbc.queryForList("""
            select id, episode_no, title, summary, content, status
              from script_episode
             where tenant_id = ? and project_id = ? and retired_at is null
             order by episode_no, id
            """, context.tenantId(), context.projectId())
            .forEach(row -> episodes.add(episodeDetail(row)));
        return result;
    }

    public JsonNode readAdjacentEpisodes(ToolExecutionContext context) {
        requireEpisode(context);
        JsonNode current = readEpisodeScript(context);
        int episodeNo = current.path("episodeNo").asInt();
        ObjectNode result = json.createObjectNode();
        addAdjacent(result, "previous", context, episodeNo, "<", "desc");
        addAdjacent(result, "next", context, episodeNo, ">", "asc");
        return result;
    }

    public JsonNode readScriptAnalysis(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        ObjectNode result = json.createObjectNode();
        List<Map<String, Object>> tasks = jdbc.queryForList("""
            select id, status, overall_progress, current_stage, current_action,
                   error_code, error_message, created_at, updated_at
              from script_analysis_task
             where tenant_id = ? and project_id = ?
             order by created_at desc limit 1
            """, context.tenantId(), context.projectId());
        if (tasks.isEmpty()) {
            result.putNull("task");
            result.putArray("stages");
            return result;
        }
        Map<String, Object> task = tasks.get(0);
        result.set("task", json.valueToTree(task));
        ArrayNode stages = result.putArray("stages");
        jdbc.queryForList("""
            select stage.id, stage.stage_code, stage.status, stage.stage_order,
                   stage.progress_percent, stage.current_action,
                   result.normalized_json, result.raw_response, result.error_code, result.error_message
              from script_analysis_stage stage
              left join script_analysis_result result on result.id = (
                select max(latest.id) from script_analysis_result latest where latest.stage_id = stage.id)
             where stage.task_id = ? order by stage.stage_order
            """, number(task.get("id"))).forEach(row -> stages.add(json.valueToTree(row)));
        return result;
    }

    public JsonNode readScriptAssets(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        ObjectNode result = json.createObjectNode();
        result.set("characters", assets("character_asset", context));
        result.set("scenes", assets("scene_asset", context));
        result.set("props", assets("prop_asset", context));
        return result;
    }

    public JsonNode readCurrentScript(ToolExecutionContext context) {
        requireScript(context, "SCRIPT:VIEW");
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select content, updated_at from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, context.tenantId(), context.projectId(), context.scriptId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前剧本不存在。");
        }
        String content = String.valueOf(rows.get(0).getOrDefault("content", ""));
        if (content.length() > 2_000_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前剧本超过 Agent 可读取的长度限制。");
        }
        String hash = sha256(content);
        context.runState().put("currentScriptHash", hash);
        GlobalUnderstandingProgress analyzing = GlobalUnderstandingProgress.analyzing();
        updateAnalysisProgress(context, analyzing.percent(), analyzing.action());
        ObjectNode result = json.createObjectNode();
        result.put("content", content);
        result.put("contentHash", hash);
        Object updatedAt = rows.get(0).get("updated_at");
        if (updatedAt == null) {
            result.putNull("updatedAt");
        } else {
            result.put("updatedAt", String.valueOf(updatedAt));
        }
        return result;
    }

    public JsonNode readScriptStructure(ToolExecutionContext context) {
        requireScript(context, "SCRIPT:VIEW");
        if (context.agentRunId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信 Agent Run 作用域。");
        }
        List<String> rows = jdbc.queryForList("""
            select content from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
            """, String.class, context.tenantId(), context.projectId(), context.scriptId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前剧本不存在。");
        }
        String source = rows.get(0);
        String contentHash = sha256(source);
        var plans = splitChunkPlanner.plan(source,
            new ScriptSplitChunkPlanner.ChunkSettings(15_000, 20_000, 24_000, 1_500));
        var seeds = plans.stream().map(plan -> new ScriptSplitSnapshotStore.SplitChunkSeed(
            plan.chunkNo(), plan.coreStart(), plan.coreEnd(), plan.contextStart(), plan.contextEnd(),
            sha256(source.substring(plan.contextStart(), plan.contextEnd())))).toList();
        var scope = new ScriptSplitSnapshotStore.SplitScope(
            context.tenantId(), context.projectId(), context.scriptId(), context.agentRunId());
        splitSnapshotStore.markStaleForDifferentHash(scope, contentHash);
        long snapshotId = splitSnapshotStore.createOrResume(
            scope, contentHash, "RUNTIME_FALLBACK", "structure-v1", seeds);
        String snapshotKey = sha256(context.agentRunId() + ":" + snapshotId + ":" + contentHash);
        context.runState().put("splitSnapshotId", snapshotId);
        context.runState().put("splitContentHash", contentHash);
        context.runState().put("splitSnapshotKey", snapshotKey);

        ObjectNode result = json.createObjectNode();
        result.put("contentHash", contentHash);
        result.put("snapshotKey", snapshotKey);
        result.put("totalChunks", plans.size());
        ArrayNode chunks = result.putArray("chunks");
        ArrayNode anchors = result.putArray("anchors");
        for (var plan : plans) {
            ObjectNode chunk = chunks.addObject();
            chunk.put("chunkNo", plan.chunkNo());
            chunk.put("coreStart", plan.coreStart());
            chunk.put("coreEnd", plan.coreEnd());
            chunk.put("boundarySignal", plan.boundarySignal());
            for (var anchor : plan.anchors()) {
                ObjectNode value = anchors.addObject();
                value.put("offset", anchor.offset());
                value.put("marker", anchor.marker());
                value.put("signal", anchor.signal());
            }
        }
        return result;
    }

    public JsonNode analyzeScriptChunks(ToolExecutionContext context) {
        long snapshotId = context.runState().require("splitSnapshotId", Long.class);
        ScriptSplitSnapshotStore.SplitSnapshot snapshot = splitSnapshotStore.require(snapshotId);
        ObjectNode result = json.createObjectNode();
        result.put("total", snapshot.total());
        result.put("completed", snapshot.completed());
        result.put("failed", snapshot.failed());
        result.putArray("candidates");
        result.putArray("anchors");
        result.putArray("aiCallLogIds");
        return result;
    }

    public JsonNode readCurrentEpisode(ToolExecutionContext context) {
        requireEpisode(context);
        if (context.scriptId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信剧本作用域。");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, stable_key, episode_no, title, content, content_fingerprint
              from script_episode
             where tenant_id = ? and project_id = ? and script_id = ? and id = ?
               and status = 'ACTIVE' and retired_at is null
            """, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前正式剧集不存在或已退役。");
        }
        Map<String, Object> row = rows.get(0);
        String content = String.valueOf(row.getOrDefault("content", ""));
        if (content.length() > 200_000) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前剧集内容过长，无法交给 Agent 处理。");
        }
        String fingerprint = String.valueOf(row.getOrDefault("content_fingerprint", sha256(content)));
        context.runState().put("currentEpisodeId", context.episodeId());
        context.runState().put("currentEpisodeScriptId", context.scriptId());
        context.runState().put("currentEpisodeFingerprint", fingerprint);
        context.runState().put("currentEpisodeContentHash", sha256(content));
        ObjectNode result = json.createObjectNode();
        put(result, "episodeKey", row.get("stable_key"));
        result.put("episodeNo", ((Number) row.get("episode_no")).intValue());
        put(result, "title", row.get("title"));
        result.put("content", content);
        result.put("contentFingerprint", fingerprint);
        ObjectNode catalog = result.putObject("assetCatalog");
        catalog.set("characters", currentScriptAssets(context, "character_asset", "CHARACTER", "c_"));
        catalog.set("scenes", currentScriptAssets(context, "scene_asset", "SCENE", "s_"));
        catalog.set("props", currentScriptAssets(context, "prop_asset", "PROP", "p_"));
        return result;
    }

    private ArrayNode currentScriptAssets(
        ToolExecutionContext context,
        String table,
        String assetType,
        String keyPrefix
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select id, name, normalized_name, content_json from " + table
                + " where tenant_id = ? and project_id = ? and script_id = ? and deleted_at is null"
                + " order by id limit 201",
            context.tenantId(), context.projectId(), context.scriptId());
        if (rows.size() > 200) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前剧本资产目录过大，无法交给 Agent 处理。");
        }
        ArrayNode values = json.createArrayNode();
        for (Map<String, Object> row : rows) {
            long assetId = number(row.get("id"));
            ObjectNode item = values.addObject();
            item.put("assetKey", keyPrefix + assetId);
            put(item, "name", row.get("name"));
            put(item, "normalizedName", row.get("normalized_name"));
            ArrayNode aliasValues = item.putArray("aliases");
            Object rawAssetContent = row.get("content_json");
            if (rawAssetContent != null) {
                try {
                    JsonNode storedAliases = json.readTree(String.valueOf(rawAssetContent)).path("aliases");
                    for (JsonNode alias : storedAliases) {
                        String aliasName = alias.isTextual() ? alias.asText() : alias.path("name").asText();
                        if (!aliasName.isBlank()) aliasValues.add(aliasName);
                    }
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalStateException("资产元数据损坏。", exception);
                }
            }
            ArrayNode variants = item.putArray("variants");
            List<Map<String, Object>> variantRows = jdbc.queryForList("""
                select id, name, content_json
                  from asset_visual_variant
                 where tenant_id = ? and project_id = ? and asset_type = ? and asset_id = ?
                   and deleted_at is null
                 order by id limit 51
                """, context.tenantId(), context.projectId(), assetType, assetId);
            if (variantRows.size() > 50) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前剧本资产形态目录过大，无法交给 Agent 处理。");
            }
            for (Map<String, Object> variant : variantRows) {
                ObjectNode variantItem = variants.addObject();
                variantItem.put("variantKey", "v_" + number(variant.get("id")));
                put(variantItem, "name", variant.get("name"));
                Object rawContent = variant.get("content_json");
                if (rawContent != null) {
                    try {
                        variantItem.set("content", json.readTree(String.valueOf(rawContent)));
                    } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        throw new IllegalStateException("资产形态元数据损坏。", exception);
                    }
                }
            }
        }
        return values;
    }

    @Transactional
    public JsonNode saveGlobalUnderstanding(
        ToolExecutionContext context,
        int schemaVersion,
        JsonNode content
    ) {
        context.requireBeforeDeadline();
        requireScript(context, "SCRIPT:EDIT");
        String expectedHash;
        try {
            expectedHash = context.runState().require("currentScriptHash", String.class);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "保存前必须先读取当前剧本。");
        }
        GlobalUnderstandingProgress saving = GlobalUnderstandingProgress.saving();
        progressWriter.update(context, saving.percent(), saving.action());
        List<Map<String, Object>> scripts = jdbc.queryForList("""
            select content from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
             for update
            """, context.tenantId(), context.projectId(), context.scriptId());
        if (scripts.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前剧本不存在。");
        }
        String actualHash = sha256(String.valueOf(scripts.get(0).getOrDefault("content", "")));
        if (!expectedHash.equals(actualHash)) {
            throw new BusinessException(ErrorCode.SCRIPT_CONTENT_CHANGED,
                "剧本在分析过程中发生变化，请重新读取后再分析。");
        }
        long id = globalUnderstanding.upsert(new ScriptGlobalUnderstandingDocument(
            null, context.tenantId(), context.projectId(), context.scriptId(), schemaVersion,
            content.deepCopy(), actualHash, context.agentRunId(), context.userId(), context.userId(),
            null, null));
        String stageStatus = null;
        if (context.analysisStageId() != null) {
            GlobalUnderstandingProgress committed = GlobalUnderstandingProgress.committed();
            int updated = jdbc.update("""
                update script_analysis_stage
                   set status = 'SUCCEEDED', progress_percent = ?, completed_units = 1,
                       total_units = 1, current_action = ?, retryable = false,
                       error_code = null, error_message = null, finished_at = now(), updated_at = now()
                 where id = ? and task_id = ? and stage_code = 'GLOBAL_UNDERSTANDING'
                """, committed.percent(), committed.action(), context.analysisStageId(), context.taskId());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧情全局理解阶段不匹配。");
            }
            jdbc.update("""
                insert into script_analysis_result
                  (task_id, stage_id, result_type, schema_version, status, raw_response,
                   normalized_json, created_at, updated_at)
                values (?, ?, 'GLOBAL_UNDERSTANDING', ?, 'SUCCEEDED', ?, ?, now(), now())
                """, context.taskId(), context.analysisStageId(), String.valueOf(schemaVersion),
                content.toString(), content.toString());
            stageStatus = "SUCCEEDED";
        }
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("globalUnderstandingId", id);
        result.put("scriptId", context.scriptId());
        result.put("contentHash", actualHash);
        if (stageStatus == null) {
            result.putNull("stageStatus");
        } else {
            result.put("stageStatus", stageStatus);
        }
        return result;
    }

    @Transactional
    public JsonNode saveEpisodeSplitting(
        ToolExecutionContext context,
        int schemaVersion,
        JsonNode episodeBoundaries
    ) {
        context.requireBeforeDeadline();
        requireScript(context, "SCRIPT:EDIT");
        String expectedHash;
        try {
            expectedHash = context.runState().require("currentScriptHash", String.class);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "保存分集前必须先读取当前剧本。");
        }
        List<Map<String, Object>> scripts = jdbc.queryForList("""
            select content, current_version_id from script
             where tenant_id = ? and project_id = ? and id = ? and deleted_at is null
             for update
            """, context.tenantId(), context.projectId(), context.scriptId());
        if (scripts.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前剧本不存在。");
        }
        Map<String, Object> script = scripts.get(0);
        String source = String.valueOf(script.getOrDefault("content", ""));
        String actualHash = sha256(source);
        if (!expectedHash.equals(actualHash)) {
            throw new BusinessException(ErrorCode.SCRIPT_CONTENT_CHANGED,
                "剧本在分集过程中发生变化，请重新读取后再分析。");
        }
        List<EpisodeSplitBoundaryResolver.Boundary> boundaries = new java.util.ArrayList<>();
        episodeBoundaries.forEach(item -> boundaries.add(new EpisodeSplitBoundaryResolver.Boundary(
            item.path("title").asText(), item.path("startMarker").asText(), item.path("endMarker").asText())));
        List<ScriptEpisodeResponse> extracted;
        try {
            extracted = splitBoundaryResolver.resolve(source, boundaries);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, exception.getMessage());
        }
        Object currentVersion = script.get("current_version_id");
        Long scriptVersionId = currentVersion instanceof Number number ? number.longValue() : null;
        List<ScriptEpisodeResponse> saved = episodeService.reconcileAndPersist(
            context.tenantId(), context.projectId(), context.scriptId(), scriptVersionId,
            context.agentRunId(), extracted);

        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("schemaVersion", schemaVersion);
        snapshot.put("contentHash", actualHash);
        ArrayNode snapshotEpisodes = snapshot.putArray("episodes");
        saved.forEach(episode -> snapshotEpisodes.addObject()
            .put("episodeId", episode.episodeId())
            .put("episodeNo", episode.episodeNo())
            .put("title", episode.title()));
        Long resultId = null;
        String stageStatus = null;
        if (context.analysisStageId() != null) {
            int updated = jdbc.update("""
                update script_analysis_stage
                   set status = 'SUCCEEDED', progress_percent = 100, completed_units = 1,
                       total_units = 1, current_action = '剧集智能拆分已保存', retryable = false,
                       error_code = null, error_message = null, finished_at = now(), updated_at = now()
                 where id = ? and task_id = ? and stage_code = 'EPISODE_SPLITTING'
                """, context.analysisStageId(), context.taskId());
            if (updated != 1) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧集智能拆分阶段不匹配。");
            }
            jdbc.update("""
                insert into script_analysis_result
                  (task_id, stage_id, result_type, schema_version, status, raw_response,
                   normalized_json, created_at, updated_at)
                values (?, ?, 'EPISODE_SPLITTING', ?, 'SUCCEEDED', ?, ?, now(), now())
                """, context.taskId(), context.analysisStageId(), String.valueOf(schemaVersion),
                episodeBoundaries.toString(), snapshot.toString());
            resultId = jdbc.queryForObject(
                "select max(id) from script_analysis_result where task_id = ? and stage_id = ?",
                Long.class, context.taskId(), context.analysisStageId());
            stageStatus = "SUCCEEDED";
        }
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("scriptId", context.scriptId());
        result.put("contentHash", actualHash);
        result.put("episodeCount", saved.size());
        result.set("episodes", snapshotEpisodes.deepCopy());
        if (resultId == null) result.putNull("resultId"); else result.put("resultId", resultId);
        if (stageStatus == null) result.putNull("stageStatus"); else result.put("stageStatus", stageStatus);
        return result;
    }

    @Transactional
    public JsonNode saveEpisodeSummary(
        ToolExecutionContext context,
        int schemaVersion,
        String summary,
        JsonNode highlights,
        JsonNode endingHook
    ) {
        context.requireBeforeDeadline();
        requireScript(context, "SCRIPT:EDIT");
        Long expectedEpisodeId;
        String expectedFingerprint;
        String expectedContentHash;
        try {
            expectedEpisodeId = context.runState().require("currentEpisodeId", Long.class);
            expectedFingerprint = context.runState().require("currentEpisodeFingerprint", String.class);
            expectedContentHash = context.runState().require("currentEpisodeContentHash", String.class);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REQUIRED_TOOL_NOT_CALLED,
                "保存概要前必须先读取当前剧集。");
        }
        if (!expectedEpisodeId.equals(context.episodeId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "读取剧集与保存作用域不一致。");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select stable_key, content, content_fingerprint from script_episode
             where id = ? and tenant_id = ? and project_id = ? and script_id = ?
               and status = 'ACTIVE' and retired_at is null for update
            """, context.episodeId(), context.tenantId(), context.projectId(), context.scriptId());
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "当前正式剧集不存在或已退役。");
        }
        Map<String, Object> episode = rows.get(0);
        String actualFingerprint = String.valueOf(episode.get("content_fingerprint"));
        String actualContentHash = sha256(String.valueOf(episode.getOrDefault("content", "")));
        if (!expectedFingerprint.equals(actualFingerprint) || !expectedContentHash.equals(actualContentHash)) {
            throw new BusinessException(ErrorCode.EPISODE_CONTENT_CHANGED,
                "剧集在概要生成过程中发生变化，请重新读取后再分析。");
        }
        ObjectNode content = json.createObjectNode();
        content.put("summary", summary);
        content.set("highlights", highlights.deepCopy());
        if (endingHook == null || endingHook.isNull()) {
            content.putNull("endingHook");
        } else {
            content.put("endingHook", endingHook.asText());
        }
        long summaryId = episodeSummaries.upsert(new ScriptEpisodeSummaryDocument(
            null, context.tenantId(), context.projectId(), context.scriptId(), context.episodeId(),
            schemaVersion, content, "AI", context.agentRunId(), context.userId(), context.userId(),
            null, null));
        jdbc.update("update script_episode set summary = ?, updated_at = now() where id = ?",
            summary, context.episodeId());
        ObjectNode result = json.createObjectNode();
        result.put("saved", true);
        result.put("summaryId", summaryId);
        put(result, "episodeKey", episode.get("stable_key"));
        result.put("contentFingerprint", actualFingerprint);
        return result;
    }

    public JsonNode saveEpisodeAssets(ToolExecutionContext context, JsonNode payload) {
        context.requireBeforeDeadline();
        requireScript(context, "SCRIPT:EDIT");
        return episodeAssets.save(context, payload);
    }

    private void updateAnalysisProgress(
        ToolExecutionContext context,
        int progress,
        String action
    ) {
        if (context.analysisStageId() == null || context.taskId() == null) {
            return;
        }
        jdbc.update("""
            update script_analysis_stage
               set progress_percent = ?, current_action = ?, updated_at = now()
             where id = ? and task_id = ? and stage_code = 'GLOBAL_UNDERSTANDING'
            """, progress, action, context.analysisStageId(), context.taskId());
        jdbc.update("""
            update script_analysis_task
               set current_action = ?, overall_progress = ?, updated_at = now()
             where id = ?
            """, action, Math.max(1, progress / 4), context.taskId());
    }

    public JsonNode validateScreenplayFormat(String content) {
        ObjectNode result = json.createObjectNode();
        ArrayNode errors = result.putArray("errors");
        String normalized = content == null ? "" : content.replace("\r\n", "\n");
        if (!SCENE_HEADING.matcher(normalized).find()) {
            errors.add("缺少合法场景头：## S编号 | 内景/外景 · 地点 | 时间段");
        }
        if (!DIALOGUE.matcher(normalized).find()) {
            errors.add("缺少合法对白：角色名：（状态/表情）台词内容");
        }
        result.put("valid", errors.isEmpty());
        return result;
    }

    @Transactional
    public JsonNode saveEpisodeScript(ToolExecutionContext context, String content) {
        context.requireBeforeDeadline();
        requireProject(context, "SCRIPT:EDIT");
        if (context.episodeId() == null || content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "剧集和剧本内容不能为空。");
        }
        JsonNode format = validateScreenplayFormat(content);
        if (!format.path("valid").asBoolean()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                "剧本格式校验失败：" + format.path("errors"));
        }
        List<Long> episodes = jdbc.queryForList("""
            select id from script_episode
             where tenant_id = ? and project_id = ? and id = ? and retired_at is null
             for update
            """, Long.class, context.tenantId(), context.projectId(), context.episodeId());
        if (episodes.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "剧集不存在。");
        }
        Integer nextVersion = jdbc.queryForObject("""
            select coalesce(max(version_no), 0) + 1 from script_episode_version where episode_id = ?
            """, Integer.class, context.episodeId());
        jdbc.update("""
            update script_episode_version set is_current = false
             where episode_id = ? and is_current = true
            """, context.episodeId());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            java.sql.PreparedStatement statement = connection.prepareStatement("""
                insert into script_episode_version
                  (tenant_id, project_id, episode_id, version_no, content, status, is_current,
                   created_by, created_at)
                values (?, ?, ?, ?, ?, 'ACTIVE', true, ?, now())
                """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, context.tenantId());
            statement.setLong(2, context.projectId());
            statement.setLong(3, context.episodeId());
            statement.setInt(4, nextVersion);
            statement.setString(5, content);
            statement.setLong(6, context.userId());
            return statement;
        }, key);
        Object generatedId = key.getKeys() == null ? null : key.getKeys().get("id");
        if (!(generatedId instanceof Number number)) {
            throw new IllegalStateException("Episode script version id was not generated");
        }
        long versionId = number.longValue();
        context.requireBeforeDeadline();
        currentSelector.selectCurrent(context.tenantId(), context.projectId(), context.episodeId(),
            versionId, content);
        ObjectNode result = json.createObjectNode();
        result.put("episodeId", context.episodeId());
        result.put("versionId", versionId);
        result.put("versionNo", nextVersion);
        result.put("current", true);
        return result;
    }

    private ArrayNode assets(String table, ToolExecutionContext context) {
        ArrayNode items = json.createArrayNode();
        jdbc.queryForList("select id, name, status from " + table
            + " where tenant_id = ? and project_id = ? and deleted_at is null order by id",
            context.tenantId(), context.projectId()).forEach(row -> items.add(json.valueToTree(row)));
        return items;
    }

    private void addAdjacent(
        ObjectNode result,
        String field,
        ToolExecutionContext context,
        int episodeNo,
        String operator,
        String direction
    ) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select id, episode_no, title, summary, content, status
              from script_episode
             where tenant_id = ? and project_id = ? and retired_at is null
               and episode_no %s ?
             order by episode_no %s limit 1
            """.formatted(operator, direction), context.tenantId(), context.projectId(), episodeNo);
        if (rows.isEmpty()) {
            result.putNull(field);
        } else {
            result.set(field, episodeDetail(rows.get(0)));
        }
    }

    private ObjectNode episodeSummary(Map<String, Object> row) {
        ObjectNode item = json.createObjectNode();
        item.put("episodeId", number(row.get("id")));
        item.put("episodeNo", number(row.get("episode_no")));
        put(item, "title", row.get("title"));
        put(item, "summary", row.get("summary"));
        put(item, "status", row.get("status"));
        return item;
    }

    private ObjectNode episodeDetail(Map<String, Object> row) {
        ObjectNode item = episodeSummary(row);
        put(item, "content", row.get("content"));
        return item;
    }

    private void requireProject(ToolExecutionContext context, String permission) {
        if (context == null || context.tenantId() == null || context.userId() == null
            || context.projectId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信执行作用域。");
        }
        boolean trustedBackgroundRun = context.agentRunId() != null
            && context.analysisStageId() != null
            && context.taskId() != null;
        if (!trustedBackgroundRun || !context.permissions().contains(permission)) {
            permissionGuard.require(context.tenantId(), context.projectId(), permission);
        }
    }

    private void requireEpisode(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        if (context.episodeId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信剧集作用域。");
        }
    }

    private void requireScript(ToolExecutionContext context, String permission) {
        requireProject(context, permission);
        if (context.scriptId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信剧本作用域。");
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void put(ObjectNode target, String field, Object value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, String.valueOf(value));
        }
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
