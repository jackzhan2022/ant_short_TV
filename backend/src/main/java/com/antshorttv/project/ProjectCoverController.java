package com.antshorttv.project;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.antshorttv.rbac.ProjectPermissionGuard;
import com.antshorttv.storage.MediaUploadSessionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{id}/cover")
public class ProjectCoverController {
    private final ProjectCoverImageService covers;
    private final ProjectMapper projects;
    private final ProjectPermissionGuard permissions;
    private final MediaUploadSessionService uploads;

    public ProjectCoverController(ProjectCoverImageService covers, ProjectMapper projects,
        ProjectPermissionGuard permissions, MediaUploadSessionService uploads) {
        this.covers = covers;
        this.projects = projects;
        this.permissions = permissions;
        this.uploads = uploads;
    }

    @GetMapping
    public ResponseEntity<Void> display(@PathVariable Long id) {
        var grant = covers.delivery(id);
        return grant == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
            : ResponseEntity.status(302).location(URI.create(grant.url())).cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ProjectCoverImageService.CoverStatus>> status(@PathVariable Long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(covers.status(id)));
    }

    @PostMapping("/retry")
    public ApiResponse<ProjectCoverImageService.CoverStatus> retry(@PathVariable Long id) {
        return ApiResponse.success(covers.retry(id));
    }

    @PostMapping("/upload")
    public ApiResponse<ProjectCoverImageService.CoverStatus> upload(@PathVariable Long id,
        @Valid @RequestBody CoverUploadRequest body) {
        ProjectEntity project = projects.selectById(id);
        if (project == null || project.deletedAt != null)
            throw new BusinessException(ErrorCode.PROJECT_ACCESS_DENIED, "Project access denied.");
        var context = permissions.require(project.tenantId, id, "PROJECT:EDIT");
        var upload = uploads.requireCompleted(context.userId(), project.tenantId, body.sessionToken());
        return ApiResponse.success(covers.bindUpload(project, upload));
    }

    public record CoverUploadRequest(@NotBlank String sessionToken) {}
}
