package com.antshorttv.video;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.script.StoryboardPromptCompiler;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class VideoTaskReferenceResolver {
    private final JdbcTemplate jdbc;
    private final ModelAccessibleVideoUrlResolver accessibleUrlResolver;

    public VideoTaskReferenceResolver(JdbcTemplate jdbc, ModelAccessibleVideoUrlResolver accessibleUrlResolver) {
        this.jdbc = jdbc;
        this.accessibleUrlResolver = accessibleUrlResolver;
    }

    public List<ResolvedReference> resolve(
        Long tenantId,
        Long projectId,
        List<StoryboardPromptCompiler.Reference> references
    ) {
        List<ResolvedReference> resolved = new ArrayList<>();
        for (StoryboardPromptCompiler.Reference reference : references) {
            resolved.add(resolveOne(tenantId, projectId, reference));
        }
        return List.copyOf(resolved);
    }

    private ResolvedReference resolveOne(
        Long tenantId,
        Long projectId,
        StoryboardPromptCompiler.Reference reference
    ) {
        Map<String, Object> row = switch (reference.sourceType()) {
            case "ASSET_VISUAL_VARIANT" -> imageVariant(tenantId, projectId, reference.sourceId());
            case "STORYBOARD_FIRST_FRAME" -> storyboardFirstFrame(tenantId, projectId, reference.sourceId());
            case "IMAGE_MATERIAL", "VIDEO_MATERIAL", "AUDIO_MATERIAL" ->
                material(tenantId, projectId, reference);
            default -> throw invalid(reference, "不支持的素材来源。");
        };
        String storagePath = string(row.get("storage_path"));
        String rawUrl = string(row.get("media_url"));
        String providerUrl = accessibleUrlResolver.resolve(notBlank(storagePath) ? storagePath : rawUrl);
        if (!notBlank(providerUrl)) {
            throw invalid(reference, "素材文件地址不可用。");
        }
        return new ResolvedReference(
            reference,
            string(row.get("display_name")),
            storagePath,
            providerUrl,
            format(row),
            longNumber(row.get("file_size")),
            intNumber(row.get("width")),
            intNumber(row.get("height")),
            decimal(row.get("duration_seconds")),
            decimal(row.get("fps"))
        );
    }

    private Map<String, Object> imageVariant(Long tenantId, Long projectId, Long variantId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select v.name display_name, r.storage_path, coalesce(r.image_url, v.current_image_url) media_url,
                   r.mime_type, r.file_size, r.width, r.height, null duration_seconds, null fps,
                   null media_format
              from asset_visual_variant v
              join ai_image_result r
                on r.id = v.current_image_result_id
               and r.tenant_id = v.tenant_id
               and r.project_id = v.project_id
               and r.status = 'ACTIVE'
             where v.id = ? and v.tenant_id = ? and v.project_id = ? and v.deleted_at is null
            """, variantId, tenantId, projectId);
        return requireOne(rows, "图片素材不存在、已删除或未生成有效图片。");
    }

    private Map<String, Object> storyboardFirstFrame(Long tenantId, Long projectId, Long storyboardId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select concat('分镜', coalesce(s.storyboard_no, s.shot_no), '首帧') display_name,
                   r.storage_path, coalesce(r.image_url, s.first_frame_url) media_url,
                   r.mime_type, r.file_size, r.width, r.height, null duration_seconds, null fps,
                   null media_format
              from storyboard s
              join ai_image_result r
                on r.id = s.first_frame_result_id
               and r.tenant_id = s.tenant_id
               and r.project_id = s.project_id
               and r.status = 'ACTIVE'
             where s.id = ? and s.tenant_id = ? and s.project_id = ? and s.deleted_at is null
            """, storyboardId, tenantId, projectId);
        return requireOne(rows, "分镜首帧不存在、已删除或未生成有效图片。");
    }

    private Map<String, Object> material(
        Long tenantId,
        Long projectId,
        StoryboardPromptCompiler.Reference reference
    ) {
        String expectedType = reference.mediaType();
        List<Map<String, Object>> rows = jdbc.queryForList("""
            select name display_name, storage_path, url media_url, mime_type, file_size,
                   width, height, duration_seconds, fps, format media_format
              from material
             where id = ? and tenant_id = ? and project_id = ? and material_type = ?
               and deleted_at is null and status = 'ACTIVE'
            """, reference.sourceId(), tenantId, projectId, expectedType);
        return requireOne(rows, reference.displayName() + "不存在、已删除或类型不匹配。");
    }

    private Map<String, Object> requireOne(List<Map<String, Object>> rows, String message) {
        if (rows.size() != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return rows.get(0);
    }

    private BusinessException invalid(StoryboardPromptCompiler.Reference reference, String reason) {
        return new BusinessException(
            ErrorCode.VALIDATION_ERROR,
            "%s（%s）%s".formatted(reference.displayName(), reference.compiledLabel(), reason)
        );
    }

    private String format(Map<String, Object> row) {
        String direct = string(row.get("media_format"));
        if (notBlank(direct)) return direct.toLowerCase();
        String mime = string(row.get("mime_type"));
        if (notBlank(mime) && mime.contains("/")) {
            String subtype = mime.substring(mime.indexOf('/') + 1).toLowerCase();
            return "mpeg".equals(subtype) ? "mp3" : subtype;
        }
        String path = notBlank(string(row.get("storage_path")))
            ? string(row.get("storage_path")) : string(row.get("media_url"));
        int dot = path == null ? -1 : path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(dot + 1).toLowerCase();
    }

    private String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer intNumber(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return null;
    }

    public record ResolvedReference(
        StoryboardPromptCompiler.Reference reference,
        String displayName,
        String objectStoragePath,
        String providerUrl,
        String format,
        Long fileSize,
        Integer width,
        Integer height,
        BigDecimal durationSeconds,
        BigDecimal fps
    ) {
    }
}
