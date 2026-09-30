package com.antshorttv.storage;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.security.TenantContext;
import com.antshorttv.security.TenantContextResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/media-uploads")
public class MediaUploadController {
    private final MediaUploadSessionService service;
    private final TenantContextResolver tenants;
    private final ProjectPermissionGuard projects;

    public MediaUploadController(
        MediaUploadSessionService service,
        TenantContextResolver tenants,
        ProjectPermissionGuard projects
    ) {
        this.service = service;
        this.tenants = tenants;
        this.projects = projects;
    }

    @PostMapping
    public ApiResponse<MediaUploadSession> create(
        @Valid @RequestBody CreateMediaUploadRequest body,
        HttpServletRequest request
    ) {
        Long tenantId = TenantRequestSupport.tenantId(request);
        TenantContext context = body.projectId() == null
            ? tenants.requireActiveMember(tenantId)
            : projects.require(tenantId, body.projectId(), "PROJECT:EDIT");
        return ApiResponse.success(service.create(new CreateMediaUploadSession(
            tenantId,
            body.projectId(),
            context.userId(),
            body.fileName(),
            body.contentType(),
            body.fileSize()
        )));
    }

    @PostMapping("/{sessionToken}/credentials")
    public ApiResponse<MediaUploadSession> renew(
        @PathVariable String sessionToken,
        HttpServletRequest request
    ) {
        TenantContext context = member(request);
        return ApiResponse.success(service.renew(context.userId(), sessionToken));
    }

    @PostMapping("/{sessionToken}/complete")
    public ApiResponse<VerifiedMediaUpload> complete(
        @PathVariable String sessionToken,
        HttpServletRequest request
    ) {
        TenantContext context = member(request);
        return ApiResponse.success(service.complete(context.userId(), sessionToken));
    }

    @GetMapping("/{sessionToken}")
    public ApiResponse<MediaUploadSession> status(
        @PathVariable String sessionToken,
        HttpServletRequest request
    ) {
        TenantContext context = member(request);
        return ApiResponse.success(service.status(context.userId(), sessionToken));
    }

    @DeleteMapping("/{sessionToken}")
    public ApiResponse<Void> cancel(
        @PathVariable String sessionToken,
        HttpServletRequest request
    ) {
        TenantContext context = member(request);
        service.cancel(context.userId(), sessionToken);
        return ApiResponse.ok();
    }

    private TenantContext member(HttpServletRequest request) {
        return tenants.requireActiveMember(TenantRequestSupport.tenantId(request));
    }
}
