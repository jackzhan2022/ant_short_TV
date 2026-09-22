package com.antshorttv.inspiration;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/platform/inspiration-creations")
public class InspirationManagementController {
    private static final String MANAGE_PERMISSION = "PLATFORM_INSPIRATION_MANAGE";
    private final InspirationManagementService service;

    public InspirationManagementController(InspirationManagementService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<InspirationManagementPageResponse> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String publishStatus,
        @RequestParam(required = false) String mediaType
    ) {
        return ApiResponse.success(service.list(page, pageSize, keyword, publishStatus, mediaType));
    }

    @PostMapping
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<InspirationManagementItemResponse> create(
        @RequestPart("file") MultipartFile file,
        @RequestParam String title,
        @RequestParam String promptText,
        @RequestParam(required = false) List<String> tags,
        @RequestParam(defaultValue = "UNPUBLISHED") String publishStatus
    ) {
        return ApiResponse.success(service.create(
            file,
            new InspirationCreateMetadata(title, tags, promptText, publishStatus)
        ));
    }

    @PutMapping("/{id}")
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<InspirationManagementItemResponse> update(
        @PathVariable Long id,
        @RequestBody InspirationMetadataRequest request
    ) {
        return ApiResponse.success(service.update(id, request));
    }

    @PutMapping("/{id}/publish-status")
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<InspirationManagementItemResponse> updatePublishStatus(
        @PathVariable Long id,
        @RequestBody InspirationPublishStatusRequest request
    ) {
        return ApiResponse.success(service.updatePublishStatus(id, request));
    }

    @PutMapping("/reorder")
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<Void> reorder(@RequestBody InspirationReorderRequest request) {
        service.reorder(request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}")
    @RequirePlatformPermission(MANAGE_PERMISSION)
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success(null);
    }
}
