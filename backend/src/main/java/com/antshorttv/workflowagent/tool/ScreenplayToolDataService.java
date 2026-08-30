package com.antshorttv.workflowagent.tool;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    public ScreenplayToolDataService(
        JdbcTemplate jdbc,
        ProjectPermissionGuard permissionGuard,
        ObjectMapper json,
        EpisodeScriptCurrentSelector currentSelector
    ) {
        this.jdbc = jdbc;
        this.permissionGuard = permissionGuard;
        this.json = json;
        this.currentSelector = currentSelector;
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
        permissionGuard.require(context.tenantId(), context.projectId(), permission);
    }

    private void requireEpisode(ToolExecutionContext context) {
        requireProject(context, "SCRIPT:VIEW");
        if (context.episodeId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少可信剧集作用域。");
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
