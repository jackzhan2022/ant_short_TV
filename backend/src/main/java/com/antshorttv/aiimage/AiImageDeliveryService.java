package com.antshorttv.aiimage;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.DeliveryGrant;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import com.antshorttv.storage.ImageDisplayRenditionService;
import com.antshorttv.storage.MediaObjectIdentity;
import org.springframework.stereotype.Service;

@Service
public class AiImageDeliveryService {
    private final AiImageResultMapper resultMapper;
    private final ProjectPermissionGuard permissions;
    private final MediaDeliveryGrantService grants;
    private final ImageDisplayRenditionService renditions;

    public AiImageDeliveryService(
        AiImageResultMapper resultMapper,
        ProjectPermissionGuard permissions,
        MediaDeliveryGrantService grants,
        ImageDisplayRenditionService renditions
    ) {
        this.resultMapper = resultMapper;
        this.permissions = permissions;
        this.grants = grants;
        this.renditions = renditions;
    }

    public DeliveryGrant issue(Long projectId, Long resultId, ImageRendition rendition) {
        AiImageResultEntity result = resultMapper.selectById(resultId);
        if (result == null || !projectId.equals(result.getProjectId()) || !"ACTIVE".equals(result.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片结果不存在。");
        }
        TenantContext context = permissions.require(result.getTenantId(), projectId, "AI_IMAGE_TASK:VIEW");
        String objectKey = switch (rendition) {
            case ORIGINAL -> result.getStoragePath();
            case DISPLAY, THUMBNAIL -> result.getDisplayPath();
        };
        if (result.getTaskId() == null && rendition != ImageRendition.ORIGINAL) {
            var display = renditions.display(new MediaObjectIdentity(result.getTenantId(), projectId,
                "AI_IMAGE_RESULT", resultId, "result-" + resultId));
            // Uploaded originals are usable immediately while the display version is being processed.
            if (display == null || !"READY".equals(display.status())) objectKey = result.getStoragePath();
        }
        if (objectKey == null || objectKey.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "图片资源尚未就绪。");
        }
        return grants.issue(new DeliveryGrantRequest(
            result.getTenantId(),
            projectId,
            context.userId(),
            "AI_IMAGE_RESULT",
            resultId,
            "result-" + resultId,
            rendition.name(),
            objectKey,
            false
        ));
    }
}
