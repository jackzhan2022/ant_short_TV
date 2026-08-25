package com.antshorttv.accounting;

import com.antshorttv.common.ApiResponse;
import com.antshorttv.platform.RequirePlatformPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/ai")
public class PlatformAiAccountingController {
    private final PlatformAiAccountingService service;

    public PlatformAiAccountingController(PlatformAiAccountingService service) {
        this.service = service;
    }

    @PostMapping("/models/{modelId}/price-versions")
    @RequirePlatformPermission("PLATFORM_AI_PRICE_PUBLISH")
    public ApiResponse<ModelPriceVersionResponse> publishModelPrice(
        @PathVariable Long modelId,
        @Valid @RequestBody PublishModelPriceRequest request
    ) {
        return ApiResponse.success(service.publishModelPrice(modelId, request));
    }

    @PostMapping("/point-policy-versions")
    @RequirePlatformPermission("PLATFORM_AI_POINT_POLICY_PUBLISH")
    public ApiResponse<PointPolicyVersionResponse> publishPointPolicy(
        @Valid @RequestBody PublishPointPolicyRequest request
    ) {
        return ApiResponse.success(service.publishPointPolicy(request));
    }

    @GetMapping("/executions/{executionId}/accounting")
    @RequirePlatformPermission("PLATFORM_AI_ACCOUNTING_VIEW")
    public ApiResponse<PlatformAiAccountingDetailResponse> accountingDetail(
        @PathVariable Long executionId
    ) {
        return ApiResponse.success(service.accountingDetail(executionId));
    }
}
