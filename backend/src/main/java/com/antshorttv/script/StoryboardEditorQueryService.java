package com.antshorttv.script;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.material.MaterialFileAccessService;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Focused read model; full workspaces and the stored generation plans remain unchanged. */
@Service
class StoryboardEditorQueryService {
    private static final List<String> PLAN_FIELDS = List.of(
        "storyboardNo", "durationSeconds", "time", "lighting", "shots", "warnings", "diagnostics");
    private static final List<String> DIAGNOSTIC_FIELDS = List.of(
        "normalizationCount", "derivedSoundCount", "actionWarnings", "classificationWarnings",
        "businessCallCount", "technicalRetryCount");
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final ScriptEpisodeSummaryRepository summaries;
    private final StoryboardAssetReferenceRepository references;
    private final MaterialFileAccessService files;
    private final ObjectMapper json;
    private volatile Boolean mysql;

    StoryboardEditorQueryService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        ScriptEpisodeSummaryRepository summaries, StoryboardAssetReferenceRepository references,
        MaterialFileAccessService files, ObjectMapper json) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.summaries = summaries;
        this.references = references;
        this.files = files;
        this.json = json;
    }

    StoryboardWorkspacePageResponse page(Long tenantId, Long projectId, Integer episodeNo,
        Integer current, Integer pageSize) {
        return page(tenantId, projectId, episodeNo, current, pageSize, null);
    }

    StoryboardWorkspacePageResponse page(Long tenantId, Long projectId, Integer episodeNo,
        Integer current, Integer pageSize, Long storyboardId) {
        permissions.require(tenantId, projectId, "PROJECT:VIEW");
        Long scriptId = currentScriptId(tenantId, projectId);
        List<ScriptEpisodeSummaryResponse> episodes = scriptId == null ? List.of() : jdbc.query("""
            select id, episode_no, title, content_fingerprint, generated_by_run_id from script_episode
             where tenant_id=? and project_id=? and script_id=? and retired_at is null order by episode_no,id
            """, (rs, n) -> new ScriptEpisodeSummaryResponse(rs.getLong("id"), rs.getInt("episode_no"),
                rs.getString("title"), null, rs.getString("content_fingerprint"),
                rs.getObject("generated_by_run_id", Long.class), null), tenantId, projectId, scriptId);
        // Only legacy projects without persisted episodes need the original body parser.
        if (scriptId != null && episodes.isEmpty()) {
            String content = jdbc.queryForObject("select content from script where tenant_id=? and project_id=? and id=?",
                String.class, tenantId, projectId, scriptId);
            episodes = ScriptEpisodeParser.parse(content).stream().map(ScriptEpisodeSummaryResponse::from).toList();
        }
        int requestedEpisode = episodeNo == null || episodeNo < 1
            ? episodes.stream().findFirst().map(ScriptEpisodeSummaryResponse::episodeNo).orElse(1) : episodeNo;
        int selected = storyboardId == null ? requestedEpisode
            : targetEpisode(tenantId, projectId, scriptId, episodes, storyboardId, requestedEpisode);
        int safeCurrent = current == null || current < 1 ? 1 : current;
        int safeSize = pageSize == null || pageSize < 1 ? 20 : Math.min(100, pageSize);
        var selectedEpisode = episodes.stream().filter(item -> item.episodeNo() == selected).findFirst().orElse(null);
        if (scriptId == null || (!episodes.isEmpty() && selectedEpisode == null)) {
            return new StoryboardWorkspacePageResponse(projectId, episodes, selected, safeCurrent, safeSize, 0L, List.of(), List.of());
        }
        Long episodeId = selectedEpisode == null ? null : selectedEpisode.episodeId();
        List<StoryboardNavigationResponse> navigation = navigation(tenantId, projectId, scriptId, episodeId, selected);
        if (storyboardId != null) {
            for (int index = 0; index < navigation.size(); index++) {
                if (storyboardId.equals(navigation.get(index).id())) {
                    safeCurrent = index / safeSize + 1;
                    break;
                }
            }
        }
        Long total = (long) navigation.size();
        List<StoryboardResponse> page = jdbc.query(pageSql(isMysql()), (rs, n) -> new StoryboardResponse(
            rs.getLong("id"), projectId, rs.getInt("shot_no"), rs.getInt("storyboard_no"),
            rs.getObject("episode_id", Long.class), selected, rs.getString("shot_type"),
            rs.getString("visual_description"), rs.getString("characters"), rs.getString("scene"),
            rs.getString("props"), rs.getString("dialogue"), rs.getObject("duration_seconds", Integer.class),
            projectPlan(readJson(rs.getString("editor_plan"))), readJson(rs.getString("prompt_document_json")),
            rs.getString("material_binding_status"), rs.getString("source_fingerprint"),
            rs.getObject("generated_by_run_id", Long.class), rs.getString("image_prompt"), rs.getString("video_prompt"),
            files.publicUrl(rs.getString("first_frame_url")), rs.getObject("current_video_result_id", Long.class),
            files.publicUrl(rs.getString("current_video_url")), rs.getObject("current_shot_result_id", Long.class),
            files.publicUrl(rs.getString("current_shot_video_url")), List.of(), readJson(rs.getString("video_generation_settings_json"))),
            tenantId, projectId, scriptId, episodeId, selected, safeSize, ((long) safeCurrent - 1) * safeSize);
        Map<Long, List<StoryboardAssetReferenceResponse>> byShot = references.listResponsesForStoryboards(
            tenantId, projectId, page.stream().map(StoryboardResponse::id).toList());
        return new StoryboardWorkspacePageResponse(projectId, episodes, selected, safeCurrent, safeSize,
            total == null ? 0 : total, page.stream().map(shot -> shot.withAssetReferences(
                byShot.getOrDefault(shot.id(), List.of())).forBrowser()).toList(), navigation);
    }

    ScriptEpisodeResponse episodeDetails(Long tenantId, Long projectId, Long episodeId) {
        permissions.require(tenantId, projectId, "PROJECT:VIEW");
        Long scriptId = currentScriptId(tenantId, projectId);
        List<ScriptEpisodeResponse> rows = jdbc.query("""
            select id,episode_no,title,content_fingerprint,generated_by_run_id from script_episode
             where tenant_id=? and project_id=? and script_id=? and id=? and retired_at is null
            """, (rs, n) -> new ScriptEpisodeResponse(rs.getLong("id"), rs.getInt("episode_no"),
                rs.getString("title"), null, null, rs.getString("content_fingerprint"),
                rs.getObject("generated_by_run_id", Long.class), null), tenantId, projectId, scriptId, episodeId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "剧集不存在。");
        var row = rows.get(0);
        var summary = summaries.findCurrent(tenantId, scriptId, episodeId).orElse(null);
        return new ScriptEpisodeResponse(row.episodeId(), row.episodeNo(), row.title(), null,
            ScriptEpisodeSummaryDocument.summaryText(summary), row.contentFingerprint(), row.generatedByRunId(), summary);
    }

    private int targetEpisode(Long tenantId, Long projectId, Long scriptId,
        List<ScriptEpisodeSummaryResponse> episodes, Long storyboardId, int fallback) {
        var targets = jdbc.query("""
            select episode_id,episode_no from storyboard where tenant_id=? and project_id=?
              and script_id=? and id=? and deleted_at is null
            """, (rs, n) -> new Long[] {rs.getObject("episode_id",Long.class), rs.getLong("episode_no")},
            tenantId, projectId, scriptId, storyboardId);
        if (targets.isEmpty()) return fallback;
        Long[] target = targets.get(0);
        return episodes.stream().filter(episode -> target[0] == null
                ? episode.episodeNo().longValue() == target[1] : target[0].equals(episode.episodeId()))
            .findFirst().map(ScriptEpisodeSummaryResponse::episodeNo).orElse(fallback);
    }

    private List<StoryboardNavigationResponse> navigation(Long tenantId, Long projectId, Long scriptId,
        Long episodeId, int episodeNo) {
        return jdbc.query("""
            select s.id,s.shot_no,coalesce(s.storyboard_no,s.shot_no) storyboard_no,
                   (nullif(trim(s.current_video_url),'') is not null or exists (
                       select 1 from ai_video_result r join ai_video_task t on t.id=r.task_id
                         and t.tenant_id=r.tenant_id and t.project_id=r.project_id and t.deleted_at is null
                        where r.storyboard_id=s.id and r.tenant_id=s.tenant_id and r.project_id=s.project_id
                          and r.status='ACTIVE'
                   )) has_video
              from storyboard s where s.tenant_id=? and s.project_id=? and s.deleted_at is null
                and s.script_id=? and (s.episode_id=? or (s.episode_id is null and s.episode_no=?))
             order by s.shot_no,s.id
            """, (rs,n) -> new StoryboardNavigationResponse(rs.getLong("id"),rs.getInt("shot_no"),
                rs.getInt("storyboard_no"),rs.getBoolean("has_video")),tenantId,projectId,scriptId,episodeId,episodeNo);
    }

    private Long currentScriptId(Long tenantId, Long projectId) {
        return jdbc.queryForList("""
            select id from script where tenant_id=? and project_id=? and deleted_at is null order by updated_at desc limit 1
            """, Long.class, tenantId, projectId).stream().findFirst().orElse(null);
    }

    private boolean isMysql() {
        if (mysql == null) {
            mysql = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) connection -> {
                String product = connection.getMetaData().getDatabaseProductName();
                return "MySQL".equalsIgnoreCase(product) || "MariaDB".equalsIgnoreCase(product);
            }));
        }
        return mysql;
    }

    static String pageSql(boolean mysql) {
        String plan = "shot_plan_json";
        if (mysql) {
            String fields = PLAN_FIELDS.stream().map(field -> "'" + field + "',JSON_EXTRACT(shot_plan_json,'$." + field + "')")
                .collect(java.util.stream.Collectors.joining(","));
            String diagnostics = DIAGNOSTIC_FIELDS.stream().map(field -> "'" + field
                + "',JSON_EXTRACT(shot_plan_json,'$.diagnostics." + field + "')")
                .collect(java.util.stream.Collectors.joining(","));
            fields = fields.replace("JSON_EXTRACT(shot_plan_json,'$.diagnostics')", "IF(JSON_TYPE(JSON_EXTRACT(shot_plan_json,'$.diagnostics'))='OBJECT',JSON_OBJECT(" + diagnostics + "),NULL)");
            plan = "CASE WHEN JSON_VALID(shot_plan_json) THEN JSON_OBJECT(" + fields + ") ELSE NULL END";
        }
        return """
            select id,shot_no,coalesce(storyboard_no,shot_no) storyboard_no,episode_id,episode_no,shot_type,
                   visual_description,characters,scene,props,dialogue,duration_seconds,%s editor_plan,
                   prompt_document_json,material_binding_status,source_fingerprint,generated_by_run_id,
                   image_prompt,video_prompt,first_frame_url,current_video_result_id,current_video_url,
                   current_shot_result_id,current_shot_video_url,video_generation_settings_json
              from storyboard where tenant_id=? and project_id=? and deleted_at is null
               and script_id=? and (episode_id=? or (episode_id is null and episode_no=?))
             order by shot_no,id limit ? offset ?
            """.formatted(plan);
    }

    private JsonNode projectPlan(JsonNode plan) {
        if (plan == null || !plan.isObject()) return null;
        ObjectNode result = json.createObjectNode();
        for (String field : PLAN_FIELDS) if (plan.has(field)) result.set(field, plan.get(field));
        if (result.path("diagnostics").isObject()) {
            ObjectNode diagnostics = json.createObjectNode();
            for (String field : DIAGNOSTIC_FIELDS) {
                JsonNode value = result.path("diagnostics").get(field);
                if (value != null && !value.isNull()) diagnostics.set(field, value);
                else if (field.endsWith("Warnings")) diagnostics.putArray(field);
                else diagnostics.put(field, 0);
            }
            result.set("diagnostics", diagnostics);
        }
        return result;
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return json.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("分镜结构化数据损坏。", failure);
        }
    }
}
