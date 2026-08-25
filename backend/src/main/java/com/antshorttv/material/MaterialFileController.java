package com.antshorttv.material;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MaterialFileController {
    private final MaterialFileAccessService accessService;
    private final ProjectPermissionGuard projectPermissionGuard;

    public MaterialFileController(
        MaterialFileAccessService accessService,
        ProjectPermissionGuard projectPermissionGuard
    ) {
        this.accessService = accessService;
        this.projectPermissionGuard = projectPermissionGuard;
    }

    @GetMapping("/materials/{tenantId}/{projectId}/**")
    public ResponseEntity<Resource> read(
        @PathVariable Long tenantId,
        @PathVariable Long projectId,
        @RequestParam(required = false) String token,
        HttpServletRequest request
    ) {
        String storagePath = storagePath(request);
        if (!accessService.isValidToken(storagePath, token)) {
            requireProjectAccess(tenantId, projectId);
        }
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType(accessService.contentType(storagePath)))
            .body(accessService.resource(storagePath));
    }

    private void requireProjectAccess(Long tenantId, Long projectId) {
        projectPermissionGuard.require(tenantId, projectId, "PROJECT:VIEW");
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
