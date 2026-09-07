package com.antshorttv.inspiration;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/inspiration")
public class InspirationThumbnailBackfillController {
    private static final String BACKFILL_PERMISSION = "PLATFORM_INSPIRATION_THUMBNAIL_BACKFILL";

    private final InspirationThumbnailBackfillService service;

    public InspirationThumbnailBackfillController(InspirationThumbnailBackfillService service) {
        this.service = service;
    }

    @PostMapping("/thumbnail-backfill")
    @RequirePlatformPermission(BACKFILL_PERMISSION)
    public ApiResponse<InspirationThumbnailBackfillResult> backfill(
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(service.backfill(limit));
    }
}
