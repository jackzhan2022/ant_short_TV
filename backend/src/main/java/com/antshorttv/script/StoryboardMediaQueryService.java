package com.antshorttv.script;

import com.antshorttv.project.ProjectAccessResolver;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class StoryboardMediaQueryService {
    private final JdbcTemplate jdbc;
    private final ProjectAccessResolver access;

    public StoryboardMediaQueryService(JdbcTemplate jdbc, ProjectAccessResolver access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    public Map<String, List<Map<String, Object>>> summary(Long tenantId, Long projectId, List<Long> storyboardIds) {
        var context = access.requireView(tenantId, projectId);
        if (storyboardIds == null || storyboardIds.size() > 100
            || storyboardIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "分镜媒体请求最多包含 100 个有效分镜。");
        }
        List<Long> ids = storyboardIds.stream().distinct().toList();
        if (ids.isEmpty()) return Map.of("imageTasks", List.of(), "videoTasks", List.of(), "voiceTasks", List.of());
        List<Object> scope = scopedParameters(tenantId, projectId, ids);
        List<Map<String, Object>> shots = jdbc.queryForList(
            "select id, episode_id, characters, scene, props, first_frame_image_id, current_video_result_id, current_voice_result_id from storyboard "
                + "where tenant_id = ? and project_id = ? and deleted_at is null and id in (" + placeholders(ids.size()) + ")",
            scope.toArray()
        );
        if (shots.size() != ids.size()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "分镜不存在或不属于当前项目。");
        }
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        var permissions = context.effectivePermissions();
        result.put("imageTasks", permissions.contains("AI_IMAGE_TASK:VIEW")
            ? tasks(tenantId, projectId, ids, shots, "image", "first_frame_image_id") : List.of());
        result.put("videoTasks", permissions.contains("AI_VIDEO_TASK:VIEW")
            ? tasks(tenantId, projectId, ids, shots, "video", "current_video_result_id") : List.of());
        result.put("voiceTasks", permissions.contains("AI_VOICE_TASK:VIEW")
            ? tasks(tenantId, projectId, ids, shots, "voice", "current_voice_result_id") : List.of());
        result.put("assetVisuals", permissions.contains("ELEMENT:VIEW") ? assetVisuals(tenantId, projectId, ids, shots) : List.of());
        return result;
    }

    private List<Map<String, Object>> assetVisuals(Long tenantId, Long projectId, List<Long> ids,
        List<Map<String, Object>> shots) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        List<Long> episodeIds = shots.stream().map(row -> value(row, "episode_id"))
            .filter(java.util.Objects::nonNull).map(value -> ((Number) value).longValue()).distinct().toList();
        Map<String, List<Map<String, Object>>> bindings = new LinkedHashMap<>();
        for (String type : List.of("CHARACTER", "SCENE", "PROP")) {
            String column = switch (type) { case "CHARACTER" -> "characters"; case "SCENE" -> "scene"; default -> "props"; };
            List<String> names = shots.stream().map(row -> value(row, column)).filter(java.util.Objects::nonNull)
                .flatMap(value -> java.util.Arrays.stream(value.toString().split("[,，、;；\\n\\r|]+")))
                .map(String::trim).filter(name -> !name.isEmpty()).distinct().toList();
            String legacy = names.isEmpty() ? "false" : "a.name in (" + placeholders(names.size()) + ")";
            List<Object> relevantParams = new ArrayList<>(List.of(tenantId, projectId));
            relevantParams.addAll(names);
            relevantParams.addAll(scopedParameters(tenantId, projectId, ids));
            List<Long> relevantAssetIds = jdbc.query(
                "select a.id from " + type.toLowerCase(java.util.Locale.ROOT) + "_asset a "
                    + "where a.tenant_id = ? and a.project_id = ? and a.deleted_at is null and (" + legacy
                    + " or exists (select 1 from storyboard_asset_reference r where r.tenant_id = ? "
                    + "and r.project_id = ? and r.storyboard_id in (" + placeholders(ids.size())
                    + ") and r.retired_at is null and r.asset_type = '" + type + "' and r.asset_id = a.id)) "
                    + "order by a.id",
                (row, index) -> row.getLong("id"), relevantParams.toArray());
            if (relevantAssetIds.isEmpty()) continue;
            List<Object> params = new ArrayList<>(List.of(tenantId, projectId, type));
            params.addAll(relevantAssetIds);
            params.addAll(names);
            params.addAll(scopedParameters(tenantId, projectId, ids));
            String preferred = "";
            if (!episodeIds.isEmpty()) {
                preferred = " or exists (select 1 from asset_visual_variant_episode preferred where preferred.tenant_id = ? "
                    + "and preferred.project_id = ? and preferred.asset_type = ? and preferred.episode_id in ("
                    + placeholders(episodeIds.size()) + ") and preferred.variant_id = v.id and preferred.is_preferred = true "
                    + "and preferred.binding_status = 'ACTIVE' and preferred.retired_at is null)";
                params.add(tenantId);
                params.add(projectId);
                params.add(type);
                params.addAll(episodeIds);
            }
            List<Map<String, Object>> rows = jdbc.queryForList(
                "select v.id, v.asset_id, v.name, v.source_type, v.generation_status, v.current_image_result_id, "
                    + "v.current_image_url, v.is_primary, image.id as display_result_id from asset_visual_variant v join " + type.toLowerCase(java.util.Locale.ROOT)
                    + "_asset a on a.id = v.asset_id and a.tenant_id = v.tenant_id and a.project_id = v.project_id and a.deleted_at is null "
                    + "left join ai_image_result image on image.id = v.current_image_result_id and image.tenant_id = v.tenant_id "
                    + "and image.project_id = v.project_id and image.status = 'ACTIVE' and image.display_path is not null "
                    + "where v.tenant_id = ? and v.project_id = ? and v.asset_type = ? and v.asset_id in ("
                    + placeholders(relevantAssetIds.size()) + ") and v.deleted_at is null and ((v.is_primary = true and "
                    + legacy + ") or exists (select 1 from storyboard_asset_reference r where r.tenant_id = ? and r.project_id = ? "
                    + "and r.storyboard_id in (" + placeholders(ids.size()) + ") and r.retired_at is null and r.asset_type = v.asset_type "
                    + "and r.asset_id = v.asset_id and (r.variant_id = v.id or (r.variant_id is null and v.is_primary = true)))"
                    + preferred + ") "
                    + "order by v.asset_id, v.is_primary desc, v.id", params.toArray()
            );
            for (var row : rows) {
                Map<String, Object> variant = camelCase(row);
                variant.put("assetType", type);
                variant.put("primary", variant.remove("isPrimary"));
                Object imageId = variant.remove("displayResultId");
                variant.put("currentImageThumbnailUrl", imageId == null ? null
                    : "/api/projects/" + projectId + "/ai-image-results/" + imageId + "/thumbnail");
                variant.put("usable", imageId != null);
                grouped.computeIfAbsent(type + ":" + variant.get("assetId"), key -> new ArrayList<>()).add(variant);
            }
            List<Long> variantIds = rows.stream().map(row -> value(row, "id"))
                .map(value -> ((Number) value).longValue()).distinct().toList();
            List<Long> assetIds = rows.stream().map(row -> value(row, "asset_id"))
                .map(value -> ((Number) value).longValue()).distinct().toList();
            if (!variantIds.isEmpty() && !episodeIds.isEmpty()) {
                List<Object> bindingParams = new ArrayList<>(List.of(tenantId, projectId, type));
                bindingParams.addAll(assetIds);
                bindingParams.addAll(variantIds);
                bindingParams.addAll(episodeIds);
                List<Map<String, Object>> values = jdbc.queryForList(
                    "select b.id, b.asset_id, b.variant_id, b.episode_id, e.episode_no, e.title episode_title, "
                        + "b.is_preferred preferred, b.binding_status status from asset_visual_variant_episode b "
                        + "join script_episode e on e.id = b.episode_id and e.tenant_id = b.tenant_id and e.project_id = b.project_id "
                        + "where b.tenant_id = ? and b.project_id = ? and b.asset_type = ? and b.asset_id in ("
                        + placeholders(assetIds.size()) + ") and b.variant_id in (" + placeholders(variantIds.size())
                        + ") and b.episode_id in (" + placeholders(episodeIds.size()) + ") and b.binding_status = 'ACTIVE' "
                        + "and b.retired_at is null and e.status = 'ACTIVE' and e.retired_at is null "
                        + "order by e.episode_no, b.is_preferred desc, b.id", bindingParams.toArray());
                for (Map<String, Object> binding : values) {
                    String key = type + ":" + value(binding, "asset_id");
                    bindings.computeIfAbsent(key, ignored -> new ArrayList<>()).add(camelCase(binding));
                }
            }
        }
        return grouped.entrySet().stream().map(entry -> {
            Map<String, Object> visual = new LinkedHashMap<>();
            visual.put("variants", entry.getValue());
            visual.put("variantCount", entry.getValue().size());
            visual.put("episodeBindings", bindings.getOrDefault(entry.getKey(), List.of()));
            visual.put("generationSummary", Map.of());
            visual.put("summaryOnly", true);
            return Map.<String, Object>of("key", entry.getKey(), "visual", visual);
        }).toList();
    }

    private List<Map<String, Object>> tasks(Long tenantId, Long projectId, List<Long> ids,
        List<Map<String, Object>> shots, String kind, String boundColumn) {
        List<Long> boundIds = shots.stream().map(row -> value(row, boundColumn))
            .filter(java.util.Objects::nonNull).map(value -> ((Number) value).longValue()).distinct().toList();
        String target = "image".equals(kind) ? "target_id" : "storyboard_id";
        String targetFilter = "image".equals(kind) ? " and target_type = 'STORYBOARD'" : "";
        String columns = "id, project_id, " + target + ", model, provider_code, status, error_message, created_at";
        if ("image".equals(kind)) columns += ", task_type, target_type, aspect_ratio, image_count, execution_id";
        if ("video".equals(kind)) columns += ", duration_seconds, aspect_ratio, execution_id";
        String boundClause = boundIds.isEmpty() ? ""
            : " or t.id in (select task_id from ai_" + kind + "_result where tenant_id = ? and project_id = ?"
                + " and status = 'ACTIVE' and id in (" + placeholders(boundIds.size()) + "))";
        List<Object> params = scopedParameters(tenantId, projectId, ids);
        if (!boundIds.isEmpty()) params.addAll(scopedParameters(tenantId, projectId, boundIds));
        // Rank only requested targets; bound tasks are independent of the history page.
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select " + columns + " from (select " + columns
                + ", row_number() over (partition by " + target + " order by created_at desc, id desc) as media_rank "
                + "from ai_" + kind + "_task where tenant_id = ? and project_id = ? and deleted_at is null"
                + targetFilter + " and " + target + " in (" + placeholders(ids.size()) + ")) t "
                + "where media_rank = 1" + boundClause + " order by created_at desc, id desc", params.toArray()
        );
        if (rows.isEmpty()) return List.of();
        List<Long> taskIds = rows.stream().map(row -> ((Number) value(row, "id")).longValue()).toList();
        Map<Long, List<Map<String, Object>>> results = results(tenantId, projectId, kind, taskIds, boundIds);
        Map<Long, Map<String, Object>> executions = executions(tenantId, projectId, rows);
        return rows.stream().map(row -> {
            Map<String, Object> mapped = camelCase(row);
            Long taskId = ((Number) mapped.get("id")).longValue();
            List<Map<String, Object>> taskResults = results.getOrDefault(taskId, List.of());
            mapped.put("results", taskResults);
            mapped.put("resultCount", taskResults.stream().findFirst()
                .map(result -> ((Number) result.getOrDefault("resultCount", 0L)).longValue()).orElse(0L));
            taskResults.forEach(result -> result.remove("resultCount"));
            if (mapped.get("executionId") instanceof Number executionId) {
                mapped.put("execution", executions.get(executionId.longValue()));
            }
            return mapped;
        }).toList();
    }

    private Map<Long, Map<String, Object>> executions(Long tenantId, Long projectId,
        List<Map<String, Object>> taskRows) {
        List<Long> ids = taskRows.stream().map(row -> value(row, "execution_id"))
            .filter(java.util.Objects::nonNull).map(value -> ((Number) value).longValue()).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        List<Object> params = scopedParameters(tenantId, projectId, ids);
        return jdbc.queryForList("select id, status, phase, progress, retryable, error_code, error_message "
                + "from ai_execution_task where tenant_id = ? and project_id = ? and id in ("
                + placeholders(ids.size()) + ")", params.toArray()).stream()
            .map(StoryboardMediaQueryService::camelCase)
            .collect(java.util.stream.Collectors.toMap(
                row -> ((Number) row.get("id")).longValue(), row -> row));
    }

    private Map<Long, List<Map<String, Object>>> results(Long tenantId, Long projectId, String kind,
        List<Long> taskIds, List<Long> boundIds) {
        String columns = "id, task_id, is_selected, status, created_at";
        columns += switch (kind) {
            case "image" -> ", target_type, target_id, thumbnail_url, width, height, file_size, material_id";
            case "video" -> ", storyboard_id, video_url, storage_path, cover_url, duration_seconds, width, height, file_size, format, material_id";
            default -> ", storyboard_id, audio_url, storage_path, duration_seconds, file_size, format, material_id";
        };
        List<Object> params = new ArrayList<>();
        String boundPriority = "";
        if (!boundIds.isEmpty()) {
            boundPriority = " when id in (" + placeholders(boundIds.size()) + ") then 0";
            params.addAll(boundIds);
        }
        params.addAll(scopedParameters(tenantId, projectId, taskIds));
        List<Map<String, Object>> rows = jdbc.queryForList(
            "select " + columns + ", result_count from (select " + columns
                + ", count(*) over (partition by task_id) result_count, row_number() over (partition by task_id order by case" + boundPriority
                + " when is_selected = true then 1 else 2 end, created_at desc, id desc) as media_rank "
                + "from ai_" + kind + "_result where tenant_id = ? and project_id = ? and status = 'ACTIVE'"
                + " and task_id in (" + placeholders(taskIds.size()) + ")) r where media_rank <= 2"
                + " order by task_id, media_rank", params.toArray()
        );
        Map<Long, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> mapped = camelCase(row);
            if ("image".equals(kind)) {
                mapped.put("selected", mapped.remove("isSelected"));
                mapped.put("imageUrl", mapped.get("thumbnailUrl"));
            } else if ("video".equals(kind)) {
                mapped.put("videoUrl", "/api/projects/" + projectId + "/ai-video-results/" + mapped.get("id") + "/playback");
                mapped.put("referenceUrl", mapped.get("storagePath"));
                mapped.put("coverUrl", com.antshorttv.material.MediaCoverDeliveryService.coverUrl(projectId,
                    ((Number) mapped.get("id")).longValue(),
                    com.antshorttv.material.MediaPlaybackService.ResourceKind.AI_VIDEO_RESULT,
                    (String) mapped.get("coverUrl")));
            } else {
                mapped.put("selected", mapped.remove("isSelected"));
            }
            grouped.computeIfAbsent(((Number) mapped.get("taskId")).longValue(), key -> new ArrayList<>()).add(mapped);
        }
        return grouped;
    }

    private static List<Object> scopedParameters(Long tenantId, Long projectId, List<Long> ids) {
        List<Object> values = new ArrayList<>();
        values.add(tenantId);
        values.add(projectId);
        values.addAll(ids);
        return values;
    }

    private static String placeholders(int size) {
        return String.join(",", java.util.Collections.nCopies(size, "?"));
    }

    private static Object value(Map<String, Object> row, String key) {
        return row.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .findFirst().map(Map.Entry::getValue).orElse(null);
    }

    private static Map<String, Object> camelCase(Map<String, Object> row) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        row.forEach((key, value) -> {
            String[] words = key.toLowerCase(java.util.Locale.ROOT).split("_");
            StringBuilder property = new StringBuilder(words[0]);
            for (int index = 1; index < words.length; index++) {
                property.append(Character.toUpperCase(words[index].charAt(0))).append(words[index].substring(1));
            }
            mapped.put(property.toString(), value);
        });
        return mapped;
    }
}
