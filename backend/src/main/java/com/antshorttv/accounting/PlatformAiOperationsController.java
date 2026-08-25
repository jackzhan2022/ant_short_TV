package com.antshorttv.accounting;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.antshorttv.accounting.PlatformAiOperationsResponses.PlatformAiOperationsOverview;

@RestController
@RequestMapping("/api/platform/ai/operations")
public class PlatformAiOperationsController {
    private final PlatformAiOperationsService service;

    public PlatformAiOperationsController(PlatformAiOperationsService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    @RequirePlatformPermission("PLATFORM_AI_ACCOUNTING_VIEW")
    public ApiResponse<PlatformAiOperationsOverview> overview() {
        return ApiResponse.success(service.overview());
    }
}
