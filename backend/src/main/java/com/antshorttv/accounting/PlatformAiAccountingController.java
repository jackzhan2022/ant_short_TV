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

    @GetMapping("/models/{modelId}/billing")
    @RequirePlatformPermission("PLATFORM_AI_ACCOUNTING_VIEW")
    public ApiResponse<ModelBillingHistoryResponse> billingHistory(@PathVariable Long modelId) {
        return ApiResponse.success(service.billingHistory(modelId));
    }

    @PostMapping("/models/{modelId}/point-price-versions")
    @RequirePlatformPermission("PLATFORM_AI_POINT_PRICE_PUBLISH")
    public ApiResponse<ModelPointPriceVersionResponse> publishPointPrice(
        @PathVariable Long modelId,
        @Valid @RequestBody PublishModelPointPriceRequest request
    ) {
        return ApiResponse.success(service.publishModelPointPrice(modelId, request));
    }

    @PostMapping("/models/{modelId}/cost-price-versions/{versionId}/revoke")
    @RequirePlatformPermission("PLATFORM_AI_PRICE_PUBLISH")
    public ApiResponse<ModelPriceVersionResponse> revokeCostPrice(
        @PathVariable Long modelId, @PathVariable Long versionId
    ) {
        return ApiResponse.success(service.revokeCostPrice(modelId, versionId));
    }

    @PostMapping("/models/{modelId}/point-price-versions/{versionId}/revoke")
    @RequirePlatformPermission("PLATFORM_AI_POINT_PRICE_PUBLISH")
    public ApiResponse<ModelPointPriceVersionResponse> revokePointPrice(
        @PathVariable Long modelId, @PathVariable Long versionId
    ) {
        return ApiResponse.success(service.revokePointPrice(modelId, versionId));
    }

    @GetMapping("/executions/{executionId}/accounting")
    @RequirePlatformPermission("PLATFORM_AI_ACCOUNTING_VIEW")
    public ApiResponse<PlatformAiAccountingDetailResponse> accountingDetail(
        @PathVariable Long executionId
    ) {
        return ApiResponse.success(service.accountingDetail(executionId));
    }
}
