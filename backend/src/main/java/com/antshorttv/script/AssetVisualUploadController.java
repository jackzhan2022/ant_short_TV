package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.ProjectPermissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/visual-variants/{variantId}/image-upload")
public class AssetVisualUploadController {
    private final AssetVisualUploadService uploads;
    private final ProjectPermissionGuard permissions;

    public AssetVisualUploadController(AssetVisualUploadService uploads, ProjectPermissionGuard permissions) {
        this.uploads = uploads;
        this.permissions = permissions;
    }

    @PostMapping
    public ApiResponse<AssetVisualVariantService.VariantResponse> upload(
        @PathVariable Long projectId, @PathVariable Long variantId,
        @Valid @RequestBody UploadRequest body, HttpServletRequest request
    ) {
        Long tenantId = TenantRequestSupport.tenantId(request);
        var context = permissions.require(tenantId, projectId, "ELEMENT:EDIT");
        return ApiResponse.success(uploads.bind(tenantId, projectId, variantId, context.userId(), body.sessionToken()));
    }

    public record UploadRequest(@NotBlank String sessionToken) {}
}
