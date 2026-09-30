package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import com.antshorttv.security.TenantContext;
import com.antshorttv.storage.DeliveryGrantRequest;
import com.antshorttv.storage.MediaDeliveryGrantService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaterialFileController {
    private final MaterialFileAccessService accessService;
    private final ProjectPermissionGuard projectPermissionGuard;
    private final MediaDeliveryGrantService deliveryGrants;

    public MaterialFileController(
        MaterialFileAccessService accessService,
        ProjectPermissionGuard projectPermissionGuard,
        MediaDeliveryGrantService deliveryGrants
    ) {
        this.accessService = accessService;
        this.projectPermissionGuard = projectPermissionGuard;
        this.deliveryGrants = deliveryGrants;
    }

    @GetMapping("/materials/{tenantId}/{projectId}/**")
    public ResponseEntity<Void> read(
        @PathVariable Long tenantId,
        @PathVariable Long projectId,
        HttpServletRequest request
    ) {
        String objectKey = accessService.normalize(storagePath(request));
        TenantContext context = projectPermissionGuard.require(tenantId, projectId, "PROJECT:VIEW");
        long resourceId = Integer.toUnsignedLong(objectKey.hashCode()) + 1;
        var grant = deliveryGrants.issue(new DeliveryGrantRequest(
            tenantId, projectId, context.userId(), "MATERIAL_PATH", resourceId,
            "object", "ORIGINAL", objectKey, objectKey.endsWith(".mp4")
        ));
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(java.net.URI.create(grant.url()))
            .build();
    }

    private String storagePath(HttpServletRequest request) {
        String uri = URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8);
        int start = uri.indexOf("/materials/");
        if (start < 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材文件不存在。");
        }
        return uri.substring(start);
    }
}
