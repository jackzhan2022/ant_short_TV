package com.antshorttv.ai;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.rbac.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AiCallLogController {

    private final AiCallLogService aiCallLogService;

    public AiCallLogController(AiCallLogService aiCallLogService) {
        this.aiCallLogService = aiCallLogService;
    }

    @GetMapping("/tenants/{tenantId}/ai-call-logs")
    @RequirePermission("AI_SERVICE:VIEW")
    public ApiResponse<AiCallLogPageResponse> list(
        @PathVariable Long tenantId,
        @RequestParam(defaultValue = "1") Integer current,
        @RequestParam(defaultValue = "20") Integer pageSize,
        @RequestParam(required = false) String serviceType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String businessScene
    ) {
        return ApiResponse.success(aiCallLogService.list(tenantId, current, pageSize, serviceType, status, businessScene));
    }
}
