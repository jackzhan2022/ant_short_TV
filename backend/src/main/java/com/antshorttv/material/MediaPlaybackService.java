package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaPlaybackService {
    public enum ResourceKind {
        AI_VIDEO_RESULT("ai_video_result", "AI_VIDEO_TASK:VIEW"),
        SHOT_COMPOSE_RESULT("shot_compose_result", "SHOT_COMPOSE:VIEW"),
        EPISODE_VIDEO_VERSION("episode_video_version", "EPISODE_VERSION:VIEW");

        private final String table;
        private final String permission;
        ResourceKind(String table, String permission) {
            this.table = table;
            this.permission = permission;
        }
        String table() { return table; }
        String permission() { return permission; }
        String path() { return table.replace('_', '-') + "s"; }
    }

    private final JdbcTemplate jdbc;
    private final ProjectPermissionGuard permissions;
    private final MediaDeliveryGrantService grants;
    private final ObjectStorageKeyFactory keys;
    private final Clock clock;

    @Autowired
    public MediaPlaybackService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MediaDeliveryGrantService grants, ObjectStorageKeyFactory keys) {
        this(jdbc, permissions, grants, keys, Clock.systemUTC());
    }

    MediaPlaybackService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MediaDeliveryGrantService grants, ObjectStorageKeyFactory keys, Clock clock) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.grants = grants;
        this.keys = keys;
        this.clock = clock;
    }

    public DeliveryGrant issue(Long projectId, Long resourceId, ResourceKind kind) {
        return issue(projectId, resourceId, kind, kind.permission);
    }

    public DeliveryGrant download(Long projectId, Long resultId) {
        return issue(projectId, resultId, ResourceKind.AI_VIDEO_RESULT, "AI_VIDEO_RESULT:DOWNLOAD");
    }

    /** For an already-authorized persisted task; never exposed directly by a controller. */
    public DeliveryGrant issueForProcessing(Long tenantId, Long projectId, Long resultId, Long userId) {
        if (tenantId == null || userId == null) throw unavailable();
        return issue(projectId, resultId, ResourceKind.AI_VIDEO_RESULT, "AI_VIDEO_TASK:VIEW", tenantId, userId);
    }

    private DeliveryGrant issue(Long projectId, Long resourceId, ResourceKind kind, String permission) {
        return issue(projectId, resourceId, kind, permission, null, null);
    }

    private DeliveryGrant issue(Long projectId, Long resourceId, ResourceKind kind, String permission,
        Long processingTenant, Long processingUser) {
        boolean aiVideo = kind == ResourceKind.AI_VIDEO_RESULT;
        String sourceColumns = aiVideo ? ", source_video_url, source_video_expires_at" : "";
        var resources = jdbc.query("select tenant_id, project_id, storage_path" + sourceColumns + " from " + kind.table
            + " where id = ? and project_id = ? and status = 'ACTIVE' limit 1",
            (row, index) -> new PlaybackResource(row.getLong("tenant_id"), row.getLong("project_id"), row.getString("storage_path"),
                aiVideo ? row.getString("source_video_url") : null,
                aiVideo ? row.getObject("source_video_expires_at", LocalDateTime.class) : null),
            resourceId, projectId);
        if (resources.isEmpty()) throw unavailable();
        var resource = resources.get(0);
        if (processingTenant != null && !processingTenant.equals(resource.tenantId())) throw unavailable();
        String objectKey;
        try {
            String source = resource.objectKey();
            objectKey = keys.objectKey(source != null && source.startsWith("/") ? source.substring(1) : source);
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
        if (!objectKey.startsWith("materials/" + resource.tenantId() + "/" + projectId + "/")) throw unavailable();
        Long userId = processingUser != null ? processingUser : permissions.require(resource.tenantId(), projectId, permission).userId();
        if (aiVideo && permission.equals(kind.permission) && resource.sourceUrl() != null
            && !resource.sourceUrl().isBlank() && resource.sourceExpiresAt() != null) {
            var expiresAt = resource.sourceExpiresAt().toInstant(ZoneOffset.UTC);
            if (expiresAt.isAfter(clock.instant().plus(Duration.ofHours(2)))) {
                return new DeliveryGrant(resource.sourceUrl(), expiresAt);
            }
        }
        return grants.issue(new DeliveryGrantRequest(resource.tenantId(), projectId, userId,
            kind.name(), resourceId, "resource-" + resourceId, "VIDEO", objectKey, true));
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.NOT_FOUND, "视频资源不存在。");
    }

    private record PlaybackResource(Long tenantId, Long projectId, String objectKey,
        String sourceUrl, LocalDateTime sourceExpiresAt) {}
}
