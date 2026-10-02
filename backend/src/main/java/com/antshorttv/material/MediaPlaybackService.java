package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.ObjectStorageKeyFactory;
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

    public MediaPlaybackService(JdbcTemplate jdbc, ProjectPermissionGuard permissions,
        MediaDeliveryGrantService grants, ObjectStorageKeyFactory keys) {
        this.jdbc = jdbc;
        this.permissions = permissions;
        this.grants = grants;
        this.keys = keys;
    }

    public DeliveryGrant issue(Long projectId, Long resourceId, ResourceKind kind) {
        return issue(projectId, resourceId, kind, kind.permission);
    }

    public DeliveryGrant download(Long projectId, Long resultId) {
        return issue(projectId, resultId, ResourceKind.AI_VIDEO_RESULT, "AI_VIDEO_RESULT:DOWNLOAD");
    }

    private DeliveryGrant issue(Long projectId, Long resourceId, ResourceKind kind, String permission) {
        var resources = jdbc.query("select tenant_id, project_id, storage_path from " + kind.table
            + " where id = ? and project_id = ? and status = 'ACTIVE' limit 1",
            (row, index) -> new PlaybackResource(row.getLong("tenant_id"), row.getLong("project_id"), row.getString("storage_path")),
            resourceId, projectId);
        if (resources.isEmpty()) throw unavailable();
        var resource = resources.get(0);
        String objectKey;
        try {
            String source = resource.objectKey();
            objectKey = keys.objectKey(source != null && source.startsWith("/") ? source.substring(1) : source);
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
        if (!objectKey.startsWith("materials/" + resource.tenantId() + "/" + projectId + "/")) throw unavailable();
        var context = permissions.require(resource.tenantId(), projectId, permission);
        return grants.issue(new DeliveryGrantRequest(resource.tenantId(), projectId, context.userId(),
            kind.name(), resourceId, "resource-" + resourceId, "VIDEO", objectKey, true));
    }

    private BusinessException unavailable() {
        return new BusinessException(ErrorCode.NOT_FOUND, "视频资源不存在。");
    }

    private record PlaybackResource(Long tenantId, Long projectId, String objectKey) {}
}
