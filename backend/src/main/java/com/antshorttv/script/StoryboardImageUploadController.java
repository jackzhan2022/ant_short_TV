package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.common.TenantRequestSupport;
import com.antshorttv.rbac.ProjectPermissionGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class StoryboardImageUploadController {
    private final StoryboardImageUploadService uploads;
    private final ProjectPermissionGuard permissions;
    private final JdbcTemplate jdbc;

    public StoryboardImageUploadController(StoryboardImageUploadService uploads,
        ProjectPermissionGuard permissions, JdbcTemplate jdbc) {
        this.uploads = uploads;
        this.permissions = permissions;
        this.jdbc = jdbc;
    }

    @PostMapping("/storyboards/{storyboardId}/reference-image-upload")
    public ApiResponse<StoryboardImageUploadService.UploadedReference> upload(
        @PathVariable Long projectId, @PathVariable Long storyboardId,
        @Valid @RequestBody UploadRequest body, HttpServletRequest request) {
        Long tenantId = TenantRequestSupport.tenantId(request);
        var context = permissions.require(tenantId, projectId, "STORYBOARD:EDIT");
        return ApiResponse.success(uploads.upload(tenantId, projectId, storyboardId,
            context.userId(), body.sessionToken(), body.name()));
    }

    @GetMapping("/image-materials/{materialId}/thumbnail")
    public ResponseEntity<Void> thumbnail(@PathVariable Long projectId, @PathVariable Long materialId) {
        var rows = jdbc.queryForList("""
            select r.id, r.tenant_id from ai_image_result r
              join material m on m.id=r.material_id and m.tenant_id=r.tenant_id and m.project_id=r.project_id
             where m.id=? and m.project_id=? and m.material_type='IMAGE'
               and m.deleted_at is null and m.status='ACTIVE' and r.status='ACTIVE'
             order by r.id limit 1
            """, materialId, projectId);
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "图片素材不存在。");
        var row = rows.get(0);
        permissions.require(((Number) row.get("tenant_id")).longValue(), projectId, "AI_IMAGE_TASK:VIEW");
        return ResponseEntity.status(302).header("Cache-Control", "private, no-store")
            .location(URI.create("/api/projects/" + projectId + "/ai-image-results/" + row.get("id") + "/thumbnail"))
            .build();
    }

    public record UploadRequest(@NotBlank @Size(max=64) String sessionToken,
        @NotBlank @Size(max=200) String name) {}
}
