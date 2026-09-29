package com.antshorttv.script;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/storyboard-asset-references")
public class StoryboardAssetReferenceBackfillController {
    private static final String PERMISSION = "PLATFORM_STORYBOARD_BINDING_BACKFILL";

    private final StoryboardAssetReferenceBackfillService service;

    public StoryboardAssetReferenceBackfillController(
        StoryboardAssetReferenceBackfillService service
    ) {
        this.service = service;
    }

    @PostMapping("/backfill")
    @RequirePlatformPermission(PERMISSION)
    public ApiResponse<StoryboardAssetReferenceBackfillResult> backfill(
        @RequestParam(required = false) Integer limit
    ) {
        return ApiResponse.success(service.backfill(limit));
    }

    @GetMapping("/consistency-audit")
    @RequirePlatformPermission(PERMISSION)
    public ApiResponse<Map<String, Integer>> audit() {
        return ApiResponse.success(Map.of("driftCount", service.countConsistencyDrift()));
    }
}
