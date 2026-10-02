package com.antshorttv.material;

import com.antshorttv.aiimage.AiImageResultMapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.MediaObjectIdentity;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import com.antshorttv.storage.ObjectStorageProperties;
import com.antshorttv.storage.RegisteredMediaDetails;
import java.net.URI;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaCoverDeliveryService {
    private static final Pattern IMAGE = Pattern.compile(
        "^/api/projects/([1-9][0-9]*)/ai-image-results/([1-9][0-9]*)/(?:download|display|thumbnail|image)$");
    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final MediaDeliveryGrantService grants;
    private final ImageDisplayRenditionService renditions;
    private final AiImageResultMapper images;
    private final ObjectStorageKeyFactory keys;
    private final ObjectStorageProperties properties;

    public MediaCoverDeliveryService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MediaDeliveryGrantService grants, ImageDisplayRenditionService renditions, AiImageResultMapper images,
        ObjectStorageKeyFactory keys, ObjectStorageProperties properties) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.grants = grants;
        this.renditions = renditions;
        this.images = images;
        this.keys = keys;
        this.properties = properties;
    }

    public DeliveryGrant delivery(Long projectId, Long resourceId, MediaPlaybackService.ResourceKind kind) {
        var rows = jdbc.query("select tenant_id, cover_url from " + kind.table()
            + " where id = ? and project_id = ? and status = 'ACTIVE' limit 1",
            (row, index) -> new CoverSource(row.getLong("tenant_id"), row.getString("cover_url")), resourceId, projectId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "视频封面不存在。");
        var source = rows.get(0);
        var context = permissions.require(source.tenantId(), projectId, kind.permission());
        if (source.url() == null || source.url().isBlank()) return null;
        var display = resolve(source.tenantId(), projectId, source.url(), kind.name() + "-cover-" + resourceId);
        if (!ready(display, source.tenantId(), projectId)) return null;
        return grants.issue(new DeliveryGrantRequest(source.tenantId(), projectId, context.userId(),
            kind.name() + "_COVER", resourceId, display.identity().versionId(), "DISPLAY_IMAGE_SLIM", display.objectKey(), false));
    }

    public DeliveryGrant storyboardFirstFrame(Long projectId, Long storyboardId) {
        var rows = jdbc.query("""
            select tenant_id, first_frame_url from storyboard
             where id = ? and project_id = ? and status in ('DRAFT','CONFIRMED') and deleted_at is null limit 1
            """, (row, index) -> new CoverSource(row.getLong("tenant_id"), row.getString("first_frame_url")),
            storyboardId, projectId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "分镜首帧不存在。");
        var source = rows.get(0);
        var context = permissions.require(source.tenantId(), projectId, "STORYBOARD:VIEW");
        if (source.url() == null || source.url().isBlank()) return null;
        var display = resolve(source.tenantId(), projectId, source.url(), "storyboard-first-frame-" + storyboardId);
        if (!ready(display, source.tenantId(), projectId)) return null;
        return grants.issue(new DeliveryGrantRequest(source.tenantId(), projectId, context.userId(),
            "STORYBOARD_FIRST_FRAME", storyboardId, display.identity().versionId(),
            "DISPLAY_IMAGE_SLIM", display.objectKey(), false));
    }

    public static String storyboardFirstFrameUrl(Long projectId, Long storyboardId, String source) {
        return source == null || source.isBlank() ? null
            : "/api/projects/" + projectId + "/storyboards/" + storyboardId + "/first-frame";
    }

    public static String coverUrl(Long projectId, Long resourceId, MediaPlaybackService.ResourceKind kind, String source) {
        return source == null || source.isBlank() ? null : "/api/projects/" + projectId + "/" + kind.path() + "/" + resourceId + "/cover";
    }

    private RegisteredMediaDetails resolve(Long tenantId, Long projectId, String source, String correlation) {
        MediaObjectIdentity identity;
        var matcher = IMAGE.matcher(source);
        if (matcher.matches()) {
            Long sourceProject;
            Long imageId;
            try {
                sourceProject = Long.valueOf(matcher.group(1));
                imageId = Long.valueOf(matcher.group(2));
            } catch (NumberFormatException exception) {
                return null;
            }
            if (!projectId.equals(sourceProject)) return null;
            var image = images.selectById(imageId);
            if (image == null || !Objects.equals(tenantId, image.getTenantId())
                || !Objects.equals(projectId, image.getProjectId()) || !"ACTIVE".equals(image.getStatus())) return null;
            identity = new MediaObjectIdentity(tenantId, projectId, "AI_IMAGE_RESULT", imageId, "result-" + imageId);
        } else {
            String key = ownedKey(tenantId, projectId, source);
            if (key == null) return null;
            var registered = jdbc.query("""
                select id, tenant_id, project_id, asset_type, asset_id, version_id,
                       rendition_type, object_key, mime_type, file_size, status
                  from media_object where tenant_id = ? and project_id = ? and object_key = ? limit 1
                """, (row, index) -> new RegisteredMediaDetails(row.getLong("id"),
                    new MediaObjectIdentity(row.getLong("tenant_id"), row.getLong("project_id"), row.getString("asset_type"),
                        row.getLong("asset_id"), row.getString("version_id")),
                    row.getString("rendition_type"), row.getString("object_key"), row.getString("mime_type"),
                    row.getLong("file_size"), row.getString("status"), null), tenantId, projectId, key);
            if (registered.isEmpty()) return null;
            var object = registered.get(0);
            if (!"READY".equals(object.status()) || object.fileSize() <= 0
                || object.mimeType() == null || !object.mimeType().startsWith("image/")) return null;
            if ("DISPLAY_IMAGE_SLIM".equals(object.renditionType())) return object;
            if (!"ORIGINAL".equals(object.renditionType())) return null;
            identity = object.identity();
        }
        var display = renditions.displayDetails(identity);
        if (ready(display, tenantId, projectId)) return display;
        if (display != null && !"FAILED".equals(display.status())) return null;
        var original = renditions.originalDetails(identity);
        if (original != null && "READY".equals(original.status()) && original.fileSize() > 0
            && original.mimeType() != null && original.mimeType().startsWith("image/")) {
            try {
                renditions.retryFailedDisplay(identity, correlation);
            } catch (RuntimeException exception) {
                // The processing coordinator records failure; cover reads keep their placeholder.
                return null;
            }
        }
        return null;
    }

    private String ownedKey(Long tenantId, Long projectId, String source) {
        try {
            String key = source;
            if (source.startsWith("https://") || source.startsWith("http://")) {
                URI uri = URI.create(source), cdn = URI.create(properties.getCdnDomain());
                if (!Objects.equals(uri.getScheme(), cdn.getScheme()) || !Objects.equals(uri.getAuthority(), cdn.getAuthority())
                    || uri.getFragment() != null) return null;
                key = uri.getPath();
            }
            if (key.startsWith("/")) key = key.substring(1);
            key = keys.objectKey(key);
            return key.startsWith("materials/" + tenantId + "/" + projectId + "/") ? key : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean ready(RegisteredMediaDetails display, Long tenantId, Long projectId) {
        return display != null && "READY".equals(display.status()) && display.fileSize() > 0
            && display.mimeType() != null && Set.of("image/jpeg", "image/png", "image/gif").contains(display.mimeType())
            && "DISPLAY_IMAGE_SLIM".equals(display.renditionType())
            && Objects.equals(tenantId, display.identity().tenantId()) && Objects.equals(projectId, display.identity().projectId())
            && display.objectKey() != null && display.objectKey().startsWith("materials/" + tenantId + "/" + projectId + "/");
    }

    private record CoverSource(Long tenantId, String url) {}
}
