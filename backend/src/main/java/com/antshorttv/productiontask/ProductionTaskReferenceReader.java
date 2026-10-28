package com.antshorttv.productiontask;

import com.antshorttv.material.CompressedImageReferences;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reads the references frozen at submission, never the current mutable asset binding. */
final class ProductionTaskReferenceReader {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    ProductionTaskReferenceReader(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    List<Map<String, Object>> videoReferences(long tenant, long project, long task,
        Object firstFrame, Object lastFrame, Object legacy) {
        var rows = jdbc.queryForList("""
            select ref.id, ref.media_type, ref.source_type, ref.source_id, ref.display_name,
                   ref.compiled_label, ref.provider_role, ref.object_storage_path, ref.provider_url,
                   (select image.id from ai_image_result image
                     where image.tenant_id=ref.tenant_id and image.project_id=ref.project_id
                       and image.storage_path=ref.object_storage_path and image.status='ACTIVE'
                     order by image.id desc limit 1) image_result_id
              from ai_video_task_reference ref
             where ref.tenant_id=? and ref.project_id=? and ref.task_id=?
             order by ref.sort_order, ref.id
            """, tenant, project, task);
        if (!rows.isEmpty()) return rows.stream().map(row -> savedReference(tenant, project, row)).toList();
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        addLegacy(unique, firstFrame, "首帧");
        addLegacy(unique, lastFrame, "尾帧");
        for (var item : imageReferences(legacy)) unique.putIfAbsent(String.valueOf(item.get("thumbnailUrl")), item);
        return new ArrayList<>(unique.values());
    }

    List<Map<String, Object>> imageReferences(Object stored) {
        if (stored == null || stored.toString().isBlank()) return List.of();
        String text = stored.toString().trim();
        List<Map<String, Object>> items = new ArrayList<>();
        if (text.startsWith("[")) {
            try {
                for (JsonNode node : mapper.readTree(text)) {
                    addImage(items, node.isTextual() ? node.asText() : node.path("url").asText(""),
                        node.path("displayName").asText(node.path("name").asText("")));
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
                return List.of();
            }
        } else {
            for (String line : text.split("\\R")) addImage(items, line.trim(), "");
        }
        return items;
    }

    private Map<String, Object> savedReference(long tenant, long project, Map<String, Object> row) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", row.get("id"));
        item.put("mediaType", row.get("media_type"));
        item.put("displayName", row.get("display_name"));
        item.put("label", row.get("compiled_label"));
        item.put("role", row.get("provider_role"));
        String path = localPath(tenant, row.get("object_storage_path"));
        if ("IMAGE".equals(row.get("media_type"))) {
            if (row.get("image_result_id") instanceof Number id) {
                String base = "/api/projects/" + project + "/ai-image-results/" + id.longValue();
                item.put("thumbnailUrl", base + "/thumbnail");
                item.put("url", base + "/display");
                item.put("downloadUrl", base + "/download");
            } else {
                String thumbnail = CompressedImageReferences.thumbnail(string(row.get("provider_url")));
                if (thumbnail != null && thumbnail.startsWith("/api/projects/" + project + "/")) {
                    item.put("thumbnailUrl", thumbnail);
                    item.put("url", thumbnail);
                } else if (path != null) {
                    // Keep a manual original link without loading a large original as a thumbnail.
                    item.put("url", path);
                    item.put("downloadUrl", path);
                }
            }
        } else if (path != null) {
            item.put("url", path);
            item.put("downloadUrl", path);
        } else if ("AUDIO".equals(row.get("media_type")) && "SYSTEM_AUDIO_ASSET".equals(row.get("source_type"))
            && row.get("source_id") instanceof Number source) {
            item.put("url", "/api/system-audio/" + source.longValue() + "/playback");
        }
        return item;
    }

    private static String localPath(long tenant, Object stored) {
        String path = string(stored);
        if (path.startsWith("/")) path = path.substring(1);
        if (!path.matches("materials/" + tenant + "/[1-9][0-9]*/.+")
            || path.contains("..") || path.contains("\\") || path.contains(":") || path.contains("?") || path.contains("#")) return null;
        return "/" + path;
    }

    private static void addImage(List<Map<String, Object>> items, String url, String name) {
        String thumbnail = CompressedImageReferences.thumbnail(url);
        if (thumbnail == null) return;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("mediaType", "IMAGE"); item.put("url", thumbnail); item.put("thumbnailUrl", thumbnail);
        if (!name.isBlank()) item.put("displayName", name);
        items.add(item);
    }

    private static void addLegacy(Map<String, Map<String, Object>> items, Object value, String role) {
        List<Map<String, Object>> image = new ArrayList<>();
        addImage(image, string(value), role);
        if (image.isEmpty()) return;
        image.get(0).put("role", role);
        items.putIfAbsent(String.valueOf(image.get(0).get("thumbnailUrl")), image.get(0));
    }

    private static String string(Object value) { return value == null ? "" : value.toString(); }
}
